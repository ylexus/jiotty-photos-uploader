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
import net.yudichev.jiotty.common.lang.Json;
import net.yudichev.jiotty.connector.google.photos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.nio.file.Files;
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

import static com.google.common.base.Preconditions.*;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.grpc.Status.INVALID_ARGUMENT;
import static net.yudichev.googlephotosupload.core.GooglePhotosUploaderImpl.GOOGLE_PHOTOS_API_BATCH_SIZE;
import static net.yudichev.googlephotosupload.core.TestTimeModule.getCurrentInstant;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

@SuppressWarnings("ClassWithTooManyFields")
final class RecordingGooglePhotosClient implements GooglePhotosClient {
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy_MM_dd_HH_mm_ss")
            .withZone(ZoneOffset.UTC);
    private static final Logger logger = LoggerFactory.getLogger(RecordingGooglePhotosClient.class);
    private final Map<String, MediaBinary> binariesByUploadToken = new LinkedHashMap<>();
    private final Map<MediaItemContents, MediaItem> itemsByContents = new LinkedHashMap<>();
    private final Map<String, MediaItem> itemsById = new LinkedHashMap<>();
    private final Map<String, Album> albumsById = new LinkedHashMap<>();
    private final Map<String, Integer> albumIdSuffixByName = new LinkedHashMap<>();
    private final Map<Object, Integer> resourceExhaustionCountByKey = new LinkedHashMap<>();
    private final Object lock = new Object();
    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") // analysis failure
    private boolean fileNameBasedFailuresEnabled = true;
    private boolean resourceExhaustedExceptions;
    private boolean listAlbumsNeverReturningEnabled;

    @Override
    public CompletableFuture<String> uploadMediaData(Path file, Executor executor) {
        logger.info("uploadMediaData {}", file);
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
                if (fileNameBasedFailuresEnabled) {
                    if (file.toString().endsWith("failOnMe.jpg")) {
                        throw new RuntimeException("upload failed");
                    }
                    if (file.toString().endsWith("failOnMeWithInvalidArgumentDuringUploadIngMediaData.jpg")) {
                        throw invalidArgumentException("uploadMediaData");
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
                if (newMediaItems.size() > GOOGLE_PHOTOS_API_BATCH_SIZE) {
                    throw invalidArgumentException("Request must have less than 50 items");
                }
                simulateResourceExhaustion(ImmutableSet.of("createMediaItems", albumId, newMediaItems));

                expireTokens();

                Set<String> mediaItemIdsInThisRequest = new HashSet<>();
                List<MediaItemOrError> result = newMediaItems.stream()
                        .map(newMediaItem -> {
                            var uploadedGoogleMediaBinary = checkNotNull(binariesByUploadToken.get(newMediaItem.uploadToken()),
                                    "Unknown upload token: %s", newMediaItem.uploadToken());
                            if (fileNameBasedFailuresEnabled &&
                                    uploadedGoogleMediaBinary.getUploadToken().contains("failOnMeWithInvalidArgumentDuringCreationOfMediaItem")) {
                                logger.warn("Media item upload error for binary {}", uploadedGoogleMediaBinary);
                                return MediaItemOrError.error(com.google.rpc.Status.newBuilder()
                                        .setCode(Code.INVALID_ARGUMENT_VALUE)
                                        .setMessage("createMediaItems")
                                        .build());
                            } else {
                                var newItemCandidate = new MediaItem(uploadedGoogleMediaBinary, newMediaItem.description());

                                // This is an extremely strange observed behaviour of Google Photos: within same request,
                                // duplicate binaries are not allowed, but across two requests it's OK
                                if (!mediaItemIdsInThisRequest.add(newItemCandidate.getId())) {
                                    return MediaItemOrError.error(com.google.rpc.Status.newBuilder()
                                            .setCode(Code.ALREADY_EXISTS_VALUE)
                                            .setMessage("Failed: There was an error while trying to create this media item.")
                                            .build());
                                }

                                var newItem = itemsByContents.merge(uploadedGoogleMediaBinary.getContents(), newItemCandidate,
                                        (existingItem, theNewItem) -> existingItem.withUploadCountIncremented());

                                var previousItemById = itemsById.put(newItem.getId(), newItem);
                                checkState(previousItemById == null || previousItemById == newItem);

                                albumId.ifPresent(newItem::addToAlbum);

                                return MediaItemOrError.item(newItem);
                            }
                        })
                        .collect(toImmutableList());
                logger.info("createMediaItems albumId={}, newMediaItems={} result: {}", albumId, newMediaItems, result);
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
                var album = new Album(name, generateAlbumId(name), true);
                albumsById.put(album.getId(), album);
                return album;
            }
        }, executor);
    }

    public GooglePhotosAlbum createNonWritableAlbum(String name) {
        synchronized (lock) {
            var album = new Album(name, generateAlbumId(name), false);
            albumsById.put(album.getId(), album);
            return album;
        }
    }

    @Override
    public CompletableFuture<List<GooglePhotosAlbum>> listAlbums(IntConsumer loadedAlbumCountProgressCallback, Executor executor) {
        synchronized (lock) {
            if (listAlbumsNeverReturningEnabled) {
                return new CompletableFuture<>();
            } else {
                return CompletableFuture.supplyAsync(() -> {
                    synchronized (lock) {
                        simulateResourceExhaustion(ImmutableSet.of("listAlbums"));
                        return ImmutableList.copyOf(albumsById.values());
                    }
                }, executor);
            }
        }
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

    void enableListAlbumsNeverReturning() {
        synchronized (lock) {
            listAlbumsNeverReturningEnabled = true;
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
        private final Instant uploadTime;
        private final MediaItemContents contents;

        MediaBinary(Path file) {
            contents = getAsUnchecked(() -> Json.parse(Files.readString(file), MediaItemContents.class));
            uploadToken = file.toAbsolutePath().toString() + '_' + counter.incrementAndGet();
            uploadTime = getCurrentInstant();
        }

        public String getUploadToken() {
            return uploadToken;
        }

        public MediaItemContents getContents() {
            return contents;
        }

        public Instant getUploadTime() {
            return uploadTime;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("uploadToken", uploadToken)
                    .add("contents", contents)
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
            id = mediaBinary.getContents().toMediaItemId();
            this.description = checkNotNull(description);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Instant getCreationTime() {
            return mediaBinary.getContents().creationTime();
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
            if (fileNameBasedFailuresEnabled && albumId.contains("fail-on-me-pre-existing-album")) {
                throw new InvalidArgumentException(new StatusRuntimeException(
                        INVALID_ARGUMENT.withDescription("No permission to add media items to this album")),
                        GrpcStatusCode.of(Status.Code.INVALID_ARGUMENT),
                        false);
            }
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
                        .add("mediaBinary", mediaBinary)
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
        private final boolean writable;
        private final Set<MediaItem> items = new LinkedHashSet<>();

        Album(String name, String id, boolean writable) {
            this.name = checkNotNull(name);
            this.id = checkNotNull(id);
            this.writable = writable;
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
        public boolean isWriteable() {
            return writable;
        }

        @Override
        public CompletableFuture<Void> addMediaItemsByIds(List<String> mediaItemsIds, Executor executor) {
            logger.info("addMediaItemsByIds to album {}: {}", id, mediaItemsIds);
            return CompletableFuture.runAsync(
                    () -> {
                        checkArgument(!mediaItemsIds.isEmpty(), "list must contain at least one media item");
                        if (mediaItemsIds.size() > GOOGLE_PHOTOS_API_BATCH_SIZE) {
                            throw invalidArgumentException("Request must have less than 50 items");

                        }
                        if (mediaItemsIds.stream().distinct().count() < mediaItemsIds.size()) {
                            throw new InvalidArgumentException(new StatusRuntimeException(
                                    INVALID_ARGUMENT.withDescription("Request must not contain duplicated ids")),
                                    GrpcStatusCode.of(Status.Code.INVALID_ARGUMENT),
                                    false);

                        }
                        internalAddMediaItemsById(mediaItemsIds);
                    },
                    executor);
        }

        private void internalAddMediaItemsById(List<String> mediaItemsIds) {
            if (fileNameBasedFailuresEnabled && id.contains("pre-existing-not-writable-album")) {
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
