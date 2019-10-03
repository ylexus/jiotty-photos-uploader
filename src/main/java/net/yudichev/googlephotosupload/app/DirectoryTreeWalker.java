package net.yudichev.googlephotosupload.app;

import java.nio.file.Path;
import java.util.function.Consumer;

interface DirectoryTreeWalker {
    void walk(Consumer<Path> fileHandler);
}
