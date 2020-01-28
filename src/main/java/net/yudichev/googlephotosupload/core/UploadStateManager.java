package net.yudichev.googlephotosupload.core;

interface UploadStateManager {
    UploadState get();

    void save(UploadState uploadState);
}
