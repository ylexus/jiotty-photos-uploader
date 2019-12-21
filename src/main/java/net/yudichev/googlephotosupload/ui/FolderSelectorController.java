package net.yudichev.googlephotosupload.ui;

import java.nio.file.Path;
import java.util.function.Consumer;

interface FolderSelectorController {
    void setFolderSelectedAction(Consumer<Path> folderSelectionListener);
}
