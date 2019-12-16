package net.yudichev.googlephotosupload.core;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface Uploader {
    CompletableFuture<Void> upload(Path rootDir);
}
