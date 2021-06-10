package net.yudichev.googlephotosupload.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;

interface DriveSpaceTracker {
    CompletableFuture<Void> reset();

    boolean validationEnabled();

    void beforeUpload();

    void afterUpload(List<PathState> pathStates);
}
