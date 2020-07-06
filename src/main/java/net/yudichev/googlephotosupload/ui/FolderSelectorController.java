package net.yudichev.googlephotosupload.ui;

import java.nio.file.Path;
import java.util.function.BiConsumer;

interface FolderSelectorController {
    void refresh();

    void setFolderSelectedAction(BiConsumer<Path, Boolean> folderSelectionListener);
}
