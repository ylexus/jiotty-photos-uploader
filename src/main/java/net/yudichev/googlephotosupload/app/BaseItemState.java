package net.yudichev.googlephotosupload.app;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.jiotty.common.lang.PackagePrivateImmutablesStyle;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
@PackagePrivateImmutablesStyle
@JsonSerialize
@JsonDeserialize
interface BaseItemState {
    @Value.Parameter
    Optional<String> mediaId();

    @Value.Parameter
    Optional<String> albumId();
}
