package net.yudichev.googlephotosupload.ui;

import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.converter.DefaultStringConverter;
import net.yudichev.googlephotosupload.core.Preferences;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.scene.control.SelectionMode.MULTIPLE;

public final class PreferencePatternEditorController {
    public ListView<String> listView;
    public Button plusButton;
    public Button minusButton;
    private Consumer<List<String>> changeHandler;

    public void initialise(Set<String> scanExclusionGlobs, Consumer<List<String>> changeHandler) {
        this.changeHandler = checkNotNull(changeHandler);
        listView.getItems().addAll(scanExclusionGlobs);

        var selectionModel = listView.getSelectionModel();
        selectionModel.setSelectionMode(MULTIPLE);
        selectionModel.getSelectedItems().addListener(this::onIgnorePatternListSelectionChanged);
        listView.setCellFactory(param -> new TextFieldListCell<>(new DefaultStringConverter()) {
            @Override
            public void commitEdit(String newValue) {
                if (!isEditing()) {
                    return;
                }
                if (Preferences.validatePathPattern(newValue)) {
                    super.commitEdit(newValue);
                }
            }
        });

        listView.setOnEditCancel(this::onCellEditCancel);
        listView.getItems().addListener(this::onListChange);
    }

    public void onKeyReleased(KeyEvent keyEvent) {
        if (keyEvent.getCode() == KeyCode.DELETE || (keyEvent.getCode() == KeyCode.BACK_SPACE && keyEvent.isMetaDown())) {
            deleteSelectedItems();
        }
        keyEvent.consume();
    }

    public void onMinusButtonAction(ActionEvent actionEvent) {
        deleteSelectedItems();
        actionEvent.consume();
    }

    public void onPlusButtonAction(ActionEvent actionEvent) {
        var items = listView.getItems();
        items.add("glob:");
        var newItemIndex = items.size() - 1;
        // TODO the call to layout is a workaround for a javafx bug https://stackoverflow.com/a/32701064
        listView.layout();
        listView.edit(newItemIndex);
        listView.getSelectionModel().select(newItemIndex);

        plusButton.setDisable(true);
        actionEvent.consume();
    }


    private void onListChange(@SuppressWarnings("TypeParameterExtendsFinalClass") ListChangeListener.Change<? extends String> change) {
        changeHandler.accept(listView.getItems());
    }

    private void deleteSelectedItems() {
        var selectedItems = listView.getSelectionModel().getSelectedItems();
        listView.getItems().removeAll(selectedItems);
    }

    private void onCellEditCancel(ListView.EditEvent<String> editEvent) {
        var items = listView.getItems();
        items.remove("");
        plusButton.setDisable(false);
        editEvent.consume();
    }

    private void onIgnorePatternListSelectionChanged(@SuppressWarnings("TypeParameterExtendsFinalClass") ListChangeListener.Change<? extends String> change) {
        minusButton.setDisable(change.getList().isEmpty());
    }
}
