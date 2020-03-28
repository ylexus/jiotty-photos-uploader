package net.yudichev.googlephotosupload.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableSet;
import net.yudichev.jiotty.common.lang.PublicImmutablesStyle;
import org.immutables.value.Value;
import org.immutables.value.Value.Immutable;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

@Immutable
@PublicImmutablesStyle
@JsonDeserialize
@JsonSerialize
abstract class BasePreferences {
    @Value.Default
    @Value.Parameter
    public Set<String> scanExclusionPatterns() {
        return ImmutableSet.of(
                "\\..*",
                ".*picasaoriginals",
                ".*[Pp]icasa.[Ii][Nn][Ii]",
                "DS_Store",
                "Thumbs.db",
                ".*\\.(txt|exe|htm)");
    }

    public final boolean anyMatch(Path path) {
        return patterns().stream().anyMatch(pattern -> pattern.matcher(path.getFileName().toString()).matches());
    }

    public final boolean noneMatch(Path path) {
        return patterns().stream().noneMatch(pattern -> pattern.matcher(path.getFileName().toString()).matches());
    }

    @Value.Derived
    @JsonIgnore
    protected Set<Pattern> patterns() {
        return scanExclusionPatterns().stream()
                .map(Pattern::compile)
                .collect(toImmutableSet());
    }
}
