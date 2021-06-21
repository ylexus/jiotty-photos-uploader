package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

interface AlbumManager {
    CompletableFuture<Map<String, GooglePhotosAlbum>> listAlbumsByTitle(List<AlbumDirectory> albumDirectories,
                                                                        Map<String, List<GooglePhotosAlbum>> cloudAlbumsByTitle);
}
