package net.yudichev.googlephotosupload.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.jiotty.common.lang.CompletableFutures.toFutureOfList;

final class UploaderImpl implements Uploader {
    private static final Logger logger = LoggerFactory.getLogger(UploaderImpl.class);
    private final GooglePhotosUploader googlePhotosUploader;
    private final DirectoryStructureSupplier directoryStructureSupplier;
    private final AlbumManager albumManager;
    private final CloudAlbumsProvider cloudAlbumsProvider;
    private final ProgressStatusFactory progressStatusFactory;
    private final UploadStateManager uploadStateManager;
    private final ResourceBundle resourceBundle;
    private final DriveSpaceTracker driveSpaceTracker;

    @Inject
    UploaderImpl(GooglePhotosUploader googlePhotosUploader,
                 DirectoryStructureSupplier directoryStructureSupplier,
                 AlbumManager albumManager,
                 CloudAlbumsProvider cloudAlbumsProvider,
                 ProgressStatusFactory progressStatusFactory,
                 UploadStateManager uploadStateManager,
                 ResourceBundle resourceBundle,
                 DriveSpaceTracker driveSpaceTracker) {
        this.googlePhotosUploader = checkNotNull(googlePhotosUploader);
        this.directoryStructureSupplier = checkNotNull(directoryStructureSupplier);
        this.albumManager = checkNotNull(albumManager);
        this.cloudAlbumsProvider = checkNotNull(cloudAlbumsProvider);
        this.progressStatusFactory = checkNotNull(progressStatusFactory);
        this.uploadStateManager = checkNotNull(uploadStateManager);
        this.resourceBundle = checkNotNull(resourceBundle);
        this.driveSpaceTracker = checkNotNull(driveSpaceTracker);
    }

    @Override
    public CompletableFuture<Void> upload(List<Path> rootDirs, boolean resume) {
        if (!resume) {
            googlePhotosUploader.doNotResume();
        }
        return driveSpaceTracker.reset()
                .thenCompose(ignored -> {
                    var albumDirectoriesFuture = directoryStructureSupplier.listAlbumDirectories(rootDirs);
                    var cloudAlbumsByTitleFuture = cloudAlbumsProvider.listCloudAlbums();
                    return albumDirectoriesFuture
                            .thenCompose(albumDirectories -> cloudAlbumsByTitleFuture
                                    .thenCompose(cloudAlbumsByTitle -> albumManager.listAlbumsByTitle(albumDirectories, cloudAlbumsByTitle)
                                            .thenCompose(albumsByTitle -> {
                                                var fileProgressStatus = progressStatusFactory.create(
                                                        resourceBundle.getString("uploaderFileProgressTitle"),
                                                        Optional.of(albumDirectories.stream().mapToInt(albumDirectory -> albumDirectory.files().size()).sum()));
                                                var directoryProgressStatus =
                                                        progressStatusFactory.create(
                                                                resourceBundle.getString("uploaderAlbumProgressTitle"),
                                                                Optional.of(albumDirectories.size()));
                                                try {
                                                    return albumDirectories.stream()
                                                            .map(albumDirectory -> googlePhotosUploader
                                                                    .uploadDirectory(
                                                                            albumDirectory.albumTitle().map(albumsByTitle::get),
                                                                            albumDirectory.files(),
                                                                            directoryProgressStatus,
                                                                            fileProgressStatus)
                                                                    .thenRun(directoryProgressStatus::incrementSuccess))
                                                            .collect(toFutureOfList())
                                                            .whenComplete((ignored2, e) -> {
                                                                directoryProgressStatus.close(e == null);
                                                                fileProgressStatus.close(e == null);
                                                            })
                                                            .thenRun(() -> logger.info("All done without fatal errors"));
                                                } catch (RuntimeException e) {
                                                    directoryProgressStatus.closeUnsuccessfully();
                                                    fileProgressStatus.closeUnsuccessfully();
                                                    throw e;
                                                }
                                            })));
                });
    }

    @Override
    public int numberOfUploadedItems() {
        return uploadStateManager.itemCount();
    }

    @Override
    public void forgetUploadState() {
        googlePhotosUploader.forgetUploadStateOnShutdown();
    }
}
