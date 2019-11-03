package net.yudichev.googlephotosupload.app;

import java.util.List;

interface DirectoryStructureSupplier {
    List<AlbumDirectory> getAlbumDirectories();
}
