package net.yudichev.googlephotosupload.ui;

import javafx.scene.Node;
import net.yudichev.googlephotosupload.core.AddToAlbumMethod;
import net.yudichev.jiotty.common.lang.Closeable;

import java.util.function.Consumer;

interface UploaderStrategyChoicePanelController {
    Node getRoot();

    void setSelection(AddToAlbumMethod addToAlbumMethod);

    Closeable addSelectionChangeListener(Consumer<AddToAlbumMethod> selectionChangeHandler);
}
