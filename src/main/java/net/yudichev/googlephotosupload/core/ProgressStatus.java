package net.yudichev.googlephotosupload.core;

public interface ProgressStatus {
    void updateSuccess(int newValue);

    void incrementSuccessBy(int increment);

    void onBackoffDelay(long backoffDelayMs);

    default void incrementSuccess() {
        incrementSuccessBy(1);
    }

    void close(boolean success);

    default void closeSuccessfully() {
        close(true);
    }

    default void closeUnsuccessfully() {
        close(false);
    }

    void addFailure(KeyedError keyedError);
}
