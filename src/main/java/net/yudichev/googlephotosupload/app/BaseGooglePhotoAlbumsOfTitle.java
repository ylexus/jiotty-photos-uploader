package net.yudichev.googlephotosupload.app;

import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;
import org.immutables.value.Value;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Value.Immutable
@PackagePrivateImmutablesStyle
interface BaseGooglePhotoAlbumsOfTitle {
    Set<GooglePhotosAlbum> albums();

    CompletableFuture<GooglePhotosAlbum> primaryAlbum();
}
