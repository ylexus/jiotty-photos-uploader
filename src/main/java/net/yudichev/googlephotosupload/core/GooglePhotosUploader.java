package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.inject.LifecycleComponent;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

interface GooglePhotosUploader extends LifecycleComponent {
    CompletableFuture<Void> uploadDirectory(Optional<GooglePhotosAlbum> googlePhotosAlbum,
                                            List<Path> files,
                                            ProgressStatus fileProgressStatus);

    void doNotResume();

    void forgetUploadStateOnShutdown();
}
