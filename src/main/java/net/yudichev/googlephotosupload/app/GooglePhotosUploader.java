package net.yudichev.googlephotosupload.app;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

interface GooglePhotosUploader {
    CompletableFuture<Void> uploadFile(Path file);
}
