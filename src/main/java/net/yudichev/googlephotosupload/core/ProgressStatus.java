package net.yudichev.googlephotosupload.core;

public interface ProgressStatus {
    void updateSuccess(int newValue);

    void incrementSuccessBy(int increment);

    void incrementFailureBy(int increment);

    default void incrementSuccess() {
        incrementSuccessBy(1);
    }

    default void incrementFailure() {
        incrementFailureBy(1);
    }

    void close(boolean success);

    default void closeSuccessfully() {
        close(true);
    }

    default void closeUnsuccessfully() {
        close(false);
    }
}
