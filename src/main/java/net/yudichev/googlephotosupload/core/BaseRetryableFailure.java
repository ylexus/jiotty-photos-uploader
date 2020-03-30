package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
@PackagePrivateImmutablesStyle
interface BaseRetryableFailure {
    @Value.Parameter
    Throwable exception();

    @Value.Parameter
    Optional<Long> backoffDelayMs();
}
