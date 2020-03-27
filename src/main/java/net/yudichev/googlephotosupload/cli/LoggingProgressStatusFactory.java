package net.yudichev.googlephotosupload.cli;

import net.yudichev.googlephotosupload.core.ProgressStatus;
import net.yudichev.googlephotosupload.core.ProgressStatusFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static net.yudichev.jiotty.common.lang.Locks.inLock;
import static net.yudichev.jiotty.common.lang.Optionals.ifPresent;

final class LoggingProgressStatusFactory implements ProgressStatusFactory {
    private static final Logger logger = LoggerFactory.getLogger(LoggingProgressStatusFactory.class);

    @Override
    public ProgressStatus create(String name, Optional<Integer> totalCount) {
        return new ProgressStatus() {
            private final Lock lock = new ReentrantLock();
            private int successCount;
            private int failureCount;

            @Override
            public void updateSuccess(int newValue) {
                inLock(lock, () -> {
                    successCount = newValue;
                    log();
                });
            }

            @Override
            public void incrementSuccessBy(int increment) {
                inLock(lock, () -> {
                    successCount += increment;
                    log();
                });
            }

            @Override
            public void incrementFailureBy(int increment) {
                inLock(lock, () -> {
                    failureCount += increment;
                    log();
                });
            }

            @Override
            public void close(boolean success) {
                inLock(lock, () -> logger.info("{}: completed; {} succeeded, {} failed", name, successCount, failureCount));
            }

            private void log() {
                inLock(lock, () -> ifPresent(totalCount,
                        totalCount -> logger.info("{}: progress {}%", name, (successCount + failureCount) * 100 / totalCount))
                        .orElse(() -> logger.info("{}: completed {}", name, successCount + failureCount)));
            }
        };
    }
}
