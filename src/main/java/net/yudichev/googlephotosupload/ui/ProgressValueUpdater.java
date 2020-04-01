package net.yudichev.googlephotosupload.ui;

import net.yudichev.googlephotosupload.core.KeyedError;

import java.util.Collection;

interface ProgressValueUpdater {
    void updateSuccess(int newValue);

    void addFailures(Collection<KeyedError> failures);

    void close(boolean success);

    void onBackoffDelay(long backoffDelayMs);
}
