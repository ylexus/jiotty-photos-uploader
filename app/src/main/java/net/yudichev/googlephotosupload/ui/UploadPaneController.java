package net.yudichev.googlephotosupload.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

interface UploadPaneController {
    void addProgressBox(ProgressBox progressBox);

    void reset();

    CompletableFuture<Void> startUpload(List<Path> path, boolean resume);

    void stopUpload();
}
