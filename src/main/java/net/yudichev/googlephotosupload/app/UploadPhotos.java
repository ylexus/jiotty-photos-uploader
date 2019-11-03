package net.yudichev.googlephotosupload.app;

import net.yudichev.jiotty.common.app.ApplicationLifecycleControl;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

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

    @Inject
    UploadPhotos(FilesystemManager filesystemManager,
                 GooglePhotosUploader uploader,
                 ApplicationLifecycleControl applicationLifecycleControl,
                 DirectoryStructureSupplier directoryStructureSupplier,
                 AlbumManager albumManager) {
        this.filesystemManager = checkNotNull(filesystemManager);
        this.uploader = checkNotNull(uploader);
        this.applicationLifecycleControl = checkNotNull(applicationLifecycleControl);
        this.directoryStructureSupplier = checkNotNull(directoryStructureSupplier);
        this.albumManager = checkNotNull(albumManager);
    }

    @Override
    protected void doStart() {
        logger.info("Starting to upload files");
        directoryStructureSupplier.getAlbumDirectories().stream()
                .flatMap(albumDirectory -> filesystemManager.listFiles(albumDirectory.path()).stream()
                        .map(path -> uploader.uploadFile(albumDirectory.albumTitle().map(albumManager::albumForTitle), path)))
                .collect(toFutureOfList())
                .thenAccept(list -> logger.info("All done without errors, files uploaded: {}", list.size()))
                .whenComplete(logErrorOnFailure(logger, "Failed to upload some file(s)"))
                .whenComplete((ignored1, ignored2) -> applicationLifecycleControl.initiateShutdown());
    }
}
