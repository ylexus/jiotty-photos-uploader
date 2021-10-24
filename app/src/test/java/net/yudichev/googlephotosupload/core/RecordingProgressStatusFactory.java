package net.yudichev.googlephotosupload.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Collections.unmodifiableMap;
import static net.yudichev.jiotty.common.lang.Locks.inLock;

final class RecordingProgressStatusFactory implements ProgressStatusFactory {
    private final Map<String, RecordingProgressStatus> statusByName = new ConcurrentHashMap<>();

    @Override
    public ProgressStatus create(String name, Optional<Integer> totalCount) {
        var status = new RecordingProgressStatus(name, totalCount);
        checkState(statusByName.put(name, status) == null, "status already created for %s", name);
        return status;
    }

    public Map<String, Set<KeyedError>> getRecordedErrorsByProgressName() {
        return statusByName.entrySet().stream()
                .filter(entry -> !entry.getValue().getRecordedErrors().isEmpty())
                .collect(toImmutableMap(Map.Entry::getKey, entry -> entry.getValue().getRecordedErrors()));
    }

    public Map<String, RecordingProgressStatus> getStatusByName() {
        return unmodifiableMap(statusByName);
    }

    public void reset() {
        statusByName.clear();
    }

    static final class RecordingProgressStatus implements ProgressStatus {
        private static final Logger logger = LoggerFactory.getLogger(RecordingProgressStatus.class);

        private final Lock lock = new ReentrantLock();
        private final Set<KeyedError> recordedErrors = new HashSet<>();
        private final String name;
        private Optional<Integer> totalCount;
        private int successCount;
        private String description;
        private Optional<Boolean> closedWithSuccess = Optional.empty();

        private RecordingProgressStatus(String name, Optional<Integer> totalCount) {
            this.name = checkNotNull(name);
            this.totalCount = checkNotNull(totalCount);
        }

        @Override
        public void updateSuccess(int newValue) {
            inLock(lock, () -> {
                successCount = newValue;
                logger.debug("{}: updateSuccess->{}", name, newValue);
            });
        }

        @Override
        public void updateTotal(int newValue) {
            inLock(lock, () -> {
                totalCount = Optional.of(newValue);
                logger.debug("{}: updateTotal->{}", name, newValue);
            });
        }

        @Override
        public void updateDescription(String newValue) {
            inLock(lock, () -> {
                description = newValue;
                logger.debug("{}: updateDescription->{}", name, newValue);
            });
        }

        @Override
        public void incrementSuccessBy(int increment) {
            inLock(lock, () -> {
                successCount += increment;
                logger.debug("{}: incrementSuccessBy->{}", name, increment);
            });
        }

        @Override
        public void onBackoffDelay(long backoffDelayMs) {
        }

        @Override
        public void close(boolean success) {
            inLock(lock, () -> {
                closedWithSuccess = Optional.of(success);
                logger.debug("{}: close->{}", name, success);
            });
        }

        @Override
        public void addFailure(KeyedError keyedError) {
            inLock(lock, () -> {
                recordedErrors.add(keyedError);
                logger.debug("{}: addFailure->{}", name, keyedError);
            });
        }

        public Set<KeyedError> getRecordedErrors() {
            return inLock(lock, () -> recordedErrors);
        }

        public Optional<Integer> getTotalCount() {
            return inLock(lock, () -> totalCount);
        }

        public int getSuccessCount() {
            return inLock(lock, () -> successCount);
        }

        public String getDescription() {
            return inLock(lock, () -> description);
        }

        public Optional<Boolean> getClosedWithSuccess() {
            return inLock(lock, () -> closedWithSuccess);
        }
    }
}
