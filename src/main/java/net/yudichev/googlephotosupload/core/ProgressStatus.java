package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.lang.Closeable;

public interface ProgressStatus extends Closeable {
    void updateSuccess(int newValue);

    void incrementSuccessBy(int increment);

    void incrementFailureBy(int increment);

    default void incrementSuccess() {
        incrementSuccessBy(1);
    }

    default void incrementFailure() {
        incrementFailureBy(1);
    }
}
