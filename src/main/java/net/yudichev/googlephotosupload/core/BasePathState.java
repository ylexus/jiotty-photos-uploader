package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import net.yudichev.jiotty.common.lang.ResultOrFailure;
import org.immutables.value.Value;

import java.nio.file.Path;

@Value.Immutable
@PackagePrivateImmutablesStyle
interface BasePathState {
    @Value.Parameter
    Path path();

    @Value.Parameter
    ResultOrFailure<ItemState> state();
}
