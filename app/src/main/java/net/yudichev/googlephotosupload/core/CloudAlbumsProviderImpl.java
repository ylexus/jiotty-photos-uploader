package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.async.AsyncOperationRetry;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static net.yudichev.googlephotosupload.core.Bindings.Backpressured;

final class CloudAlbumsProviderImpl extends BaseLifecycleComponent implements CloudAlbumsProvider {
    private static final Logger logger = LoggerFactory.getLogger(CloudAlbumsProviderImpl.class);
    private final AsyncOperationRetry asyncOperationRetry;
    private final GooglePhotosClient googlePhotosClient;
    private final Provider<ExecutorService> executorServiceProvider;
    private final ProgressStatusFactory progressStatusFactory;
    private final ResourceBundle resourceBundle;

    private volatile ExecutorService executorService;

    @Inject
    CloudAlbumsProviderImpl(AsyncOperationRetry asyncOperationRetry,
                            GooglePhotosClient googlePhotosClient,
                            @SuppressWarnings("BoundedWildcard") @Backpressured Provider<ExecutorService> executorServiceProvider,
                            ProgressStatusFactory progressStatusFactory,
                            ResourceBundle resourceBundle) {
        this.asyncOperationRetry = checkNotNull(asyncOperationRetry);
        this.googlePhotosClient = checkNotNull(googlePhotosClient);
        this.executorServiceProvider = executorServiceProvider;
        this.progressStatusFactory = checkNotNull(progressStatusFactory);
        this.resourceBundle = checkNotNull(resourceBundle);
    }

    @Override
    public CompletableFuture<Map<String, List<GooglePhotosAlbum>>> listCloudAlbums() {
        checkStarted();
        logger.info("Loading albums in cloud (may take several minutes)...");
        var progressStatus = progressStatusFactory.create(resourceBundle.getString("cloudAlbumsProviderProgressTitle"), Optional.empty());
        var result = asyncOperationRetry.withBackOffAndRetry(
                        "get all albums",
                        () -> googlePhotosClient.listAlbums(progressStatus::updateSuccess, executorService),
                        progressStatus::onBackoffDelay)
                .<Map<String, List<GooglePhotosAlbum>>>thenApply(albumsInCloud -> {
                    logger.info("... loaded {} album(s) in cloud", albumsInCloud.size());
                    return albumsInCloud.stream()
                            .collect(groupingBy(GooglePhotosAlbum::getTitle,
                                    () -> new HashMap<>(albumsInCloud.size()),
                                    toList()));
                });
        result.whenComplete((ignored, e) -> progressStatus.close(e == null));
        return result;
    }

    @Override
    protected void doStart() {
        executorService = executorServiceProvider.get();
    }
}
