package net.yudichev.googlephotosupload.ui;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import org.immutables.value.Value.Immutable;

import java.util.Set;

@Immutable
@PackagePrivateImmutablesStyle
@JsonSerialize
@JsonDeserialize
interface BaseSourceDirectories {
    Set<String> paths();
}
