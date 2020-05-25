package net.yudichev.googlephotosupload.core;

import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.api.gax.rpc.ResourceExhaustedException;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.rpc.Code;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.yudichev.jiotty.connector.google.photos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;

import static com.google.common.base.Preconditions.*;
import static com.google.common.collect.ImmutableList.copyOf;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static io.grpc.Status.INVALID_ARGUMENT;
import static net.yudichev.googlephotosupload.core.TestTimeModule.getCurrentInstant;

final class RecordingGooglePhotosClient implements GooglePhotosClient {
    private static final Logger logger = LoggerFactory.getLogger(RecordingGooglePhotosClient.class);

    private final Map<String, UploadedGoogleMediaBinary> binariesByUploadToken = new LinkedHashMap<>();
    private final Map<String, UploadedGoogleMediaItem> itemsById = new LinkedHashMap<>();
    private final Map<String, CreatedGooglePhotosAlbum> albumsById = new LinkedHashMap<>();
    private final Map<String, Integer> albumIdSuffixByName = new LinkedHashMap<>();
    private final Map<Object, Integer> resourceExhaustionCountByKey = new LinkedHashMap<>();
    private final Object lock = new Object();

    private boolean resourceExhaustedExceptions;
    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") // analysis failure
    private boolean fileNameBasedFailuresEnabled = true;

    RecordingGooglePhotosClient() {
        createAlbum("fail-on-me-pre-existing-album", directExecutor());
    }

    @Override
    public CompletableFuture<String> uploadMediaData(Path file, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
                if (fileNameBasedFailuresEnabled) {
                    if (file.toString().endsWith("failOnMe.jpg")) {
                        throw new RuntimeException("upload failed");
                    }
                    if (file.toString().endsWith("failOnMeWithInvalidArgumentDuringUploadIngMediaData.jpg")) {
                        throw new InvalidArgumentException(new StatusRuntimeException(
                                INVALID_ARGUMENT.withDescription("Some invalid argument error")),
                                GrpcStatusCode.of(Status.Code.INVALID_ARGUMENT),
                                false);
                    }
                }
                simulateResourceExhaustion(ImmutableSet.of("uploadMediaData", file));
                var binary = new UploadedGoogleMediaBinary(file);
                binariesByUploadToken.put(binary.getUploadToken(), binary);
                return binary.getUploadToken();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<MediaItemOrError>> createMediaItems(Optional<String> albumId, List<NewMediaItem> newMediaItems, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
                checkArgument(!newMediaItems.isEmpty(), "the list of media items is empty");
                if (newMediaItems.size() > 50) {
                    throw new InvalidArgumentException(new StatusRuntimeException(
                            INVALID_ARGUMENT.withDescription("Request must have less than 50 items")),
                            GrpcStatusCode.of(Status.Code.INVALID_ARGUMENT),
                            false);
                }
                simulateResourceExhaustion(ImmutableSet.of("createMediaItems", albumId, newMediaItems));

                albumId
                        .filter(s -> fileNameBasedFailuresEnabled)
                        .filter(theAlbumId -> theAlbumId.contains("fail-on-me-pre-existing-album"))
                        .ifPresent(theAlbumId -> {
                            throw new InvalidArgumentException(new StatusRuntimeException(
                                    INVALID_ARGUMENT.withDescription("No permission to add media items to this album")),
                                    GrpcStatusCode.of(Status.Code.INVALID_ARGUMENT),
                                    false);
                        });
                expireTokens();

                List<MediaItemOrError> result = newMediaItems.stream()
                        .map(newMediaItem -> {
                            var uploadedGoogleMediaBinary = checkNotNull(binariesByUploadToken.get(newMediaItem.uploadToken()),
                                    "Unknown upload token: %s", newMediaItem.uploadToken());
                            if (fileNameBasedFailuresEnabled &&
                                    uploadedGoogleMediaBinary.getFile().toString().endsWith("failOnMeWithInvalidArgumentDuringCreationOfMediaItem.jpg")) {
                                logger.warn("Media item upload error for binary {}", uploadedGoogleMediaBinary);
                                return MediaItemOrError.error(com.google.rpc.Status.newBuilder()
                                        .setCode(Code.INVALID_ARGUMENT_VALUE)
                                        .build());
                            } else {
                                var googleMediaItem = new UploadedGoogleMediaItem(
                                        uploadedGoogleMediaBinary,
                                        albumId,
                                        newMediaItem.description());
                                return MediaItemOrError.item(itemsById.compute(googleMediaItem.getId(), (mediaId, existingItem) -> {
                                    if (existingItem == null) {
                                        return googleMediaItem;
                                    }
                                    existingItem = existingItem.withUploadCountIncremented();
                                    albumId.ifPresent(existingItem::addToAlbum);
                                    return existingItem;
                                }));
                            }
                        })
                        .collect(toImmutableList());
                logger.debug("createMediaItems albumId={}, newMediaItems={} result: {}", albumId, newMediaItems, result);
                return result;
            }
        }, executor);
    }

    private void expireTokens() {
        binariesByUploadToken.values().removeIf(binary -> binary.getUploadTime().plus(Duration.ofDays(1)).isBefore(getCurrentInstant()));
    }

    @Override
    public CompletableFuture<GooglePhotosAlbum> createAlbum(String name, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
                if (fileNameBasedFailuresEnabled && name.endsWith("failOnMe")) {
                    throw new RuntimeException("album creation failed");
                }
                simulateResourceExhaustion(ImmutableSet.of("createAlbum", name));
                var album = new CreatedGooglePhotosAlbum(name, generateAlbumId(name));
                albumsById.put(album.getId(), album);
                return album;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<GooglePhotosAlbum>> listAlbums(IntConsumer loadedAlbumCountProgressCallback, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
                simulateResourceExhaustion(ImmutableSet.of("listAlbums"));
                return copyOf(albumsById.values());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<GooglePhotosAlbum> getAlbum(String albumId, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
                simulateResourceExhaustion(ImmutableSet.of("getAlbum", albumId));
                GooglePhotosAlbum album = albumsById.get(albumId);
                checkArgument(album != null, "unknown album id: %s", albumId);
                return album;
            }
        }, executor);
    }

    void enableResourceExhaustedExceptions() {
        synchronized (lock) {
            resourceExhaustedExceptions = true;
        }
    }

    void disableFileNameBaseFailures() {
        synchronized (lock) {
            fileNameBasedFailuresEnabled = false;
        }
    }

    Collection<UploadedGoogleMediaItem> getAllItems() {
        synchronized (lock) {
            return copyOf(itemsById.values());
        }
    }

    List<CreatedGooglePhotosAlbum> getAllAlbums() {
        synchronized (lock) {
            return copyOf(albumsById.values());
        }
    }

    private String generateAlbumId(String name) {
        int idSuffix = albumIdSuffixByName.compute(name, (theName, currentIdSuffix) -> {
            if (currentIdSuffix == null) {
                return 0;
            }
            return currentIdSuffix + 1;
        });
        return idSuffix == 0 ? name : name + idSuffix;
    }

    private void simulateResourceExhaustion(Object key) {
        if (!resourceExhaustedExceptions) {
            return;
        }
        int currentCount = resourceExhaustionCountByKey.compute(key, (ignored, count) -> {
            if (count == null) {
                count = 3;
            }
            return --count;
        });
        if (currentCount > 0) {
            logger.debug("Simulating resource exhaustion for {}, retries left: {}", key, currentCount);
            throw new ResourceExhaustedException(
                    new RuntimeException("exhausted"),
                    GrpcStatusCode.of(Status.Code.RESOURCE_EXHAUSTED),
                    true);
        } else {
            logger.debug("Resource exhaustion count depleted for {} ({}), not simulating exhaustion", key, currentCount);
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static class UploadedGoogleMediaItem implements GoogleMediaItem {
        private final String id;
        private final Optional<String> description;
        private final Set<String> albumIds = new HashSet<>();
        private final UploadedGoogleMediaBinary uploadedGoogleMediaBinary;
        private final Object stateLock = new Object();
        private final AtomicInteger uploadCount = new AtomicInteger(1);

        UploadedGoogleMediaItem(UploadedGoogleMediaBinary uploadedGoogleMediaBinary, Optional<String> albumId, Optional<String> description) {
            this.uploadedGoogleMediaBinary = checkNotNull(uploadedGoogleMediaBinary);
            id = uploadedGoogleMediaBinary.getFile().toAbsolutePath().toString();
            this.description = checkNotNull(description);
            albumId.ifPresent(albumIds::add);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Instant getCreationTime() {
            var fileName = uploadedGoogleMediaBinary.getFile().getFileName().toString();
            return fileName.startsWith("creation-time") ?
                    Instant.parse(fileName.substring("creation-time".length(), fileName.lastIndexOf('.'))) :
                    Instant.EPOCH;
        }

        public int getUploadCount() {
            return uploadCount.get();
        }

        public Optional<String> getDescription() {
            return description;
        }

        public UploadedGoogleMediaBinary getBinary() {
            return uploadedGoogleMediaBinary;
        }

        @Override
        public String toString() {
            synchronized (stateLock) {
                return MoreObjects.toStringHelper(this)
                        .add("id", id)
                        .add("binary", uploadedGoogleMediaBinary)
                        .add("albumIds", albumIds)
                        .add("uploadCount", uploadCount)
                        .toString();
            }
        }

        void addToAlbum(String albumId) {
            synchronized (stateLock) {
                logger.debug("Add item {} to album {}", this, albumId);
                albumIds.add(albumId);
            }
        }

        void removeFromAlbum(String albumId) {
            synchronized (stateLock) {
                logger.debug("remove item {} from album {}", this, albumId);
                checkState(albumIds.remove(albumId), "item %s was not in album %s", id, albumId);
            }
        }

        Set<String> getAlbumIds() {
            synchronized (stateLock) {
                return ImmutableSet.copyOf(albumIds);
            }
        }

        UploadedGoogleMediaItem withUploadCountIncremented() {
            uploadCount.incrementAndGet();
            return this;
        }
    }

    static final class UploadedGoogleMediaBinary {
        private static final AtomicLong counter = new AtomicLong();
        private final String uploadToken;
        private final Path file;
        private final Instant uploadTime;

        UploadedGoogleMediaBinary(Path file) {
            this.file = checkNotNull(file);
            uploadToken = file.toAbsolutePath().toString() + '_' + counter.incrementAndGet();
            uploadTime = getCurrentInstant();
        }

        public String getUploadToken() {
            return uploadToken;
        }

        public Path getFile() {
            return file;
        }

        public Instant getUploadTime() {
            return uploadTime;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("uploadToken", uploadToken)
                    .add("file", file)
                    .toString();
        }
    }

    public class CreatedGooglePhotosAlbum implements GooglePhotosAlbum {
        private final String name;
        private final String id;

        CreatedGooglePhotosAlbum(String name, String id) {
            this.name = checkNotNull(name);
            this.id = checkNotNull(id);
        }

        @Override
        public String getTitle() {
            return name;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public long getMediaItemCount() {
            synchronized (lock) {
                return itemsById.values().stream()
                        .map(UploadedGoogleMediaItem::getAlbumIds)
                        .filter(albumIds -> albumIds.contains(id))
                        .count();
            }
        }

        public List<UploadedGoogleMediaItem> getItems() {
            synchronized (lock) {
                return itemsById.values().stream()
                        .filter(uploadedGoogleMediaItem -> uploadedGoogleMediaItem.getAlbumIds().contains(id))
                        .collect(toImmutableList());
            }
        }

        @Override
        public String getAlbumUrl() {
            return "http://photos.com/" + id;
        }

        @Override
        public CompletableFuture<Void> addMediaItemsByIds(List<String> mediaItemsIds, Executor executor) {
            return CompletableFuture.runAsync(
                    () -> {
                        checkArgument(!mediaItemsIds.isEmpty(), "list must contain at least one media item");
                        if (mediaItemsIds.size() > 50) {
                            throw new InvalidArgumentException(new StatusRuntimeException(
                                    INVALID_ARGUMENT.withDescription("Request must have less than 50 items")),
                                    GrpcStatusCode.of(Status.Code.INVALID_ARGUMENT),
                                    false);

                        }
                        synchronized (lock) {
                            simulateResourceExhaustion(ImmutableSet.of("addMediaItemsByIds", mediaItemsIds));
                            mediaItemsIds.stream()
                                    .peek(mediaItemId -> {
                                        if (mediaItemId.contains("protected")) {
                                            throw new InvalidArgumentException(
                                                    new StatusRuntimeException(INVALID_ARGUMENT),
                                                    GrpcStatusCode.of(Status.Code.INVALID_ARGUMENT),
                                                    false);
                                        }
                                    })
                                    .map(mediaItemId -> checkNotNull(itemsById.get(mediaItemId), "unknown item id: %s", mediaItemId))
                                    .forEach(uploadedGoogleMediaItem -> uploadedGoogleMediaItem.addToAlbum(id));
                        }
                    },
                    executor);
        }

        @Override
        public CompletableFuture<Void> removeMediaItemsByIds(List<String> mediaItemsIds, Executor executor) {
            return CompletableFuture.runAsync(
                    () -> {
                        checkArgument(!mediaItemsIds.isEmpty(), "list must contain at least one media item");
                        checkArgument(mediaItemsIds.size() < 50, "Request must have less than 50 items, but was %s", mediaItemsIds.size());
                        synchronized (lock) {
                            simulateResourceExhaustion(ImmutableSet.of("removeMediaItemsByIds", mediaItemsIds));
                            mediaItemsIds.stream()
                                    .peek(mediaItemId -> {
                                        if (mediaItemId.contains("protected")) {
                                            throw new InvalidArgumentException(
                                                    new StatusRuntimeException(INVALID_ARGUMENT),
                                                    GrpcStatusCode.of(Status.Code.INVALID_ARGUMENT),
                                                    false);
                                        }
                                    })
                                    .map(mediaItemId -> checkNotNull(itemsById.get(mediaItemId), "unknown item id: %s", mediaItemId))
                                    .forEach(uploadedGoogleMediaItem -> uploadedGoogleMediaItem.removeFromAlbum(id));
                        }
                    },
                    executor);
        }

        @Override
        public CompletableFuture<List<GoogleMediaItem>> getMediaItems(IntConsumer loadedItemCountProgressCallback, Executor executor) {
            return CompletableFuture.supplyAsync(
                    () -> {
                        synchronized (lock) {
                            simulateResourceExhaustion(ImmutableSet.of("getMediaItems"));
                            return itemsById.values().stream()
                                    .filter(mediaItem -> mediaItem.getAlbumIds().contains(id))
                                    .collect(toImmutableList());
                        }
                    },
                    executor);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("name", name)
                    .add("id", id)
                    .toString();
        }
    }
}
