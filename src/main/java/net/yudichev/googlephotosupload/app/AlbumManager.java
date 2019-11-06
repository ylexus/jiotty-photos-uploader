package net.yudichev.googlephotosupload.app;

import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

interface AlbumManager {
    CompletableFuture<Map<String, GooglePhotosAlbum>> albumsByTitle(List<AlbumDirectory> albumDirectories);
}
