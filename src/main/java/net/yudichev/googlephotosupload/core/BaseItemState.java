package net.yudichev.googlephotosupload.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import org.immutables.value.Value;
import org.immutables.value.Value.Immutable;

import java.time.Instant;
import java.util.Optional;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Immutable
@PackagePrivateImmutablesStyle
@JsonSerialize
@JsonDeserialize
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
interface BaseItemState {
    Optional<UploadMediaItemState> uploadState();

    Optional<String> mediaId();

    @Immutable
    @PackagePrivateImmutablesStyle
    @JsonSerialize
    @JsonDeserialize
    @JsonInclude(NON_NULL)
    interface BaseUploadMediaItemState {
        @Value.Parameter
        String token();

        @Value.Parameter
        Instant uploadInstant();
    }
}
