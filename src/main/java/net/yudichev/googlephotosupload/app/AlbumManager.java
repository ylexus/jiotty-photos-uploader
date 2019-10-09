package net.yudichev.googlephotosupload.app;

import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

interface AlbumManager {
    Optional<CompletableFuture<GooglePhotosAlbum>> albumForDir(Path dir);
}
