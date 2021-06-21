package net.yudichev.googlephotosupload.core;

import com.google.inject.AbstractModule;
import net.yudichev.jiotty.common.time.CurrentDateTimeProvider;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static java.time.Instant.EPOCH;

final class TestTimeModule extends AbstractModule {
    private static Instant currentInstant;

    static {
        resetTime();
    }

    public static Instant getCurrentInstant() {
        return currentInstant;
    }

    public static void advanceTimeBy(Duration duration) {
        currentInstant = currentInstant.plus(duration);
    }

    public static void resetTime() {
        currentInstant = EPOCH;
    }

    @Override
    protected void configure() {
        bind(CurrentDateTimeProvider.class).toInstance(new CurrentDateTimeProvider() {
            @Override
            public LocalDateTime currentDateTime() {
                return LocalDateTime.ofInstant(currentInstant(), ZoneOffset.UTC);
            }

            @Override
            public Instant currentInstant() {
                return currentInstant;
            }
        });
    }
}
