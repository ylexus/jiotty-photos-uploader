package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.inject.LifecycleComponent;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

interface GooglePhotosUploader extends LifecycleComponent {
    CompletableFuture<Void> uploadDirectory(Path albumDirectoryPath,
                                            Optional<GooglePhotosAlbum> googlePhotosAlbum,
                                            ProgressStatus fileProgressStatus);

    void doNotResume();

    void forgetUploadStateOnShutdown();
}
