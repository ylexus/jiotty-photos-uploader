package net.yudichev.googlephotosupload.core;

import org.junit.jupiter.api.Test;

import static net.yudichev.googlephotosupload.core.DriveSpaceTrackerImpl.maxUsedPercentageReached;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class DriveSpaceTrackerImplTest {
    private static final long GB = 1024L * 1024 * 1024;

    @Test
    void maxUsedPercentageIsReachedOnlyAtOrAboveTheThreshold() {
        assertThat(maxUsedPercentageReached(GB * 8, GB * 10, 80.0), is(true));
        assertThat(maxUsedPercentageReached(GB * 9, GB * 10, 80.0), is(true));
        assertThat(maxUsedPercentageReached(GB * 7, GB * 10, 80.0), is(false));
    }

    @Test
    void maxUsedPercentageHandlesFractionalPercentages() {
        assertThat(maxUsedPercentageReached(995, 1000, 99.5), is(true));
        assertThat(maxUsedPercentageReached(994, 1000, 99.5), is(false));
    }
}
