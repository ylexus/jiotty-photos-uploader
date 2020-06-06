package net.yudichev.googlephotosupload.core;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import org.immutables.value.Value;

import java.time.Instant;

@Value.Immutable
@PackagePrivateImmutablesStyle
@JsonSerialize
@JsonDeserialize
abstract class BaseMediaItemContents {
    @Value.Parameter
    public abstract Instant creationTime();

    @Value.Parameter
    public abstract int contents();

    public final String toMediaItemId() {
        return creationTime().toString() + '/' + contents();
    }
}
