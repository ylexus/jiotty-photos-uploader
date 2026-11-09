package net.yudichev.googlephotosupload.ui;

import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static net.yudichev.googlephotosupload.ui.VersionCheck.VERSION_ORDER;
import static net.yudichev.googlephotosupload.ui.VersionCheck.checksForUpdates;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

final class VersionCheckTest {
    @Test
    void checksForUpdatesOnlyInReleaseBuildsNotInstalledFromTheStore() {
        assertThat(checksForUpdates("2.2.0", null), is(true));
        assertThat(checksForUpdates("2.2.0", "msstore"), is(false));
        assertThat(checksForUpdates("0.DEV", null), is(false));
    }

    // equal versions compare as 0
    @Test
    @SuppressWarnings("EqualsWithItself")
    void ordersVersionsNumerically() {
        assertThat(VERSION_ORDER.compare("2.10.0", "2.9.0"), greaterThan(0));
        assertThat(VERSION_ORDER.compare("2.2.0", "2.10.0"), lessThan(0));
        assertThat(VERSION_ORDER.compare("10.0.0", "9.99.99"), greaterThan(0));
        assertThat(VERSION_ORDER.compare("2.2.0", "2.2.0"), is(0));
    }

    @Test
    void toleratesNonNumericParts() {
        assertThat(VERSION_ORDER.compare("2.2.0", "0.DEV"), greaterThan(0));
        assertThat(VERSION_ORDER.compare("2.0.6-beta", "2.0.6"), is(0));
    }

    @Test
    void sortsNewestFirst() {
        var sorted = Stream.of("2.9.0", "2.10.0", "2.2.0").sorted(VERSION_ORDER.reversed()).toList();
        assertThat(sorted, contains("2.10.0", "2.9.0", "2.2.0"));
    }
}
