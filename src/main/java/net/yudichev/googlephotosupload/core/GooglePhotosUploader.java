package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

interface GooglePhotosUploader {
    CompletableFuture<Void> uploadDirectory(Path albumDirectoryPath,
                                            Optional<GooglePhotosAlbum> googlePhotosAlbum,
                                            ProgressStatus fileProgressStatus);

    int canResume();

    void doNotResume();

    void forgetUploadStateOnShutdown();
}
