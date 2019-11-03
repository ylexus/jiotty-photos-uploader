package net.yudichev.googlephotosupload.app;

import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.common.lang.CompletableFutures;
import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import net.yudichev.jiotty.connector.google.photos.GoogleMediaItem;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosClient;
import org.immutables.value.Value;
import org.immutables.value.Value.Immutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.lang.Math.min;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static net.yudichev.googlephotosupload.app.Bindings.Backoff;
import static net.yudichev.googlephotosupload.app.Bindings.Backpressured;
import static net.yudichev.jiotty.common.lang.CompletableFutures.logErrorOnFailure;
import static net.yudichev.jiotty.common.lang.CompletableFutures.toFutureOfList;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

final class AlbumManagerImpl extends BaseLifecycleComponent implements AlbumManager {
    private static final Logger logger = LoggerFactory.getLogger(AlbumManagerImpl.class);

    private final GooglePhotosClient googlePhotosClient;
    private final RemoteApiResultHandler backOffHandler;
    private final DirectoryStructureSupplier directoryStructureSupplier;
    private final Provider<ExecutorService> executorServiceProvider;
    private ExecutorService executorService;
    private Map<String, GooglePhotosAlbum> albumsByTitle;

    @Inject
    AlbumManagerImpl(GooglePhotosClient googlePhotosClient,
                     @Backpressured Provider<ExecutorService> executorServiceProvider,
                     @Backoff RemoteApiResultHandler backOffHandler,
                     DirectoryStructureSupplier directoryStructureSupplier) {
        this.googlePhotosClient = checkNotNull(googlePhotosClient);
        this.executorServiceProvider = checkNotNull(executorServiceProvider);
        this.backOffHandler = checkNotNull(backOffHandler);
        this.directoryStructureSupplier = checkNotNull(directoryStructureSupplier);
    }

    @Override
    public GooglePhotosAlbum albumForTitle(String albumTitle) {
        GooglePhotosAlbum album = albumsByTitle.get(albumTitle);
        checkArgument(album != null, "No known album for title {}", albumTitle);
        return album;
    }

    @Override
    protected void doStart() {
        executorService = executorServiceProvider.get();
        logger.info("Reconciling folders with Google Photos cloud");

        logger.info("Loading albums in cloud (may take several minutes)...");
        List<GooglePhotosAlbum> albumsInCloud = getAsUnchecked(() ->
                withBackOffAndRetry("get all albums", googlePhotosClient::listAlbums).get(30, TimeUnit.MINUTES));
        logger.info("... loaded {} album(s) in cloud", albumsInCloud.size());

        Map<String, List<GooglePhotosAlbum>> cloudAlbumsByTitle = new HashMap<>(albumsInCloud.size());
        albumsInCloud.forEach(googlePhotosAlbum ->
                cloudAlbumsByTitle.computeIfAbsent(googlePhotosAlbum.getTitle(), title -> new ArrayList<>()).add(googlePhotosAlbum));

        List<AlbumDirectory> albumDirectories = directoryStructureSupplier.getAlbumDirectories();

        // TODO user option "in case of album name match reuse existing albums, do not create new ones"
        logger.info("Reconciling {} albums(s) with Google Photos, may take a bit of time...", albumDirectories.size());
        albumsByTitle = albumDirectories.stream()
                .map(albumDirectory -> albumDirectory.albumTitle()
                        .map(albumTitle -> reconcile(cloudAlbumsByTitle, albumTitle, albumDirectory.path())))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(future -> getAsUnchecked(() -> future.get(1, TimeUnit.HOURS)))
                .collect(toImmutableMap(
                        GooglePhotosAlbum::getTitle,
                        Function.identity()));
        logger.info("... done");
    }

    private CompletableFuture<GooglePhotosAlbum> reconcile(Map<String, List<GooglePhotosAlbum>> cloudAlbumsByTitle,
                                                           String filesystemAlbumTitle,
                                                           Path path) {
        CompletableFuture<GooglePhotosAlbum> albumFuture;
        List<GooglePhotosAlbum> cloudAlbumsForThisTitle = cloudAlbumsByTitle.get(filesystemAlbumTitle);
        if (cloudAlbumsForThisTitle == null) {
            logger.info("Creating album [{}] for path [{}]", filesystemAlbumTitle, path);
            albumFuture = withBackOffAndRetry(
                    "create album " + filesystemAlbumTitle,
                    () -> googlePhotosClient.createAlbum(filesystemAlbumTitle, executorService));
        } else if (cloudAlbumsForThisTitle.size() > 1) {
            List<GooglePhotosAlbum> nonEmptyCloudAlbumsForThisTitle = cloudAlbumsForThisTitle.stream()
                    .filter(googlePhotosAlbum -> googlePhotosAlbum.getMediaItemCount() > 0)
                    .collect(toImmutableList());
            if (nonEmptyCloudAlbumsForThisTitle.isEmpty()) {
                cloudAlbumsForThisTitle.stream()
                        .skip(1)
                        .forEach(this::removeAlbum);
                return completedFuture(cloudAlbumsForThisTitle.get(0));
            } else if (nonEmptyCloudAlbumsForThisTitle.size() > 1) {
                GooglePhotosAlbum primaryAlbum = cloudAlbumsForThisTitle.get(0);
                logger.info("Merging {} duplicate cloud album(s) for path [{}] into {}", cloudAlbumsForThisTitle.size(), path, primaryAlbum);
                albumFuture = mergeAlbums(primaryAlbum, cloudAlbumsForThisTitle.subList(1, cloudAlbumsForThisTitle.size()));
            } else {
                albumFuture = completedFuture(nonEmptyCloudAlbumsForThisTitle.get(0));
            }
        } else {
            albumFuture = completedFuture(cloudAlbumsForThisTitle.get(0));
        }
        return albumFuture;
    }

    private CompletableFuture<GooglePhotosAlbum> mergeAlbums(GooglePhotosAlbum primaryAlbum, List<GooglePhotosAlbum> albumsToBeMerged) {
        return albumsToBeMerged.stream()
                .map(albumToBeMerged -> albumToBeMerged.getMediaItems()
                        .thenCompose(mediaItems -> mediaItems.isEmpty() ?
                                CompletableFutures.completedFuture() :
                                // move these items to primary album
                                moveItems(albumToBeMerged, primaryAlbum, mediaItems))
                        .thenRun(() -> removeAlbum(albumToBeMerged)))
                .collect(toFutureOfList())
                .thenApply(list -> primaryAlbum);
    }

    private CompletableFuture<Void> moveItems(GooglePhotosAlbum sourceAlbum, GooglePhotosAlbum destinationAlbum, List<GoogleMediaItem> mediaItems) {
        int maxItemsPerRequest = 49;
        return IntStream.range(0, mediaItems.size() / maxItemsPerRequest + 1)
                .mapToObj(groupNumber -> mediaItems.subList(
                        groupNumber * maxItemsPerRequest,
                        min((groupNumber + 1) * maxItemsPerRequest, mediaItems.size())))
                .map(itemsInGroup -> {
                    logger.debug("Moving a batch of {} items from {} to {}", itemsInGroup.size(), sourceAlbum.getTitle(), destinationAlbum.getTitle());
                    return withBackOffAndRetry(
                            "add items for " + sourceAlbum.getTitle() + " to album " + destinationAlbum.getId(),
                            () -> destinationAlbum.addMediaItems(itemsInGroup, executorService))
                            // 2. remove items from this album
                            .thenCompose(aVoid -> withBackOffAndRetry(
                                    "remove items for " + sourceAlbum.getTitle() + " from album " + sourceAlbum.getId(),
                                    () -> sourceAlbum.removeMediaItems(itemsInGroup, executorService)));
                })
                .collect(toFutureOfList())
                .thenApply(list -> null);
    }

    private void removeAlbum(GooglePhotosAlbum albumToBeMerged) {
        // remove this album - CAN'T DO THIS :-(, so flagging to user
        logger.info("MANUAL ACTION NEEDED: remove empty album '{}': {}",
                albumToBeMerged.getTitle(), albumToBeMerged.getAlbumUrl());
    }

    private <T> CompletableFuture<T> withBackOffAndRetry(String operationName, Supplier<CompletableFuture<T>> action) {
        return action.get()
                .thenApply(value -> {
                    backOffHandler.reset();
                    return Either.<T, RetryableFailure>left(value);
                })
                .exceptionally(exception -> {
                    boolean shouldRetry = backOffHandler.handle(operationName, exception);
                    return Either.right(RetryableFailure.of(exception, shouldRetry));
                })
                .thenCompose(eitherValueOrRetryableFailure -> eitherValueOrRetryableFailure.map(
                        CompletableFuture::completedFuture,
                        retryableFailure -> {
                            if (retryableFailure.shouldRetry()) {
                                logger.debug("Retrying operation '{}'", operationName);
                                return withBackOffAndRetry(operationName, action);
                            } else {
                                return CompletableFutures.failure(retryableFailure.exception());
                            }
                        }
                ))
                .whenComplete(logErrorOnFailure(logger, "Unhandled exception performing '%s'", operationName));
    }

    @Immutable
    @PackagePrivateImmutablesStyle
    interface BaseRetryableFailure {
        @Value.Parameter
        Throwable exception();

        @Value.Parameter
        boolean shouldRetry();
    }
}
