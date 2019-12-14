package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

interface GooglePhotosUploader {
    CompletableFuture<Void> uploadFile(Optional<GooglePhotosAlbum> album, Path file);
}
