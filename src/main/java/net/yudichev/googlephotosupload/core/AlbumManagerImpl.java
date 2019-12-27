package net.yudichev.googlephotosupload.core;

import com.google.api.gax.rpc.InvalidArgumentException;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.common.lang.CompletableFutures;
import net.yudichev.jiotty.connector.google.photos.GoogleMediaItem;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.getCausalChain;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.lang.Math.min;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static net.yudichev.googlephotosupload.core.Bindings.Backpressured;
import static net.yudichev.jiotty.common.lang.CompletableFutures.toFutureOfList;

final class AlbumManagerImpl extends BaseLifecycleComponent implements AlbumManager {
    private static final Logger logger = LoggerFactory.getLogger(AlbumManagerImpl.class);

    private final GooglePhotosClient googlePhotosClient;
    private final Provider<ExecutorService> executorServiceProvider;
    private final CloudOperationHelper cloudOperationHelper;
    private final ProgressStatusFactory progressStatusFactory;
    private ExecutorService executorService;

    @Inject
    AlbumManagerImpl(GooglePhotosClient googlePhotosClient,
                     @Backpressured Provider<ExecutorService> executorServiceProvider,
                     CloudOperationHelper cloudOperationHelper,
                     ProgressStatusFactory progressStatusFactory) {
        this.googlePhotosClient = checkNotNull(googlePhotosClient);
        this.executorServiceProvider = checkNotNull(executorServiceProvider);
        this.cloudOperationHelper = checkNotNull(cloudOperationHelper);
        this.progressStatusFactory = checkNotNull(progressStatusFactory);
    }

    @Override
    public CompletableFuture<Map<String, GooglePhotosAlbum>> listAlbumsByTitle(List<AlbumDirectory> albumDirectories, Map<String, List<GooglePhotosAlbum>> cloudAlbumsByTitle) {
        checkStarted();
        int reconcilableAlbumCount = albumDirectories.size() - 1;
        logger.info("Reconciling {} albums(s) with Google Photos, may take a bit of time...", reconcilableAlbumCount);
        ProgressStatus progressStatus = progressStatusFactory.create(
                String.format("Reconciling %s album(s) with Google Photos", reconcilableAlbumCount),
                Optional.of(reconcilableAlbumCount)); // root directory is excluded from progress as it does not need to be reconciled
        // TODO user option "in case of album name match reuse existing albums, do not create new ones"
        return albumDirectories.stream()
                .map(albumDirectory -> albumDirectory.albumTitle()
                        .map(albumTitle -> reconcile(cloudAlbumsByTitle, albumTitle, albumDirectory.path())
                                .whenComplete((album, e) -> progressStatus.incrementSuccess())))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toFutureOfList())
                .<Map<String, GooglePhotosAlbum>>thenApply(googlePhotosAlbums -> googlePhotosAlbums.stream()
                        .collect(toImmutableMap(
                                GooglePhotosAlbum::getTitle,
                                Function.identity())))
                .whenComplete((ignored, e) -> progressStatus.close());
    }

    @Override
    protected void doStart() {
        executorService = executorServiceProvider.get();
    }

    private CompletableFuture<GooglePhotosAlbum> reconcile(Map<String, List<GooglePhotosAlbum>> cloudAlbumsByTitle,
                                                           String filesystemAlbumTitle,
                                                           Path path) {
        CompletableFuture<GooglePhotosAlbum> albumFuture;
        List<GooglePhotosAlbum> cloudAlbumsForThisTitle = cloudAlbumsByTitle.get(filesystemAlbumTitle);
        if (cloudAlbumsForThisTitle == null) {
            logger.info("Creating album [{}] for path [{}]", filesystemAlbumTitle, path);
            albumFuture = cloudOperationHelper.withBackOffAndRetry("create album " + filesystemAlbumTitle, () -> googlePhotosClient.createAlbum(filesystemAlbumTitle, executorService));
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
        return getItemsInAlbum(primaryAlbum)
                .thenCompose(itemsInPrimaryAlbum -> {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Items currently in primary album: {}", mediaItemsToIds(itemsInPrimaryAlbum));
                    }
                    return albumsToBeMerged.stream()
                            .map(albumToBeMerged -> moveItems(albumToBeMerged, primaryAlbum, itemsInPrimaryAlbum)
                                    .thenRun(() -> removeAlbum(albumToBeMerged)))
                            .collect(toFutureOfList())
                            .thenApply(list -> primaryAlbum);
                });
    }

    private List<GoogleMediaItem> without(List<GoogleMediaItem> mediaItems, List<GoogleMediaItem> itemsToExclude) {
        ArrayList<GoogleMediaItem> result = new ArrayList<>(mediaItems);
        result.removeAll(itemsToExclude);
        return result;
    }

    private CompletableFuture<Void> moveItems(GooglePhotosAlbum sourceAlbum,
                                              GooglePhotosAlbum destinationAlbum,
                                              List<GoogleMediaItem> itemsInDestinationAlbum) {
        int maxItemsPerRequest = 49;
        return getItemsInAlbum(sourceAlbum).thenCompose(itemsInSourceAlbum -> itemsInSourceAlbum.isEmpty() ?
                CompletableFutures.completedFuture() :
                IntStream.range(0, itemsInSourceAlbum.size() / maxItemsPerRequest + 1)
                        .mapToObj(groupNumber -> itemsInSourceAlbum.subList(
                                groupNumber * maxItemsPerRequest,
                                min((groupNumber + 1) * maxItemsPerRequest, itemsInSourceAlbum.size())))
                        .filter(itemsInGroup -> !itemsInGroup.isEmpty())
                        .map(itemsInGroup -> {
                            logger.debug("Moving a batch of {} items for {} from {} to {}",
                                    itemsInGroup.size(), sourceAlbum.getTitle(), sourceAlbum.getId(), destinationAlbum.getId());
                            List<GoogleMediaItem> itemsToAdd = without(itemsInGroup, itemsInDestinationAlbum);
                            CompletableFuture<Void> addFuture;
                            if (itemsToAdd.isEmpty()) {
                                addFuture = CompletableFutures.completedFuture();
                            } else {
                                if (logger.isDebugEnabled()) {
                                    logger.debug("Add to album {} items {}", destinationAlbum.getId(), mediaItemsToIds(itemsToAdd));
                                }
                                String addOperationName = "add " + itemsToAdd.size() + " items for " + sourceAlbum.getTitle() +
                                        " to album " + destinationAlbum.getId();
                                addFuture = cloudOperationHelper.withBackOffAndRetry(addOperationName,
                                        () -> withInvalidMediaItemErrorIgnored(addOperationName, destinationAlbum.addMediaItems(itemsToAdd, executorService)));
                            }
                            String removeOperationName = "remove " + itemsInGroup.size() + " items for " + sourceAlbum.getTitle() +
                                    " from album " + sourceAlbum.getId();
                            return addFuture.thenCompose(aVoid -> cloudOperationHelper.withBackOffAndRetry(removeOperationName,
                                    () -> withInvalidMediaItemErrorIgnored(removeOperationName, sourceAlbum.removeMediaItems(itemsInGroup, executorService))));
                        })
                        .collect(toFutureOfList())
                        .thenApply(list -> null));
    }

    private String mediaItemsToIds(List<GoogleMediaItem> items) {
        return items.stream().map(GoogleMediaItem::getId).collect(Collectors.joining(", "));
    }

    private CompletableFuture<List<GoogleMediaItem>> getItemsInAlbum(GooglePhotosAlbum sourceAlbum) {
        return cloudOperationHelper.withBackOffAndRetry(
                "get media items in album " + sourceAlbum.getId(),
                () -> sourceAlbum.getMediaItems(executorService));
    }

    private void removeAlbum(GooglePhotosAlbum albumToBeMerged) {
        // TODO if case of ignored INVALID_MEDIA_ID failures, this album may not be empty, so the phrasing below
        //  would be incorrect
        // remove this album - CAN'T DO THIS :-(, so flagging to user
        logger.info("MANUAL ACTION NEEDED: remove empty album '{}': {}",
                albumToBeMerged.getTitle(), albumToBeMerged.getAlbumUrl());
    }

    private static CompletableFuture<Void> withInvalidMediaItemErrorIgnored(String operationName, CompletableFuture<Void> action) {
        return action
                .exceptionally(exception -> {
                    if (getCausalChain(exception).stream().anyMatch(throwable -> throwable instanceof InvalidArgumentException)) {
                        logger.warn("Ignoring INVALID_ARGUMENT failure, must be pre-existing media item that we have no access to; operation was '{}'", operationName);
                        return null;
                    } else {
                        throw new RuntimeException(exception);
                    }
                });
    }
}
