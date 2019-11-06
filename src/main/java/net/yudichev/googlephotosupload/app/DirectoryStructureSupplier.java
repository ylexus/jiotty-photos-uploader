package net.yudichev.googlephotosupload.app;

import java.util.List;
import java.util.concurrent.CompletableFuture;

interface DirectoryStructureSupplier {
    CompletableFuture<List<AlbumDirectory>> listAlbumDirectories();
}
