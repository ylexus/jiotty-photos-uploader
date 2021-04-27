package net.yudichev.googlephotosupload.core;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.collect.Sets.newConcurrentHashSet;
import static java.util.Collections.unmodifiableMap;

final class RecordingProgressStatusFactory implements ProgressStatusFactory {
    private final Map<String, Set<KeyedError>> recordedErrorsByProgressName = new ConcurrentHashMap<>();

    @Override
    public ProgressStatus create(String name, Optional<Integer> totalCount) {
        return new ProgressStatus() {
            @Override
            public void updateSuccess(int newValue) {
            }

            @Override
            public void updateDescription(String newValue) {
            }

            @Override
            public void incrementSuccessBy(int increment) {
            }

            @Override
            public void onBackoffDelay(long backoffDelayMs) {
            }

            @Override
            public void close(boolean success) {
            }

            @Override
            public void addFailure(KeyedError keyedError) {
                recordedErrorsByProgressName.computeIfAbsent(name, ignored -> newConcurrentHashSet()).add(keyedError);
            }
        };
    }

    public Map<String, Set<KeyedError>> getRecordedErrorsByProgressName() {
        return unmodifiableMap(recordedErrorsByProgressName);
    }

    public void reset() {
        recordedErrorsByProgressName.clear();
    }
}
