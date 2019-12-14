package net.yudichev.googlephotosupload.cli;

import net.yudichev.googlephotosupload.core.ProgressStatus;
import net.yudichev.googlephotosupload.core.ProgressStatusFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static net.yudichev.jiotty.common.lang.Optionals.ifPresent;

final class LoggingProgressStatusFactory implements ProgressStatusFactory {
    private static final Logger logger = LoggerFactory.getLogger(LoggingProgressStatusFactory.class);

    @Override
    public ProgressStatus create(String name, Optional<Integer> totalCount) {
        return new ProgressStatus() {
            private int value;

            @Override
            public void update(int newValue) {
                value = newValue;
                log();
            }

            @Override
            public void incrementBy(int increment) {
                value += increment;
                log();
            }

            @Override
            public void close() {
                logger.info("{}: completed", name);
            }

            private void log() {
                ifPresent(totalCount,
                        totalCount -> logger.info("{}: progress {}%", name, value * 100 / totalCount))
                        .orElse(() -> logger.info("{}: completed {}", name, value));
            }
        };
    }
}
