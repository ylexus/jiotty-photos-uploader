package net.yudichev.googlephotosupload.app;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import org.immutables.value.Value;

import java.util.Map;

@Value.Immutable
@PackagePrivateImmutablesStyle
@JsonSerialize
@JsonDeserialize
interface BaseAlbumsState {
    //TODO rename
    @Value.Parameter
    Map<String, AlbumState> uploadedAlbumIdByTitle();
}
