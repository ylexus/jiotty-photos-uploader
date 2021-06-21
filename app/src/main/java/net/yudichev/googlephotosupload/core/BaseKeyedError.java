package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.lang.PublicImmutablesStyle;
import org.immutables.value.Value;
import org.immutables.value.Value.Immutable;

@Immutable
@PublicImmutablesStyle
interface BaseKeyedError {
    @Value.Parameter
    Object getKey();

    @Value.Parameter
    String getError();
}
