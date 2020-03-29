package net.yudichev.googlephotosupload.core;

import com.google.inject.BindingAnnotation;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
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
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.stream.Collectors.toConcurrentMap;
import static net.yudichev.googlephotosupload.core.Bindings.Backpressured;
import static net.yudichev.jiotty.common.lang.CompletableFutures.completedFuture;
import static net.yudichev.jiotty.common.lang.Locks.inLock;

final class GooglePhotosUploaderImpl extends BaseLifecycleComponent implements GooglePhotosUploader {
    private static final Logger logger = LoggerFactory.getLogger(GooglePhotosUploaderImpl.class);

    private final GooglePhotosClient googlePhotosClient;
    private final StateSaverFactory stateSaverFactory;
    private final UploadStateManager uploadStateManager;

    private final Provider<ExecutorService> executorServiceProvider;
    private final BackingOffRemoteApiExceptionHandler backOffHandler;
    private final InvalidMediaItemRemoteApiExceptionHandler invalidMediaItemHandler;
    private final Lock stateLock = new ReentrantLock();

    private StateSaver stateSaver;
    private ExecutorService executorService;
    private Map<Path, CompletableFuture<ItemState>> uploadedItemStateByPath;
    private UploadState uploadState;

    @Inject
    GooglePhotosUploaderImpl(GooglePhotosClient googlePhotosClient,
                             @Backpressured Provider<ExecutorService> executorServiceProvider,
                             BackingOffRemoteApiExceptionHandler backOffHandler,
                             InvalidMediaItemRemoteApiExceptionHandler invalidMediaItemHandler,
                             StateSaverFactory stateSaverFactory,
                             UploadStateManager uploadStateManager) {
        this.executorServiceProvider = checkNotNull(executorServiceProvider);
        this.backOffHandler = checkNotNull(backOffHandler);
        this.invalidMediaItemHandler = checkNotNull(invalidMediaItemHandler);
        this.googlePhotosClient = checkNotNull(googlePhotosClient);
        this.stateSaverFactory = checkNotNull(stateSaverFactory);
        this.uploadStateManager = checkNotNull(uploadStateManager);
    }

    @Override
    public CompletableFuture<Void> uploadFile(Optional<GooglePhotosAlbum> album, Path file) {
        checkStarted();
        return inLock(stateLock, () -> uploadedItemStateByPath.compute(file,
                (theFile, currentFuture) -> {
                    if (currentFuture == null || currentFuture.isCompletedExceptionally()) {
                        logger.info("Scheduling upload of {}", file);
                        currentFuture = doUpload(album, theFile);
                    } else {
                        if (currentFuture.isDone()) {
                            ItemState currentItemState = currentFuture.getNow(null);
                            if (currentItemState.mediaId().isEmpty()) {
                                logger.info("Permanently failed before, skipping: {}", file);
                            } else {
                                logger.info("Already uploaded, skipping: {}", file);
                            }
                        } else {
                            logger.error("Unexpected future state for {}: {}", file, currentFuture);
                        }
                    }
                    return currentFuture;
                })
                .thenApply(itemState -> {
                    stateSaver.save();
                    backOffHandler.reset();
                    return false;
                })
                .exceptionally(exception -> {
                    String operationName = "uploading file " + file;
                    boolean shouldRetry = backOffHandler.handle(operationName, exception) > 0;
                    boolean invalidMediaItem = invalidMediaItemHandler.handle(operationName, exception);
                    if (invalidMediaItem) {
                        uploadedItemStateByPath.computeIfPresent(file, (path, existingStateFuture) ->
                                CompletableFuture.completedFuture(ItemState.builder().build()));
                    }
                    if (!shouldRetry && !invalidMediaItem) {
                        throw new RuntimeException(exception);
                    }
                    return shouldRetry;
                })
                .thenCompose(shouldRetry -> {
                    if (shouldRetry) {
                        logger.debug("Retrying upload of {}", file);
                        return uploadFile(album, file);
                    }
                    return completedFuture();
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
                            entry -> CompletableFuture.completedFuture(entry.getValue())));
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

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface InvalidMediaItem {
    }
}
