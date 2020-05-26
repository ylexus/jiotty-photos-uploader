package net.yudichev.googlephotosupload.core;

import com.google.common.collect.ImmutableList;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.common.lang.CompletableFutures;
import net.yudichev.jiotty.common.lang.ResultOrFailure;
import net.yudichev.jiotty.common.time.CurrentDateTimeProvider;
import net.yudichev.jiotty.connector.google.photos.GoogleMediaItem;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosClient;
import net.yudichev.jiotty.connector.google.photos.NewMediaItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.Lists.partition;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.util.Comparator.comparing;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.stream.Collectors.toConcurrentMap;
import static net.yudichev.googlephotosupload.core.Bindings.Backpressured;
import static net.yudichev.jiotty.common.lang.CompletableFutures.toFutureOfList;
import static net.yudichev.jiotty.common.lang.CompletableFutures.toFutureOfListChaining;
import static net.yudichev.jiotty.common.lang.HumanReadableExceptionMessage.humanReadableMessage;
import static net.yudichev.jiotty.common.lang.Locks.inLock;
import static net.yudichev.jiotty.common.lang.ResultOrFailure.failure;
import static net.yudichev.jiotty.common.lang.ResultOrFailure.success;

final class GooglePhotosUploaderImpl extends BaseLifecycleComponent implements GooglePhotosUploader {
    private static final int GOOGLE_PHOTOS_API_BATCH_SIZE = 50;

    private static final Logger logger = LoggerFactory.getLogger(GooglePhotosUploaderImpl.class);

    private final GooglePhotosClient googlePhotosClient;
    private final StateSaverFactory stateSaverFactory;
    private final UploadStateManager uploadStateManager;
    private final CurrentDateTimeProvider currentDateTimeProvider;
    private final CloudOperationHelper cloudOperationHelper;

    private final FilesystemManager filesystemManager;
    private final Provider<ExecutorService> executorServiceProvider;
    private final BackingOffRemoteApiExceptionHandler backOffHandler;
    private final FatalUserCorrectableRemoteApiExceptionHandler fatalUserCorrectableHandler;
    private final Lock stateLock = new ReentrantLock();

    private StateSaver stateSaver;
    private ExecutorService executorService;
    private Map<Path, CompletableFuture<ItemState>> uploadedItemStateByPath;
    private UploadState uploadState;

    @Inject
    GooglePhotosUploaderImpl(GooglePhotosClient googlePhotosClient,
                             FilesystemManager filesystemManager,
                             @Backpressured Provider<ExecutorService> executorServiceProvider,
                             BackingOffRemoteApiExceptionHandler backOffHandler,
                             FatalUserCorrectableRemoteApiExceptionHandler fatalUserCorrectableHandler,
                             StateSaverFactory stateSaverFactory,
                             UploadStateManager uploadStateManager,
                             CurrentDateTimeProvider currentDateTimeProvider,
                             CloudOperationHelper cloudOperationHelper) {
        this.filesystemManager = checkNotNull(filesystemManager);
        this.executorServiceProvider = checkNotNull(executorServiceProvider);
        this.backOffHandler = checkNotNull(backOffHandler);
        this.fatalUserCorrectableHandler = checkNotNull(fatalUserCorrectableHandler);
        this.googlePhotosClient = checkNotNull(googlePhotosClient);
        this.stateSaverFactory = checkNotNull(stateSaverFactory);
        this.uploadStateManager = checkNotNull(uploadStateManager);
        this.currentDateTimeProvider = checkNotNull(currentDateTimeProvider);
        this.cloudOperationHelper = checkNotNull(cloudOperationHelper);
    }

    @Override
    public CompletableFuture<Void> uploadDirectory(Path albumDirectoryPath, Optional<GooglePhotosAlbum> googlePhotosAlbum, ProgressStatus fileProgressStatus) {
        checkStarted();

        return supplyAsync(() -> filesystemManager.listFiles(albumDirectoryPath))
                .thenCompose(paths -> paths.stream()
                        .map(path -> createMediaData(path)
                                .thenApply(itemState -> {
                                    itemState.toFailure().ifPresentOrElse(
                                            error -> fileProgressStatus.addFailure(KeyedError.of(path, error)),
                                            fileProgressStatus::incrementSuccess);
                                    return PathState.of(path, itemState);
                                }))
                        .collect(toFutureOfList())
                        .thenCompose(createMediaDataResults -> partition(createMediaDataResults, GOOGLE_PHOTOS_API_BATCH_SIZE).stream()
                                .collect(toFutureOfListChaining(pathStates -> createMediaItems(fileProgressStatus, pathStates)))
                                .thenApply(lists -> lists.stream().flatMap(Collection::stream)))
                        .thenCompose(pathMediaItemOrErrorStream -> addToAlbum(googlePhotosAlbum, pathMediaItemOrErrorStream, fileProgressStatus)));
    }

    @Override
    public void doNotResume() {
        inLock(stateLock, () -> {
            logger.info("Requested not to resume, forgetting {} previously uploaded item(s)", uploadedItemStateByPath.size());
            uploadState = UploadState.builder().build();
            uploadStateManager.save(uploadState);
            uploadedItemStateByPath.clear();
        });
    }

    @Override
    public int canResume() {
        return (int) uploadStateManager.get().uploadedMediaItemIdByAbsolutePath().values().stream()
                .filter(itemState -> itemState.mediaId().isPresent())
                .count();
    }

    @Override
    protected void doStart() {
        inLock(stateLock, () -> {
            executorService = executorServiceProvider.get();
            stateSaver = stateSaverFactory.create("uploaded-items", this::saveState);
            uploadState = uploadStateManager.get();
            uploadedItemStateByPath = uploadState.uploadedMediaItemIdByAbsolutePath().entrySet().stream()
                    .collect(toConcurrentMap(
                            entry -> Paths.get(entry.getKey()),
                            entry -> completedFuture(entry.getValue())));
        });
    }

    @Override
    protected void doStop() {
        inLock(stateLock, () -> stateSaver.close());
    }

    /**
     * @return list of items that were successfully created, which may be less in size than the specified
     * {@code createMediaDataResults}
     */
    private CompletableFuture<List<PathMediaItemOrError>> createMediaItems(ProgressStatus fileProgressStatus,
                                                                           List<PathState> createMediaDataResults) {
        List<PathState> pendingPathStates = createMediaDataResults.stream()
                .filter(pathState -> {
                    var itemStateOptional = pathState.state().toSuccess();
                    return itemStateOptional.isPresent() &&
                            itemStateOptional.get().mediaId().isEmpty();
                })
                .collect(toImmutableList());
        if (pendingPathStates.isEmpty()) {
            return completedFuture(ImmutableList.of());
        }

        List<NewMediaItem> pendingNewMediaItems = pendingPathStates.stream()
                .map(pathState -> NewMediaItem.of(pathState.state().toSuccess().get().uploadState().get().token(),
                        Optional.of(pathState.path().getFileName().toString())))
                .collect(toImmutableList());
        return cloudOperationHelper.withBackOffAndRetry(
                "create media items",
                () -> googlePhotosClient.createMediaItems(Optional.empty(), pendingNewMediaItems, executorService),
                fileProgressStatus::onBackoffDelay)
                .<List<PathMediaItemOrError>>thenApply(mediaItemOrErrors -> {
                    ImmutableList.Builder<PathMediaItemOrError> resultListBuilder = ImmutableList.builder();
                    for (var i = 0; i < pendingPathStates.size(); i++) {
                        var pathState = pendingPathStates.get(i);
                        var mediaItemOrError = mediaItemOrErrors.get(i);
                        mediaItemOrError.errorStatus().ifPresent(status -> fileProgressStatus.addFailure(
                                KeyedError.of(pathState.path(), status.getCode() + ": " + status.getMessage())));
                        mediaItemOrError.item().ifPresent(item -> {
                            uploadedItemStateByPath.compute(pathState.path(),
                                    (path, itemStateFuture) -> checkNotNull(itemStateFuture).thenApply(itemState -> itemState.withMediaId(item.getId())));
                            resultListBuilder.add(PathMediaItemOrError.of(pathState.path(), item));
                        });
                    }
                    return resultListBuilder.build();
                })
                .exceptionally(throwable -> {
                    if (fatalUserCorrectableHandler.handle("create media items", throwable)) {
                        pendingPathStates.stream()
                                .map(PathState::path)
                                .map(path -> KeyedError.of(path, humanReadableMessage(throwable)))
                                .forEach(fileProgressStatus::addFailure);
                        return ImmutableList.of();
                    } else {
                        throw new RuntimeException(throwable);
                    }
                });
    }

    private CompletableFuture<ResultOrFailure<ItemState>> createMediaData(Path file) {
        checkStarted();
        return inLock(stateLock, () -> uploadedItemStateByPath.compute(file,
                (theFile, currentFuture) -> {
                    if (currentFuture == null || currentFuture.isCompletedExceptionally()) {
                        logger.info("Scheduling upload of {}", file);
                        currentFuture = doCreateMediaData(theFile);
                    } else {
                        var itemState = currentFuture.getNow(null);
                        if (itemState != null) {
                            currentFuture = itemState.uploadState()
                                    .filter(uploadMediaItemState -> {
                                        if (itemState.mediaId().isPresent()) {
                                            return true;
                                        }
                                        return uploadTokenNotExpired(file, uploadMediaItemState);
                                    })
                                    .map(uploadMediaItemState -> {
                                        logger.info("Already uploaded, skipping: {}", file);
                                        return completedFuture(itemState);
                                    })
                                    .orElseGet(() -> {
                                        logger.info("Uploaded, but upload token expired, re-uploading: {}", file);
                                        return doCreateMediaData(theFile);
                                    });
                        } else {
                            logger.error("Unexpected future state for {}: {}", file, currentFuture);
                        }
                    }
                    return currentFuture;
                })
                .thenApply(itemState -> {
                    stateSaver.save();
                    backOffHandler.reset();
                    return success(itemState);
                })
                .exceptionallyCompose(exception -> {
                    var operationName = "uploading file " + file;
                    if (fatalUserCorrectableHandler.handle(operationName, exception)) {
                        return completedFuture(failure(humanReadableMessage(exception)));
                    } else if (backOffHandler.handle(operationName, exception).isPresent()) {
                        logger.debug("Retrying upload of {}", file);
                        return createMediaData(file);
                    } else {
                        throw new RuntimeException(exception);
                    }
                }));
    }

    private CompletionStage<Void> addToAlbum(Optional<GooglePhotosAlbum> googlePhotosAlbum,
                                             Stream<PathMediaItemOrError> pathMediaItemOrErrorStream,
                                             ProgressStatus fileProgressStatus) {
        return googlePhotosAlbum
                .map(album -> {
                    var pathMediaItemOrErrors = pathMediaItemOrErrorStream
                            .filter(pathMediaItemOrError -> {
                                var itemState = uploadedItemStateByPath.get(pathMediaItemOrError.path()).getNow(null);
                                checkState(itemState != null, "item state future must be completed by the time addToAlbum() is called");
                                return itemState.albumId().isEmpty();
                            })
                            .collect(toImmutableList());
                    var mediaItemsToAddToAlbum = pathMediaItemOrErrors.stream()
                            .map(PathMediaItemOrError::mediaItem)
                            .sorted(comparing(GoogleMediaItem::getCreationTime))
                            .collect(toImmutableList());
                    return cloudOperationHelper.withBackOffAndRetry("add items to album",
                            () -> partition(mediaItemsToAddToAlbum, GOOGLE_PHOTOS_API_BATCH_SIZE).stream()
                                    .collect(toFutureOfListChaining(mediaItems -> album.addMediaItems(mediaItems, executorService)))
                                    .<Void>thenApply(ignored -> null),
                            fileProgressStatus::onBackoffDelay)
                            .exceptionallyCompose(exception -> {
                                var operationName = "adding items to album " + album.getTitle();
                                if (fatalUserCorrectableHandler.handle(operationName, exception)) {
                                    pathMediaItemOrErrors.stream()
                                            .map(PathMediaItemOrError::path)
                                            .forEach(path -> fileProgressStatus.addFailure(KeyedError.of(path, humanReadableMessage(exception))));
                                    return CompletableFutures.completedFuture();
                                } else {
                                    throw new RuntimeException(exception);
                                }
                            });
                })
                .orElseGet(CompletableFutures::completedFuture);
    }

    private boolean uploadTokenNotExpired(Path file, UploadMediaItemState uploadMediaItemState) {
        var expiry = uploadMediaItemState.uploadInstant().plus(23, HOURS);
        var notExpired = expiry.isAfter(currentDateTimeProvider.currentInstant());
        if (!notExpired) {
            logger.debug("upload token for {} expired, forgetting: {}", file, uploadMediaItemState);
        }
        return notExpired;
    }

    private CompletableFuture<ItemState> doCreateMediaData(Path file) {
        return googlePhotosClient.uploadMediaData(file, executorService)
                .thenApply(uploadToken -> {
                    logger.info("Uploaded file {}, upload token {}", file, uploadToken);
                    return ItemState.builder()
                            .setUploadState(UploadMediaItemState.of(uploadToken, currentDateTimeProvider.currentInstant()))
                            .build();
                });
    }

    private void saveState() {
        var newUploadState = UploadState.of(
                uploadedItemStateByPath.entrySet().stream()
                        .filter(entry -> entry.getValue().isDone() && !entry.getValue().isCompletedExceptionally())
                        .collect(toImmutableMap(
                                entry -> entry.getKey().toString(),
                                entry -> entry.getValue().getNow(null))));
        if (!newUploadState.equals(uploadState)) {
            uploadState = newUploadState;
            uploadStateManager.save(uploadState);
        }
    }
}