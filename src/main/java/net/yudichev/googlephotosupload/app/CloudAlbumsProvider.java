package net.yudichev.googlephotosupload.app;

import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

interface CloudAlbumsProvider {
    CompletableFuture<Map<String, List<GooglePhotosAlbum>>> listCloudAlbums();
}
