package net.yudichev.googlephotosupload.app;

import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.app.Bindings.Backpressured;

final class CloudAlbumsProviderImpl implements CloudAlbumsProvider {
    private static final Logger logger = LoggerFactory.getLogger(CloudAlbumsProviderImpl.class);
    private final CloudOperationHelper cloudOperationHelper;
    private final GooglePhotosClient googlePhotosClient;
    private final ExecutorService executorService;

    @Inject
    CloudAlbumsProviderImpl(CloudOperationHelper cloudOperationHelper,
                            GooglePhotosClient googlePhotosClient,
                            @Backpressured ExecutorService executorService) {
        this.cloudOperationHelper = checkNotNull(cloudOperationHelper);
        this.googlePhotosClient = checkNotNull(googlePhotosClient);
        this.executorService = checkNotNull(executorService);
    }

    @Override
    public CompletableFuture<Map<String, List<GooglePhotosAlbum>>> listCloudAlbums() {
        // TODO emit (UI?) progress here
        logger.info("Loading albums in cloud (may take several minutes)...");
        return cloudOperationHelper.withBackOffAndRetry("get all albums", () -> googlePhotosClient.listAlbums(executorService))
                .thenApply(albumsInCloud -> {
                    logger.info("... loaded {} album(s) in cloud", albumsInCloud.size());
                    Map<String, List<GooglePhotosAlbum>> cloudAlbumsByTitle = new HashMap<>(albumsInCloud.size());
                    albumsInCloud.forEach(googlePhotosAlbum ->
                            cloudAlbumsByTitle.computeIfAbsent(googlePhotosAlbum.getTitle(), title -> new ArrayList<>()).add(googlePhotosAlbum));
                    return cloudAlbumsByTitle;
                });
    }
}
