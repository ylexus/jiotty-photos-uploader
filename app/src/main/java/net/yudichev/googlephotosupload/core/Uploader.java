package net.yudichev.googlephotosupload.core;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Uploader {
    CompletableFuture<Void> upload(List<Path> rootDirs, boolean resume);

    int numberOfUploadedItems();

    void forgetUploadState();
}
