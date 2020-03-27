package net.yudichev.googlephotosupload.ui;

interface ProgressValueUpdater {
    void updateSuccess(int newValue);

    void updateFailure(int newValue);

    void close(boolean success);
}
