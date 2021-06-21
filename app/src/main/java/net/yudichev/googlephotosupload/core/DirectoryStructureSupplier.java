package net.yudichev.googlephotosupload.core;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

interface DirectoryStructureSupplier {
    CompletableFuture<List<AlbumDirectory>> listAlbumDirectories(List<Path> rootDirs);
}
