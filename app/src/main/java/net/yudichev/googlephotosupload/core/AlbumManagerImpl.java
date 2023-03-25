package net.yudichev.googlephotosupload.core;

import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.common.collect.ImmutableMap;
import net.yudichev.jiotty.common.async.AsyncOperationRetry;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.common.lang.CompletableFutures;
import net.yudichev.jiotty.connector.google.photos.GoogleMediaItem;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.LongConsumer;
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
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

final class AlbumManagerImpl extends BaseLifecycleComponent implements AlbumManager {
    private static final Logger logger = LoggerFactory.getLogger(AlbumManagerImpl.class);

    private final GooglePhotosClient googlePhotosClient;
    private final Provider<ExecutorService> executorServiceProvider;
    private final AsyncOperationRetry asyncOperationRetry;
    private final ProgressStatusFactory progressStatusFactory;
    private final ResourceBundle resourceBundle;

    private volatile ExecutorService executorService;

    @Inject
    AlbumManagerImpl(GooglePhotosClient googlePhotosClient,
                     @Backpressured Provider<ExecutorService> executorServiceProvider,
                     AsyncOperationRetry asyncOperationRetry,
                     ProgressStatusFactory progressStatusFactory,
                     ResourceBundle resourceBundle) {
        this.googlePhotosClient = checkNotNull(googlePhotosClient);
        this.executorServiceProvider = checkNotNull(executorServiceProvider);
        this.asyncOperationRetry = checkNotNull(asyncOperationRetry);
        this.progressStatusFactory = checkNotNull(progressStatusFactory);
        this.resourceBundle = checkNotNull(resourceBundle);
    }

    @Override
    protected void doStart() {
        executorService = executorServiceProvider.get();
    }

    @Override
    public CompletableFuture<Map<String, GooglePhotosAlbum>> listAlbumsByTitle(List<AlbumDirectory> albumDirectories,
                                                                               Map<String, List<GooglePhotosAlbum>> cloudAlbumsByTitle) {
        checkStarted();
        if (albumDirectories.isEmpty()) {
            return completedFuture(ImmutableMap.of());
        }
        // root directory is excluded from progress as it does not need to be reconciled
        var reconcilableAlbumCount = albumDirectories.size() - 1;
        logger.info("Reconciling {} albums(s) with Google Photos, may take a bit of time...", reconcilableAlbumCount);
        var progressStatus = progressStatusFactory.create(
                resourceBundle.getString("albumManagerProgressStatusTitle"),
                Optional.of(reconcilableAlbumCount));
        return albumDirectories.stream()
                .map(albumDirectory -> {
                    progressStatus.updateDescription(albumDirectory.path().toAbsolutePath().toString());
                    return albumDirectory.albumTitle()
                            .map(albumTitle -> reconcile(cloudAlbumsByTitle, albumTitle, albumDirectory.path(), progressStatus, progressStatus::onBackoffDelay)
                                    .whenComplete((album, e) -> progressStatus.incrementSuccess()));
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toFutureOfList())
                .<Map<String, GooglePhotosAlbum>>thenApply(googlePhotosAlbums -> googlePhotosAlbums.stream()
                        .collect(toImmutableMap(
                                GooglePhotosAlbum::getTitle,
                                Function.identity())))
                .whenComplete((ignored, e) -> progressStatus.close(e == null));
    }

    private static String mediaItemsToIds(List<GoogleMediaItem> items) {
        return items.stream().map(GoogleMediaItem::getId).collect(Collectors.joining(", "));
    }

    private CompletableFuture<GooglePhotosAlbum> mergeAlbums(GooglePhotosAlbum primaryAlbum,
                                                             List<GooglePhotosAlbum> albumsToBeMerged,
                                                             ProgressStatus progressStatus,
                                                             LongConsumer backoffEventConsumer) {
        return getItemsInAlbum(primaryAlbum, backoffEventConsumer)
                .thenCompose(itemsInPrimaryAlbum -> {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Items currently in primary album: {}", mediaItemsToIds(itemsInPrimaryAlbum));
                    }
                    return albumsToBeMerged.stream()
                            .map(albumToBeMerged -> moveItems(albumToBeMerged, primaryAlbum, itemsInPrimaryAlbum, backoffEventConsumer)
                                    .thenRun(() -> removeAlbum(albumToBeMerged, progressStatus)))
                            .collect(toFutureOfList())
                            .thenApply(list -> primaryAlbum);
                });
    }

    private void removeAlbum(GooglePhotosAlbum albumToBeMerged, ProgressStatus progressStatus) {
        // remove this album - CAN'T DO THIS :-(, so flagging to user
        progressStatus.addFailure(KeyedError.of(
                getAsUnchecked(() -> new URL(albumToBeMerged.getAlbumUrl())),
                String.format(resourceBundle.getString("albumManagerPleaseDeleteManually"), albumToBeMerged.getTitle())));
    }

    private CompletableFuture<Void> moveItems(GooglePhotosAlbum sourceAlbum,
                                              GooglePhotosAlbum destinationAlbum,
                                              List<GoogleMediaItem> itemsInDestinationAlbum,
                                              LongConsumer backoffEventConsumer) {
        var maxItemsPerRequest = 49;
        return getItemsInAlbum(sourceAlbum, backoffEventConsumer).thenCompose(itemsInSourceAlbum -> itemsInSourceAlbum.isEmpty() ?
                CompletableFutures.completedFuture() :
                IntStream.range(0, itemsInSourceAlbum.size() / maxItemsPerRequest + 1)
                        .mapToObj(groupNumber -> itemsInSourceAlbum.subList(
                                groupNumber * maxItemsPerRequest,
                                min((groupNumber + 1) * maxItemsPerRequest, itemsInSourceAlbum.size())))
                        .filter(itemsInGroup -> !itemsInGroup.isEmpty())
                        .map(itemsInGroup -> {
                            logger.debug("Moving a batch of {} items for {} from {} to {}",
                                    itemsInGroup.size(), sourceAlbum.getTitle(), sourceAlbum.getId(), destinationAlbum.getId());
                            var itemsToAdd = without(itemsInGroup, itemsInDestinationAlbum);
                            CompletableFuture<Void> addFuture;
                            if (itemsToAdd.isEmpty()) {
                                addFuture = CompletableFutures.completedFuture();
                            } else {
                                if (logger.isDebugEnabled()) {
                                    logger.debug("Add to album {} items {}", destinationAlbum.getId(), mediaItemsToIds(itemsToAdd));
                                }
                                var addOperationName = "add " + itemsToAdd.size() + " items for " + sourceAlbum.getTitle() +
                                        " to album " + destinationAlbum.getId();
                                addFuture = asyncOperationRetry.withBackOffAndRetry(addOperationName,
                                        () -> withInvalidMediaItemErrorIgnored(addOperationName, destinationAlbum.addMediaItems(itemsToAdd, executorService)),
                                        backoffEventConsumer);
                            }
                            var removeOperationName = "remove " + itemsInGroup.size() + " items for " + sourceAlbum.getTitle() +
                                    " from album " + sourceAlbum.getId();
                            return addFuture.thenCompose(aVoid -> asyncOperationRetry.withBackOffAndRetry(removeOperationName,
                                    () -> withInvalidMediaItemErrorIgnored(removeOperationName, sourceAlbum.removeMediaItems(itemsInGroup, executorService)),
                                    backoffEventConsumer));
                        })
                        .collect(toFutureOfList())
                        .thenApply(list -> null));
    }

    private static List<GoogleMediaItem> without(List<GoogleMediaItem> mediaItems, List<GoogleMediaItem> itemsToExclude) {
        List<GoogleMediaItem> result = new ArrayList<>(mediaItems);
        result.removeAll(itemsToExclude);
        return result;
    }

    private CompletableFuture<List<GoogleMediaItem>> getItemsInAlbum(GooglePhotosAlbum sourceAlbum, LongConsumer backoffEventConsumer) {
        return asyncOperationRetry.withBackOffAndRetry(
                "get media items in album " + sourceAlbum.getId(),
                () -> sourceAlbum.getMediaItems(executorService),
                backoffEventConsumer);
    }

    private CompletableFuture<GooglePhotosAlbum> reconcile(Map<String, List<GooglePhotosAlbum>> cloudAlbumsByTitle,
                                                           String filesystemAlbumTitle,
                                                           Path path,
                                                           ProgressStatus progressStatus,
                                                           LongConsumer backoffEventConsumer) {
        return Optional.ofNullable(cloudAlbumsByTitle.get(filesystemAlbumTitle))
                .flatMap(albums -> {
                    var writableAlbums = albums.stream()
                            .filter(googlePhotosAlbum -> {
                                var writeable = googlePhotosAlbum.isWriteable();
                                if (!writeable) {
                                    logger.debug("Ignoring non-writable album {}", googlePhotosAlbum);
                                }
                                return writeable;
                            }) // ignore albums we can't touch anyway
                            .collect(toImmutableList());
                    return writableAlbums.isEmpty() ? Optional.empty() : Optional.of(writableAlbums);
                })
                .map(cloudAlbumsForThisTitle -> {
                    if (cloudAlbumsForThisTitle.size() > 1) {
                        List<GooglePhotosAlbum> nonEmptyCloudAlbumsForThisTitle = cloudAlbumsForThisTitle.stream()
                                .filter(googlePhotosAlbum -> googlePhotosAlbum.getMediaItemCount() > 0)
                                .collect(toImmutableList());
                        if (nonEmptyCloudAlbumsForThisTitle.isEmpty()) {
                            cloudAlbumsForThisTitle.stream()
                                    .skip(1)
                                    .forEach(albumToBeMerged -> removeAlbum(albumToBeMerged, progressStatus));
                            return completedFuture(cloudAlbumsForThisTitle.get(0));
                        } else if (nonEmptyCloudAlbumsForThisTitle.size() > 1) {
                            var primaryAlbum = cloudAlbumsForThisTitle.get(0);
                            logger.info("Merging {} duplicate cloud album(s) for path [{}] into {}", cloudAlbumsForThisTitle.size(), path, primaryAlbum);
                            return mergeAlbums(
                                    primaryAlbum,
                                    cloudAlbumsForThisTitle.subList(1, cloudAlbumsForThisTitle.size()),
                                    progressStatus,
                                    backoffEventConsumer);
                        } else {
                            return completedFuture(nonEmptyCloudAlbumsForThisTitle.get(0));
                        }
                    } else {
                        return completedFuture(cloudAlbumsForThisTitle.get(0));
                    }
                })
                .orElseGet(() -> {
                    logger.info("Creating album [{}] for path [{}]", filesystemAlbumTitle, path);
                    return asyncOperationRetry.withBackOffAndRetry(
                            "create album " + filesystemAlbumTitle,
                            () -> googlePhotosClient.createAlbum(filesystemAlbumTitle, executorService),
                            backoffEventConsumer);
                });
    }

    private static CompletableFuture<Void> withInvalidMediaItemErrorIgnored(String operationName, CompletableFuture<Void> action) {
        return action
                .exceptionally(exception -> {
                    if (getCausalChain(exception).stream().anyMatch(throwable -> throwable instanceof InvalidArgumentException)) {
                        logger.warn("Ignoring INVALID_ARGUMENT failure, must be pre-existing media item that we have no access to; operation was '{}'",
                                operationName);
                        return null;
                    } else {
                        throw new RuntimeException(exception);
                    }
                });
    }
}
