package net.yudichev.googlephotosupload.core;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

interface FilesystemManager {
    void walkDirectories(Path rootDir, Consumer<Path> directoryHandler);

    List<Path> listFiles(Path directory);
}
