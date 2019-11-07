package net.yudichev.googlephotosupload.app;

import net.yudichev.jiotty.common.app.ApplicationLifecycleControl;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.jiotty.common.lang.CompletableFutures.logErrorOnFailure;
import static net.yudichev.jiotty.common.lang.CompletableFutures.toFutureOfList;

final class UploadPhotos extends BaseLifecycleComponent {
    private static final Logger logger = LoggerFactory.getLogger(UploadPhotos.class);
    private final FilesystemManager filesystemManager;
    private final GooglePhotosUploader uploader;
    private final ApplicationLifecycleControl applicationLifecycleControl;
    private final DirectoryStructureSupplier directoryStructureSupplier;
    private final AlbumManager albumManager;
    private final CloudAlbumsProvider cloudAlbumsProvider;

    @Inject
    UploadPhotos(FilesystemManager filesystemManager,
                 GooglePhotosUploader uploader,
                 ApplicationLifecycleControl applicationLifecycleControl,
                 DirectoryStructureSupplier directoryStructureSupplier,
                 AlbumManager albumManager,
                 CloudAlbumsProvider cloudAlbumsProvider) {
        this.filesystemManager = checkNotNull(filesystemManager);
        this.uploader = checkNotNull(uploader);
        this.applicationLifecycleControl = checkNotNull(applicationLifecycleControl);
        this.directoryStructureSupplier = checkNotNull(directoryStructureSupplier);
        this.albumManager = checkNotNull(albumManager);
        this.cloudAlbumsProvider = checkNotNull(cloudAlbumsProvider);
    }

    @Override
    protected void doStart() {
        CompletableFuture<List<AlbumDirectory>> albumDirectoriesFuture = directoryStructureSupplier.listAlbumDirectories();
        CompletableFuture<Map<String, List<GooglePhotosAlbum>>> cloudAlbumsByTitleFuture = cloudAlbumsProvider.listCloudAlbums();
        albumDirectoriesFuture
                .thenCompose(albumDirectories -> cloudAlbumsByTitleFuture
                        .thenCompose(cloudAlbumsByTitle -> albumManager.listAlbumsByTitle(albumDirectories, cloudAlbumsByTitle)
                                .thenCompose(albumsByTitle -> albumDirectories.stream()
                                        .flatMap(albumDirectory -> filesystemManager.listFiles(albumDirectory.path()).stream()
                                                .map(path -> uploader.uploadFile(albumDirectory.albumTitle().map(albumsByTitle::get), path)))
                                        .collect(toFutureOfList())
                                        .thenAccept(list -> logger.info("All done without errors, files uploaded: {}", list.size()))))
                        .whenComplete(logErrorOnFailure(logger, "Failed"))
                        .whenComplete((ignored1, ignored2) -> applicationLifecycleControl.initiateShutdown()));
    }
}
