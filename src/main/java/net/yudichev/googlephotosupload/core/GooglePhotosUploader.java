package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

interface GooglePhotosUploader {
    CompletableFuture<ResultOrFailure<Object>> uploadFile(Optional<GooglePhotosAlbum> album, Path file);

    void doNotResume();

    int canResume();
}
