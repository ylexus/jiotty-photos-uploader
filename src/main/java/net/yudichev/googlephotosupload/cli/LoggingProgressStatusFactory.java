package net.yudichev.googlephotosupload.cli;

import net.yudichev.googlephotosupload.core.KeyedError;
import net.yudichev.googlephotosupload.core.ProgressStatus;
import net.yudichev.googlephotosupload.core.ProgressStatusFactory;
import net.yudichev.jiotty.common.async.ExecutorFactory;
import net.yudichev.jiotty.common.async.SchedulingExecutor;
import net.yudichev.jiotty.common.lang.throttling.ThrottlingConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static net.yudichev.jiotty.common.lang.Locks.inLock;

final class LoggingProgressStatusFactory implements ProgressStatusFactory {
    private static final long BACKOFF_DELAY_MS_BEFORE_NOTICE_APPEARS = Duration.ofMinutes(1).toMillis();
    private static final Logger logger = LoggerFactory.getLogger(LoggingProgressStatusFactory.class);
    private final ThrottlingConsumer<Runnable> eventSink;
    private final SchedulingExecutor executor;

    @Inject
    LoggingProgressStatusFactory(ExecutorFactory executorFactory) {
        executor = executorFactory.createSingleThreadedSchedulingExecutor("progress-status");
        eventSink = new ThrottlingConsumer<>(executor, Duration.ofSeconds(3), Runnable::run);
    }

    @Override
    public ProgressStatus create(String name, Optional<Integer> totalCount) {
        return new ProgressStatus() {
            private final Lock lock = new ReentrantLock();
            private int successCount;
            private int failureCount;

            @Override
            public void updateSuccess(int newValue) {
                inLock(lock, () -> { successCount = newValue;});
                throttledLog();
            }

            @Override
            public void updateDescription(String newValue) {
                // do nothing in CLI - log is enough
            }

            @Override
            public void incrementSuccessBy(int increment) {
                inLock(lock, () -> { successCount += increment;});
                throttledLog();
            }

            @Override
            public void onBackoffDelay(long backoffDelayMs) {
                if (backoffDelayMs > BACKOFF_DELAY_MS_BEFORE_NOTICE_APPEARS) {
                    logger.info("Pausing for a long time due to Google imposed request quota...");
                }
            }

            @Override
            public void addFailure(KeyedError keyedError) {
                logger.warn("Failure for {}: {}", keyedError.getKey(), keyedError.getError());
                inLock(lock, () -> { failureCount += 1;});
                throttledLog();
            }

            @Override
            public void close(boolean success) {
                inLock(lock, () -> logger.info("{}: completed; {} succeeded, {} failed", name, successCount, failureCount));
                executor.close();
            }

            private void throttledLog() {
                eventSink.accept(() -> inLock(lock, () -> totalCount.ifPresentOrElse(
                        totalCount -> {
                            if (totalCount > 0) {
                                logger.info("{}: progress {}%", name, (successCount + failureCount) * 100 / totalCount);
                            }
                        },
                        () -> logger.info("{}: completed {}", name, successCount + failureCount))));
            }
        };
    }
}