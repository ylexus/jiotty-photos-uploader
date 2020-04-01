package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toConcurrentMap;
import static net.yudichev.googlephotosupload.core.Bindings.Backpressured;
import static net.yudichev.googlephotosupload.core.ResultOrFailure.failure;
import static net.yudichev.googlephotosupload.core.ResultOrFailure.success;
import static net.yudichev.jiotty.common.lang.HumanReadableExceptionMessage.humanReadableMessage;
import static net.yudichev.jiotty.common.lang.Locks.inLock;

final class GooglePhotosUploaderImpl extends BaseLifecycleComponent implements GooglePhotosUploader {
    private static final Logger logger = LoggerFactory.getLogger(GooglePhotosUploaderImpl.class);

    private final GooglePhotosClient googlePhotosClient;
    private final StateSaverFactory stateSaverFactory;
    private final UploadStateManager uploadStateManager;

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
                             @Backpressured Provider<ExecutorService> executorServiceProvider,
                             BackingOffRemoteApiExceptionHandler backOffHandler,
                             FatalUserCorrectableRemoteApiExceptionHandler fatalUserCorrectableHandler,
                             StateSaverFactory stateSaverFactory,
                             UploadStateManager uploadStateManager) {
        this.executorServiceProvider = checkNotNull(executorServiceProvider);
        this.backOffHandler = checkNotNull(backOffHandler);
        this.fatalUserCorrectableHandler = checkNotNull(fatalUserCorrectableHandler);
        this.googlePhotosClient = checkNotNull(googlePhotosClient);
        this.stateSaverFactory = checkNotNull(stateSaverFactory);
        this.uploadStateManager = checkNotNull(uploadStateManager);
    }

    @Override
    public CompletableFuture<ResultOrFailure<Object>> uploadFile(Optional<GooglePhotosAlbum> album, Path file) {
        checkStarted();
        return inLock(stateLock, () -> uploadedItemStateByPath.compute(file,
                (theFile, currentFuture) -> {
                    if (currentFuture == null || currentFuture.isCompletedExceptionally()) {
                        logger.info("Scheduling upload of {}", file);
                        currentFuture = doUpload(album, theFile);
                    } else {
                        if (currentFuture.isDone()) {
                            logger.info("Already uploaded, skipping: {}", file);
                        } else {
                            logger.error("Unexpected future state for {}: {}", file, currentFuture);
                        }
                    }
                    return currentFuture;
                })
                .thenApply(itemState -> {
                    stateSaver.save();
                    backOffHandler.reset();
                    return success();
                })
                .exceptionallyCompose(exception -> {
                    String operationName = "uploading file " + file;
                    if (fatalUserCorrectableHandler.handle(operationName, exception)) {
                        return completedFuture(failure(humanReadableMessage(exception)));
                    } else if (backOffHandler.handle(operationName, exception).isPresent()) {
                        logger.debug("Retrying upload of {}", file);
                        return uploadFile(album, file);
                    } else {
                        throw new RuntimeException(exception);
                    }
                }));
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
        return uploadStateManager.get().uploadedMediaItemIdByAbsolutePath().size();
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

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private CompletableFuture<ItemState> doUpload(Optional<GooglePhotosAlbum> album, Path file) {
        Optional<String> albumId = album.map(GooglePhotosAlbum::getId);
        return googlePhotosClient.uploadMediaItem(albumId, file, executorService)
                .thenApply(googleMediaItem -> {
                    logger.info("Uploaded file {} as media item {} and album {}", file, googleMediaItem.getId(), album.map(GooglePhotosAlbum::getTitle));
                    return ItemState.builder()
                            .setMediaId(googleMediaItem.getId())
                            .setAlbumId(albumId)
                            .build();
                });
    }

    private void saveState() {
        UploadState newUploadState = UploadState.of(
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