package net.yudichev.googlephotosupload.app;

import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.ResourceExhaustedException;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.rpc.Code;
import io.grpc.Status;
import net.yudichev.jiotty.connector.google.photos.GoogleMediaItem;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosClient;
import net.yudichev.jiotty.connector.google.photos.MediaItemCreationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.google.common.base.Preconditions.*;
import static com.google.common.collect.ImmutableList.copyOf;
import static com.google.common.collect.ImmutableList.toImmutableList;

final class RecordingGooglePhotosClient implements GooglePhotosClient {
    private static final Logger logger = LoggerFactory.getLogger(RecordingGooglePhotosClient.class);

    private final Map<String, UploadedGoogleMediaItem> itemsById = new LinkedHashMap<>();
    private final Map<String, CreatedGooglePhotosAlbum> albumsById = new LinkedHashMap<>();
    private final Map<String, Integer> albumIdSuffixByName = new LinkedHashMap<>();
    private final Map<Object, Integer> resourceExhaustionCountByKey = new LinkedHashMap<>();
    private final Object lock = new Object();

    private boolean resourceExhaustedExceptions;

    @Override
    public CompletableFuture<GoogleMediaItem> uploadMediaItem(Optional<String> albumId, Path path, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
                if (!path.toString().endsWith(".jpg")) {
                    throw new MediaItemCreationFailedException("Unable to create media item for file",
                            com.google.rpc.Status.newBuilder()
                                    .setCode(Code.INVALID_ARGUMENT_VALUE)
                                    .setMessage("Failed: There was an error while trying to create this media item.")
                                    .build());
                }
                if (path.toString().endsWith("failOnMe.jpg")) {
                    throw new RuntimeException("upload failed");
                }
                simulateResourceExhaustion(ImmutableSet.of("uploadMediaItem", path));
                UploadedGoogleMediaItem item = new UploadedGoogleMediaItem(path, albumId);
                UploadedGoogleMediaItem existingItem = itemsById.putIfAbsent(item.getId(), item);
                checkArgument(existingItem == null, "item with such ID already exists: %s", existingItem);
                return item;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<GooglePhotosAlbum> createAlbum(String name, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
                if (name.endsWith("failOnMe")) {
                    throw new RuntimeException("album creation failed");
                }
                simulateResourceExhaustion(ImmutableSet.of("createAlbum", name));
                CreatedGooglePhotosAlbum album = new CreatedGooglePhotosAlbum(name, generateAlbumId(name));
                albumsById.put(album.getId(), album);
                return album;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<GooglePhotosAlbum>> listAlbums(Executor executor) {
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
        resourceExhaustedExceptions = true;
    }

    Collection<UploadedGoogleMediaItem> getAllItems() {
        synchronized (lock) {
            return ImmutableList.copyOf(itemsById.values());
        }
    }

    List<CreatedGooglePhotosAlbum> getAllAlbums() {
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
                count = 2;
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
        private final Set<String> albumIds = new HashSet<>();
        private final Path file;
        private final Object stateLock = new Object();

        UploadedGoogleMediaItem(Path file, Optional<String> albumId) {
            this.file = checkNotNull(file);
            id = file.toAbsolutePath().toString();
            albumId.ifPresent(albumIds::add);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String toString() {
            synchronized (stateLock) {
                return MoreObjects.toStringHelper(this)
                        .add("id", id)
                        .add("file", file)
                        .add("albumIds", albumIds)
                        .toString();
            }
        }

        void addToAlbum(String albumId) {
            synchronized (stateLock) {
                logger.debug("Add item {} to album {}", this, albumId);
                checkState(albumIds.add(albumId), "item %s is already in album %s", id, albumId);
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

        @Override
        public String getAlbumUrl() {
            return "http://photos.com/" + id;
        }

        @Override
        public CompletableFuture<Void> addMediaItemsByIds(List<String> list, Executor executor) {
            return CompletableFuture.runAsync(
                    () -> {
                        checkArgument(!list.isEmpty(), "list must contain at least one media item");
                        checkArgument(list.size() < 50, "Request must have less than 50 items, but was %s", list.size());
                        synchronized (lock) {
                            simulateResourceExhaustion(ImmutableSet.of("addMediaItemsByIds", list));
                            list.stream()
                                    .map(mediaItemId -> checkNotNull(itemsById.get(mediaItemId), "unknown item id: %s", mediaItemId))
                                    .forEach(uploadedGoogleMediaItem -> uploadedGoogleMediaItem.addToAlbum(id));
                        }
                    },
                    executor);
        }

        @Override
        public CompletableFuture<Void> removeMediaItemsByIds(List<String> list, Executor executor) {
            return CompletableFuture.runAsync(
                    () -> {
                        checkArgument(!list.isEmpty(), "list must contain at least one media item");
                        checkArgument(list.size() < 50, "Request must have less than 50 items, but was %s", list.size());
                        synchronized (lock) {
                            simulateResourceExhaustion(ImmutableSet.of("removeMediaItemsByIds", list));
                            list.stream()
                                    .map(mediaItemId -> checkNotNull(itemsById.get(mediaItemId), "unknown item id: %s", mediaItemId))
                                    .forEach(uploadedGoogleMediaItem -> uploadedGoogleMediaItem.removeFromAlbum(id));
                        }
                    },
                    executor);
        }

        @Override
        public CompletableFuture<List<GoogleMediaItem>> getMediaItems(Executor executor) {
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
