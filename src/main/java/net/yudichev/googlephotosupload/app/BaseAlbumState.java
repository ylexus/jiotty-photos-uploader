package net.yudichev.googlephotosupload.app;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import org.immutables.value.Value;
import org.immutables.value.Value.Immutable;

import java.util.Optional;
import java.util.Set;

@Immutable
@PackagePrivateImmutablesStyle
@JsonDeserialize
@JsonSerialize
interface BaseAlbumState {
    @Value.Parameter
    Set<String> albumIds();

    Optional<String> primaryAlbumId();
}
