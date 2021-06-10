package net.yudichev.googlephotosupload.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.yudichev.jiotty.common.lang.PublicImmutablesStyle;
import org.immutables.value.Value;
import org.immutables.value.Value.Immutable;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

@Immutable
@PublicImmutablesStyle
@JsonDeserialize
@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class BasePreferences {
    private static final String P_DOT_FILES = "glob:**/.*";
    private static final String P_DOT_DIRS = "glob:**/.*/**";
    private static final String P_DS_STORE = "glob:**/DS_Store";
    private static final String P_THUMBS_DB = "glob:**/Thumbs.db";
    private static final String P_DESKTOP_INI = "glob:**/desktop.ini";
    private static final String P_PICASAORIGINALS = "glob:**/*picasaoriginals";
    private static final String P_PICASA_INI = "glob:**/*[Pp]icasa.[Ii][Nn][Ii]";
    private static final String P_TXT_EXE_HTML = "glob:**/*.{txt,TXT,exe,EXE,htm,HTM,html,HTML}";
    static final Set<String> DEFAULT_SCAN_EXCLUSION_GLOBS = ImmutableSet.of(
            P_DOT_FILES,
            P_DOT_DIRS,
            P_DS_STORE,
            P_THUMBS_DB,
            P_DESKTOP_INI,
            P_PICASAORIGINALS,
            P_PICASA_INI,
            P_TXT_EXE_HTML);

    private static final Map<String, Set<String>> KNOWN_PATTERNS_MIGRATION_MAP = ImmutableMap.<String, Set<String>>builder()
            .put("\\..*", ImmutableSet.of(P_DOT_FILES, P_DOT_DIRS))
            .put(".*picasaoriginals", ImmutableSet.of(P_PICASAORIGINALS))
            .put(".*[Pp]icasa.[Ii][Nn][Ii]", ImmutableSet.of(P_PICASA_INI))
            .put("DS_Store", ImmutableSet.of(P_DS_STORE))
            .put("Thumbs.db", ImmutableSet.of(P_THUMBS_DB))
            .put(".*\\.(txt|exe|htm)", ImmutableSet.of(P_TXT_EXE_HTML))
            .put("desktop.ini", ImmutableSet.of(P_DESKTOP_INI))
            .build();

    /**
     * @deprecated {@link #scanExclusionGlobs()} is the newer alternative
     */
    @Deprecated
    public abstract Optional<Set<String>> scanExclusionPatterns();

    @Value.Default
    public Set<String> scanExclusionGlobs() {
        return DEFAULT_SCAN_EXCLUSION_GLOBS;
    }

    @Value.Default
    public Set<String> scanInclusionGlobs() {
        return ImmutableSet.of();
    }

    public abstract Optional<AddToAlbumMethod> addToAlbumStrategy();

    @Value.Default
    public String albumDelimiter() {
        return ": ";
    }

    public abstract Optional<Integer> relevantDirDepthLimit();

    @Value.Default
    public boolean useCustomCredentials() {
        return false;
    }

    public abstract Optional<FailOnDriveSpaceOption> failOnDriveSpace();

    @Value.Check
    void validateRelevantDirDepthLimit() {
        relevantDirDepthLimit().ifPresent(value -> checkArgument(value > 0, "validateRelevantDirDepthLimit cannot be <=0: %s", value));
    }

    public static boolean validatePathPattern(String pattern) {
        var fileSystem = FileSystems.getDefault();
        try {
            fileSystem.getPathMatcher(pattern);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public final boolean shouldIncludePath(Path path) {
        return matchesInclusionPatternIfAny(path) && compiledScanExclusionMatchers().stream().noneMatch(matcher -> matcher.matches(path));
    }

    private boolean matchesInclusionPatternIfAny(Path path) {
        var matchers = compiledScanInclusionMatchers();
        return matchers.isEmpty() || matchers.stream().anyMatch(matcher -> matcher.matches(path));
    }

    @SuppressWarnings({"ClassReferencesSubclass", "deprecation"})
    @Value.Check
    BasePreferences migrateIfNeeded() {
        var preferences = (Preferences) this;
        return scanExclusionPatterns()
                .map(legacyPatterns -> {
                    checkArgument(scanExclusionGlobs().equals(DEFAULT_SCAN_EXCLUSION_GLOBS));
                    return preferences
                            .withScanExclusionPatterns(Optional.empty())
                            .withScanExclusionGlobs(legacyPatterns.stream()
                                    .map(legacyRegexp -> KNOWN_PATTERNS_MIGRATION_MAP.getOrDefault(legacyRegexp,
                                            ImmutableSet.of(
                                                    "regex:.*/" + legacyRegexp + "/.*",
                                                    "regex:.*/" + legacyRegexp)))
                                    .flatMap(Collection::stream)
                                    .collect(toImmutableSet()));
                })
                .orElse(preferences);
    }

    @Value.Derived
    @JsonIgnore
    protected Set<PathMatcher> compiledScanExclusionMatchers() {
        return compile(scanExclusionGlobs());
    }

    @Value.Derived
    @JsonIgnore
    protected Set<PathMatcher> compiledScanInclusionMatchers() {
        return compile(scanInclusionGlobs());
    }

    private static Set<PathMatcher> compile(Set<String> strings) {
        var fileSystem = FileSystems.getDefault();
        return strings.stream()
                .map(fileSystem::getPathMatcher)
                .collect(toImmutableSet());
    }

    @Immutable
    @PublicImmutablesStyle
    interface BaseFailOnDriveSpaceOption {
        Optional<Integer> minFreeMegabytes();

        Optional<Double> maxUsedPercentage();

        @Value.Check
        default void validate() {
            checkArgument(minFreeMegabytes().isPresent() ^ maxUsedPercentage().isPresent());
        }
    }
}
