package net.yudichev.googlephotosupload.ui;

import javafx.scene.Node;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

interface UploadPaneController {
    void addProgressBox(Node node);

    void reset();

    CompletableFuture<Void> startUpload(Path path, boolean resume);

    void stopUpload();
}
