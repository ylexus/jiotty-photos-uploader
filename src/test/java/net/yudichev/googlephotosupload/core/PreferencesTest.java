package net.yudichev.googlephotosupload.core;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Paths;

import static net.yudichev.googlephotosupload.core.OptionalMatchers.emptyOptional;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class PreferencesTest {
    @SuppressWarnings("deprecation")
    @Test
    void migrationOfPatterns() {
        var migratedPreferences = Preferences.builder()
                .setScanExclusionPatterns(ImmutableSet.of(".*picasaoriginals", "\\..*", ".*custom_pattern"))
                .build();

        assertThat(migratedPreferences.scanExclusionPatterns(), is(emptyOptional()));
        assertThat(migratedPreferences.scanExclusionGlobs(), containsInAnyOrder(
                "glob:**/*picasaoriginals", "glob:**/.*", "glob:**/.*/**", "regex:.*/.*custom_pattern/.*", "regex:.*/.*custom_pattern"));
    }

    @Test
    void pathMatchingIncludeAndExcludeIsNotMatched() {
        var preferences = Preferences.builder()
                .addScanInclusionGlobs("glob:*.txt")
                .addScanExclusionGlobs("glob:a.txt")
                .build();

        assertThat(preferences.shouldIncludePath(Paths.get("a.txt")), is(false));
    }

    @Test
    void pathMatchingIncludeAndNoExcludesIsMatched() {
        var preferences = Preferences.builder()
                .addScanInclusionGlobs("glob:*.txt")
                .addScanExclusionGlobs("glob:a.txt")
                .build();

        assertThat(preferences.shouldIncludePath(Paths.get("b.txt")), is(true));
    }

    @Test
    void pathNotMatchingIncludeNorExcludesIsNotMatched() {
        var preferences = Preferences.builder()
                .addScanInclusionGlobs("glob:*.txt")
                .addScanExclusionGlobs("glob:a.txt")
                .build();

        assertThat(preferences.shouldIncludePath(Paths.get("a.html")), is(false));
    }

    @Test
    void pathNotMatchingIncludeButMatchingExcludesIsNotMatched() {
        var preferences = Preferences.builder()
                .addScanInclusionGlobs("glob:*.txt")
                .addScanExclusionGlobs("glob:g.html")
                .build();

        assertThat(preferences.shouldIncludePath(Paths.get("g.html")), is(false));
    }

    @Test
    void noIncludePatternsPathMatchingExcludeIsNotMatched() {
        var preferences = Preferences.builder()
                .addScanExclusionGlobs("glob:g.html")
                .build();

        assertThat(preferences.shouldIncludePath(Paths.get("g.html")), is(false));
    }

    @Test
    void noIncludePatternsPathNotMatchingExcludeIsMatched() {
        var preferences = Preferences.builder()
                .addScanExclusionGlobs("glob:g.html")
                .build();

        assertThat(preferences.shouldIncludePath(Paths.get("a.html")), is(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/path/to/picasa.ini",
            "/path/to/.picasa.ini",
            "/path/to/.file.jpg",
            "/path/.to/file.jpg",
            "/path/.to/some/file.jpg",
            "/path/to/.DS_Store",
            "/path/to/Thumbs.db",
            "/path/to/a.txt",
            "/path/to/a.exe",
            "/path/to/a.htm",
            "/path/to/a.html",
            "/path/to/desktop.ini",
    })
    void defaultExclusionPatternScenarios(String path) {
        var preferences = Preferences.builder().build();

        assertThat(preferences.shouldIncludePath(Paths.get(path)), is(false));
    }
}