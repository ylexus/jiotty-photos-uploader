package net.yudichev.googlephotosupload.ui;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.VBox;
import net.yudichev.googlephotosupload.core.AddToAlbumMethod;
import net.yudichev.jiotty.common.lang.Closeable;
import net.yudichev.jiotty.common.lang.CompositeException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static net.yudichev.googlephotosupload.core.AddToAlbumMethod.AFTER_CREATING_ITEMS_SORTED;
import static net.yudichev.googlephotosupload.core.AddToAlbumMethod.WHILE_CREATING_ITEMS;

public final class UploaderStrategyChoicePanelControllerImpl implements UploaderStrategyChoicePanelController {
    private final List<Consumer<AddToAlbumMethod>> listeners = new CopyOnWriteArrayList<>();
    public RadioButton addToAlbumWhileCreatingRadioButton;
    public RadioButton addAfterCreatingItemsRadioButton;
    public VBox root;

    @Override
    public Node getRoot() {
        return root;
    }

    @Override
    public void setSelection(AddToAlbumMethod addToAlbumMethod) {
        addAfterCreatingItemsRadioButton.setSelected(addToAlbumMethod == AFTER_CREATING_ITEMS_SORTED);
        addToAlbumWhileCreatingRadioButton.setSelected(addToAlbumMethod == WHILE_CREATING_ITEMS);
    }

    public void onRadioButtonSelectionChange(ActionEvent actionEvent) {
        CompositeException.runForAll(listeners, listener -> {
            var method = addAfterCreatingItemsRadioButton.isSelected() ? AFTER_CREATING_ITEMS_SORTED : WHILE_CREATING_ITEMS;
            listener.accept(method);
        });
        actionEvent.consume();
    }

    @Override
    public Closeable addSelectionChangeListener(Consumer<AddToAlbumMethod> selectionChangeHandler) {
        listeners.add(selectionChangeHandler);
        return () -> listeners.remove(selectionChangeHandler);
    }
}
