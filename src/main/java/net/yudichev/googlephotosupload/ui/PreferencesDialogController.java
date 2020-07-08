package net.yudichev.googlephotosupload.ui;

import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.converter.DefaultStringConverter;
import net.yudichev.googlephotosupload.core.AddToAlbumMethod;
import net.yudichev.googlephotosupload.core.Preferences;
import net.yudichev.googlephotosupload.core.PreferencesManager;
import net.yudichev.jiotty.common.varstore.VarStore;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.scene.control.SelectionMode.MULTIPLE;
import static net.yudichev.jiotty.common.lang.Locks.inLock;

public final class PreferencesDialogController implements PreferencesManager {
    private static final String VAR_STORE_KEY = "preferences";
    private final VarStore varStore;
    private final Provider<UploaderStrategyChoicePanelController> uploaderStrategyChoicePanelControllerProvider;
    private final Lock lock = new ReentrantLock();
    public ListView<String> listView;
    public Button plusButton;
    public Button minusButton;
    public TitledPane uploaderStrategyChoiceContainer;
    private Preferences preferences;

    @Inject
    PreferencesDialogController(VarStore varStore,
                                Provider<UploaderStrategyChoicePanelController> uploaderStrategyChoicePanelControllerProvider) {
        this.varStore = checkNotNull(varStore);
        this.uploaderStrategyChoicePanelControllerProvider = checkNotNull(uploaderStrategyChoicePanelControllerProvider);
        preferences = varStore.readValue(Preferences.class, VAR_STORE_KEY).orElseGet(() -> Preferences.builder().build());
    }

    public void initialize() {
        var uploaderStrategyChoicePanelController = uploaderStrategyChoicePanelControllerProvider.get();
        uploaderStrategyChoiceContainer.setContent(uploaderStrategyChoicePanelController.getRoot());
        uploaderStrategyChoicePanelController.addSelectionChangeListener(this::onUploaderStrategyChange);

        inLock(lock, () -> {
            listView.getItems().addAll(preferences.scanExclusionPatterns());
            preferences.addToAlbumStrategy().ifPresent(uploaderStrategyChoicePanelController::setSelection);
        });

        var selectionModel = listView.getSelectionModel();
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

    private void onUploaderStrategyChange(AddToAlbumMethod strategy) {
        inLock(lock, () -> {
            preferences = preferences.withAddToAlbumStrategy(strategy);
            savePreferences();
        });
    }

    @Override
    public Preferences get() {
        return inLock(lock, () -> preferences);
    }

    public void onPlusButtonAction(ActionEvent actionEvent) {
        var items = listView.getItems();
        items.add("");
        var newItemIndex = items.size() - 1;
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

    @Override
    public void update(Function<Preferences, Preferences> updater) {
        inLock(lock, () -> {
            preferences = updater.apply(preferences);
            savePreferences();
        });
    }

    private void deleteSelectedItems() {
        var selectedItems = listView.getSelectionModel().getSelectedItems();
        listView.getItems().removeAll(selectedItems);
    }

    private void onListChange(@SuppressWarnings("TypeParameterExtendsFinalClass") ListChangeListener.Change<? extends String> change) {
        inLock(lock, () -> {
            preferences = preferences.withScanExclusionPatterns(listView.getItems());
            savePreferences();
        });
    }

    private void savePreferences() {
        varStore.saveValue(VAR_STORE_KEY, preferences);
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
