package net.yudichev.googlephotosupload.core;

import java.nio.file.Path;

public interface Uploader {
    void start(Path rootDir);
}
