package net.yudichev.googlephotosupload.app;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

interface FilesystemManager {
    void walkDirectories(Consumer<Path> directoryHandler);

    List<Path> listFiles(Path directory);
}
