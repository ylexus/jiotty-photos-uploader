package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.core.Bindings.Backpressured;

final class CloudAlbumsProviderImpl implements CloudAlbumsProvider {
    private static final Logger logger = LoggerFactory.getLogger(CloudAlbumsProviderImpl.class);
    private final CloudOperationHelper cloudOperationHelper;
    private final GooglePhotosClient googlePhotosClient;
    private final ExecutorService executorService;
    private final ProgressStatusFactory progressStatusFactory;

    @Inject
    CloudAlbumsProviderImpl(CloudOperationHelper cloudOperationHelper,
                            GooglePhotosClient googlePhotosClient,
                            @Backpressured ExecutorService executorService,
                            ProgressStatusFactory progressStatusFactory) {
        this.cloudOperationHelper = checkNotNull(cloudOperationHelper);
        this.googlePhotosClient = checkNotNull(googlePhotosClient);
        this.executorService = checkNotNull(executorService);
        this.progressStatusFactory = checkNotNull(progressStatusFactory);
    }

    @Override
    public CompletableFuture<Map<String, List<GooglePhotosAlbum>>> listCloudAlbums() {
        logger.info("Loading albums in cloud (may take several minutes)...");
        ProgressStatus progressStatus = progressStatusFactory.create("Loading albums in cloud", Optional.empty());
        return cloudOperationHelper.withBackOffAndRetry("get all albums",
                () -> googlePhotosClient.listAlbums(progressStatus::update, executorService))
                .thenApply(albumsInCloud -> {
                    logger.info("... loaded {} album(s) in cloud", albumsInCloud.size());
                    Map<String, List<GooglePhotosAlbum>> cloudAlbumsByTitle = new HashMap<>(albumsInCloud.size());
                    albumsInCloud.forEach(googlePhotosAlbum ->
                            cloudAlbumsByTitle.computeIfAbsent(googlePhotosAlbum.getTitle(), title -> new ArrayList<>()).add(googlePhotosAlbum));
                    return cloudAlbumsByTitle;
                })
                .whenComplete((ignored, e) -> progressStatus.close());
    }
}
