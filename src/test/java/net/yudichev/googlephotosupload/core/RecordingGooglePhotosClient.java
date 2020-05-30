package net.yudichev.googlephotosupload.core;

import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.api.gax.rpc.ResourceExhaustedException;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.rpc.Code;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.yudichev.jiotty.connector.google.photos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static io.grpc.Status.INVALID_ARGUMENT;
import static net.yudichev.googlephotosupload.core.TestTimeModule.getCurrentInstant;

final class RecordingGooglePhotosClient implements GooglePhotosClient {
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy_MM_dd_HH_mm_ss")
            .withZone(ZoneOffset.UTC);
    private static final Logger logger = LoggerFactory.getLogger(RecordingGooglePhotosClient.class);

    private final Map<String, MediaBinary> binariesByUploadToken = new LinkedHashMap<>();
    private final Map<String, MediaItem> itemsById = new LinkedHashMap<>();
    private final Map<String, Album> albumsById = new LinkedHashMap<>();
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
                        throw invalidArgumentException("Some invalid argument error");
                    }
                }
                simulateResourceExhaustion(ImmutableSet.of("uploadMediaData", file));
                var binary = new MediaBinary(file);
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
                    throw invalidArgumentException("Request must have less than 50 items");
                }
                simulateResourceExhaustion(ImmutableSet.of("createMediaItems", albumId, newMediaItems));

                albumId
                        .filter(s -> fileNameBasedFailuresEnabled)
                        .filter(theAlbumId -> theAlbumId.contains("fail-on-me-pre-existing-album"))
                        .ifPresent(theAlbumId -> {
                            throw invalidArgumentException("No permission to add media items to this album");
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
                                var candidateNewItem = new MediaItem(uploadedGoogleMediaBinary, newMediaItem.description());
                                var createdMediaItem = itemsById.compute(candidateNewItem.getId(), (mediaId, existingItem) -> {
                                    if (existingItem == null) {
                                        existingItem = candidateNewItem;
                                    } else {
                                        existingItem = existingItem.withUploadCountIncremented();
                                    }
                                    return existingItem;
                                });
                                albumId.ifPresent(createdMediaItem::addToAlbum);
                                return MediaItemOrError.item(createdMediaItem);
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
                var album = new Album(name, generateAlbumId(name));
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
                return ImmutableList.copyOf(albumsById.values());
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

    Collection<MediaItem> getAllItems() {
        synchronized (lock) {
            return ImmutableList.copyOf(itemsById.values());
        }
    }

    List<Album> getAllAlbums() {
        synchronized (lock) {
            return ImmutableList.copyOf(albumsById.values());
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

    @Nonnull
    private static InvalidArgumentException invalidArgumentException(String s) {
        return new InvalidArgumentException(new StatusRuntimeException(
                INVALID_ARGUMENT.withDescription(s)),
                GrpcStatusCode.of(Status.Code.INVALID_ARGUMENT),
                false);
    }

    static final class MediaBinary {
        private static final AtomicLong counter = new AtomicLong();
        private final String uploadToken;
        private final Path file;
        private final Instant uploadTime;

        MediaBinary(Path file) {
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

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public class MediaItem implements GoogleMediaItem {
        private final String id;
        private final Optional<String> description;
        private final MediaBinary mediaBinary;
        private final Object stateLock = new Object();
        private final AtomicInteger uploadCount = new AtomicInteger(1);

        MediaItem(MediaBinary mediaBinary, Optional<String> description) {
            this.mediaBinary = checkNotNull(mediaBinary);
            id = mediaBinary.getFile().toAbsolutePath().toString();
            this.description = checkNotNull(description);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Instant getCreationTime() {
            var fileName = mediaBinary.getFile().getFileName().toString();
            return fileName.startsWith("creation-time-") ?
                    Instant.from(FILE_DATE_FORMAT.parse(fileName.substring("creation-time-".length(), fileName.lastIndexOf('.')))) :
                    Instant.EPOCH;
        }

        public int getUploadCount() {
            return uploadCount.get();
        }

        public Optional<String> getDescription() {
            return description;
        }

        public MediaBinary getBinary() {
            return mediaBinary;
        }

        void addToAlbum(String albumId) {
            synchronized (stateLock) {
                logger.debug("Add item {} to album {}", this, albumId);
                lookupAlbumOrFail(albumId).internalAddMediaItemsById(ImmutableList.of(id));
            }
        }

        MediaItem withUploadCountIncremented() {
            uploadCount.incrementAndGet();
            return this;
        }

        private Album lookupAlbumOrFail(String theAlbumId) {
            var album = albumsById.get(theAlbumId);
            if (album == null) {
                throw invalidArgumentException("invalid album id: " + theAlbumId);
            }
            return album;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            return id.equals(((MediaItem) obj).id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            synchronized (stateLock) {
                return MoreObjects.toStringHelper(this)
                        .add("id", id)
                        .add("uploadCount", uploadCount)
                        .toString();
            }
        }

        public Set<String> getAlbumIds() {
            return albumsById.values().stream()
                    .filter(album -> album.getItems().stream().anyMatch(mediaItem -> mediaItem.equals(this)))
                    .map(Album::getId)
                    .collect(ImmutableSet.toImmutableSet());
        }
    }

    public class Album implements GooglePhotosAlbum {
        private final String name;
        private final String id;
        private final Set<MediaItem> items = new LinkedHashSet<>();

        Album(String name, String id) {
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
                return items.size();
            }
        }

        public List<MediaItem> getItems() {
            synchronized (lock) {
                return ImmutableList.copyOf(items);
            }
        }

        @Override
        public String getAlbumUrl() {
            return "http://photos.com/" + id;
        }

        @Override
        public CompletableFuture<Void> addMediaItemsByIds(List<String> mediaItemsIds, Executor executor) {
            logger.info("addMediaItemsByIds to album {}: {}", id, mediaItemsIds);
            return CompletableFuture.runAsync(
                    () -> {
                        checkArgument(!mediaItemsIds.isEmpty(), "list must contain at least one media item");
                        if (mediaItemsIds.size() > 50) {
                            throw invalidArgumentException("Request must have less than 50 items");

                        }
                        internalAddMediaItemsById(mediaItemsIds);
                    },
                    executor);
        }

        private void internalAddMediaItemsById(List<String> mediaItemsIds) {
            if (fileNameBasedFailuresEnabled && id.contains("fail-on-me-pre-existing-album")) {
                throw invalidArgumentException("No permission to add media items to this album");
            }
            synchronized (lock) {
                simulateResourceExhaustion(ImmutableSet.of("addMediaItemsByIds", mediaItemsIds));
                mediaItemsIds.stream()
                        .map(mediaItemId -> checkNotNull(itemsById.get(mediaItemId), "unknown item id: %s", mediaItemId))
                        .forEach(items::add);
            }
        }

        @Override
        public CompletableFuture<Void> removeMediaItemsByIds(List<String> mediaItemsIds, Executor executor) {
            return CompletableFuture.runAsync(
                    () -> {
                        checkArgument(!mediaItemsIds.isEmpty(), "list must contain at least one media item");
                        checkArgument(mediaItemsIds.size() < 50, "Request must have less than 50 items, but was %s", mediaItemsIds.size());
                        synchronized (lock) {
                            simulateResourceExhaustion(ImmutableSet.of("removeMediaItemsByIds", mediaItemsIds));
                            internalRemoveMediaItemsById(mediaItemsIds);
                        }
                    },
                    executor);
        }

        private void internalRemoveMediaItemsById(List<String> mediaItemsIds) {
            mediaItemsIds.stream()
                    .map(mediaItemId -> checkNotNull(itemsById.get(mediaItemId), "unknown item id: %s", mediaItemId))
                    .forEach(items::remove);
        }

        @Override
        public CompletableFuture<List<GoogleMediaItem>> getMediaItems(IntConsumer loadedItemCountProgressCallback, Executor executor) {
            return CompletableFuture.supplyAsync(
                    () -> {
                        synchronized (lock) {
                            simulateResourceExhaustion(ImmutableSet.of("getMediaItems"));
                            return ImmutableList.copyOf(items);
                        }
                    },
                    executor);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("name", name)
                    .add("id", id)
                    .add("items", items)
                    .toString();
        }
    }
}
