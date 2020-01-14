package net.yudichev.googlephotosupload.ui;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.converter.DefaultStringConverter;
import net.yudichev.googlephotosupload.core.Preferences;
import net.yudichev.googlephotosupload.core.PreferencesSupplier;
import net.yudichev.jiotty.common.varstore.VarStore;

import javax.inject.Inject;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.scene.control.SelectionMode.MULTIPLE;

public final class PreferencesDialogFxController implements PreferencesSupplier {
    private static final String VAR_STORE_KEY = "preferences";
    private final VarStore varStore;
    public ListView<String> listView;
    public Button plusButton;
    public Button minusButton;
    private volatile Preferences preferences;

    @Inject
    PreferencesDialogFxController(VarStore varStore) {
        this.varStore = checkNotNull(varStore);
        preferences = varStore.readValue(Preferences.class, VAR_STORE_KEY).orElseGet(() -> Preferences.builder().build());
    }

    public void initialize() {
        listView.getItems().addAll(preferences.scanExclusionPatterns());
        MultipleSelectionModel<String> selectionModel = listView.getSelectionModel();
        selectionModel.setSelectionMode(MULTIPLE);
        selectionModel.getSelectedItems().addListener(this::onIgnorePatternListSelectionChanged);
        listView.setCellFactory(param -> new TextFieldListCell<>(new DefaultStringConverter()) {
            @Override
            public void commitEdit(String newValue) {
                if (!isEditing()) {
                    return;
                }
                try {
                    Pattern.compile(newValue);
                    super.commitEdit(newValue);
                } catch (PatternSyntaxException ignored) {
                }
            }
        });

        listView.setOnEditCancel(this::onCellEditCancel);
        listView.getItems().addListener(this::onListChange);
    }

    @Override
    public Preferences get() {
        return preferences;
    }

    public void onPlusButtonAction(ActionEvent actionEvent) {
        ObservableList<String> items = listView.getItems();
        items.add("");
        int newItemIndex = items.size() - 1;
        // TODO the call to layout is a workaround for a javafx bug https://stackoverflow.com/a/32701064
        listView.layout();
        listView.edit(newItemIndex);
        listView.getSelectionModel().select(newItemIndex);

        plusButton.setDisable(true);
        actionEvent.consume();
    }

    public void onMinusButtonAction(ActionEvent actionEvent) {
        deleteSelectedItems();
        actionEvent.consume();
    }

    public void onKeyReleased(KeyEvent keyEvent) {
        if (keyEvent.getCode() == KeyCode.DELETE || (keyEvent.getCode() == KeyCode.BACK_SPACE && keyEvent.isMetaDown())) {
            deleteSelectedItems();
        }
        keyEvent.consume();
    }

    private void deleteSelectedItems() {
        ObservableList<String> selectedItems = listView.getSelectionModel().getSelectedItems();
        listView.getItems().removeAll(selectedItems);
    }

    private void onListChange(ListChangeListener.Change<? extends String> change) {
        preferences = Preferences.of(listView.getItems());
        varStore.saveValue(VAR_STORE_KEY, preferences);
    }

    private void onCellEditCancel(ListView.EditEvent<String> editEvent) {
        ObservableList<String> items = listView.getItems();
        items.remove("");
        plusButton.setDisable(false);
        editEvent.consume();
    }

    private void onIgnorePatternListSelectionChanged(ListChangeListener.Change<? extends String> change) {
        minusButton.setDisable(change.getList().isEmpty());
    }
}
