package net.yudichev.googlephotosupload.app;

import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;

interface AlbumManager {
    GooglePhotosAlbum albumForTitle(String albumTitle);
}
