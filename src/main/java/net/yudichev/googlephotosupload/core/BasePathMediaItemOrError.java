package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import net.yudichev.jiotty.connector.google.photos.GoogleMediaItem;
import org.immutables.value.Value;

import java.nio.file.Path;

@Value.Immutable
@PackagePrivateImmutablesStyle
interface BasePathMediaItemOrError {
    @Value.Parameter
    Path path();

    @Value.Parameter
    GoogleMediaItem mediaItem();
}
