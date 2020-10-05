package net.yudichev.googlephotosupload.ui;

import javafx.event.ActionEvent;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseEvent;
import javafx.util.converter.IntegerStringConverter;
import net.yudichev.googlephotosupload.core.AddToAlbumMethod;
import net.yudichev.googlephotosupload.core.Preferences;
import net.yudichev.googlephotosupload.core.PreferencesManager;
import net.yudichev.jiotty.common.varstore.VarStore;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.core.ResourceBundleModule.RESOURCE_BUNDLE;
import static net.yudichev.googlephotosupload.ui.FatalStartupError.showFatalStartupError;
import static net.yudichev.jiotty.common.lang.Locks.inLock;

public final class PreferencesDialogController implements PreferencesManager {
    private static final String VAR_STORE_KEY = "preferences";
    private static final Pattern RELEVANT_DIR_DEPTH_PATTERN = Pattern.compile("(|[1-9]\\d{0,2})");
    private static final int DEFAULT_RELEVANT_DIR_DEPTH_LIMIT = 2;
    private final VarStore varStore;
    private final Provider<UploaderStrategyChoicePanelController> uploaderStrategyChoicePanelControllerProvider;
    private final Lock lock = new ReentrantLock();
    private final Provider<JavafxApplicationResources> javafxApplicationResourcesProvider;
    public TitledPane uploaderStrategyChoiceContainer;
    public PreferencePatternEditorController excludePanelController;
    public PreferencePatternEditorController includePanelController;
    public RadioButton relevantDirDepthTitleFullRadioButton;
    public RadioButton relevantDirDepthTitleLimitedRadioButton;
    public TextField relevantDirDepthTitleLimitTextField;
    private Preferences preferences;
    private TextFormatter<Integer> relevantDirDepthTitleLimitTextFieldFormatter;

    @Inject
    PreferencesDialogController(VarStore varStore,
                                Provider<UploaderStrategyChoicePanelController> uploaderStrategyChoicePanelControllerProvider,
                                Provider<JavafxApplicationResources> javafxApplicationResourcesProvider) {
        this.varStore = checkNotNull(varStore);
        this.uploaderStrategyChoicePanelControllerProvider = checkNotNull(uploaderStrategyChoicePanelControllerProvider);
        try {
            preferences = varStore.readValue(Preferences.class, VAR_STORE_KEY).orElseGet(() -> Preferences.builder().build());
        } catch (RuntimeException e) {
            showFatalStartupError(RESOURCE_BUNDLE.getString("preferencesLoadingFatalError"));
            throw e;
        }
        this.javafxApplicationResourcesProvider = checkNotNull(javafxApplicationResourcesProvider);
    }

    public void initialize() {
        var uploaderStrategyChoicePanelController = uploaderStrategyChoicePanelControllerProvider.get();
        uploaderStrategyChoiceContainer.setContent(uploaderStrategyChoicePanelController.getRoot());
        uploaderStrategyChoicePanelController.addSelectionChangeListener(this::onUploaderStrategyChange);

        inLock(lock, () -> {
            excludePanelController.initialise(preferences.scanExclusionGlobs(), this::onExcludeGlobsChanged);
            includePanelController.initialise(preferences.scanInclusionGlobs(), this::onIncludeGlobsChanged);
            preferences.addToAlbumStrategy().ifPresent(uploaderStrategyChoicePanelController::setSelection);

            //noinspection ReturnOfNull valid for this API
            relevantDirDepthTitleLimitTextFieldFormatter = new TextFormatter<>(
                    new IntegerStringConverter(),
                    preferences.relevantDirDepthLimit().orElse(null),
                    change -> RELEVANT_DIR_DEPTH_PATTERN.matcher(change.getText()).matches() ? change : null);
            relevantDirDepthTitleLimitTextFieldFormatter.valueProperty().addListener(
                    (observable, oldValue, newValue) -> onRelevantDirDepthTitleLimitChange(newValue));
            relevantDirDepthTitleLimitTextField.setTextFormatter(relevantDirDepthTitleLimitTextFieldFormatter);
            relevantDirDepthTitleFullRadioButton.setSelected(preferences.relevantDirDepthLimit().isEmpty());
            relevantDirDepthTitleLimitedRadioButton.setSelected(preferences.relevantDirDepthLimit().isPresent());
        });
    }

    private void onExcludeGlobsChanged(List<String> patterns) {
        inLock(lock, () -> {
            preferences = preferences.withScanExclusionGlobs(patterns);
            savePreferences();
        });
    }

    private void onIncludeGlobsChanged(List<String> patterns) {
        inLock(lock, () -> {
            preferences = preferences.withScanInclusionGlobs(patterns);
            savePreferences();
        });
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

    @Override
    public void update(Function<Preferences, Preferences> updater) {
        inLock(lock, () -> {
            preferences = updater.apply(preferences);
            savePreferences();
        });
    }

    private void savePreferences() {
        varStore.saveValue(VAR_STORE_KEY, preferences);
    }

    public void onPatternsDocumentationLinkAction(ActionEvent actionEvent) {
        javafxApplicationResourcesProvider.get().hostServices().showDocument(
                "https://docs.oracle.com/en/java/javase/15/docs/api/java.base/java/nio/file/FileSystem.html#getPathMatcher(java.lang.String)");
        actionEvent.consume();
    }

    public void onRelevantDirDepthTypeSelectionChange(ActionEvent actionEvent) {
        if (relevantDirDepthTitleFullRadioButton.isSelected()) {
            relevantDirDepthTitleLimitTextFieldFormatter.setValue(null);
        } else {
            relevantDirDepthTitleLimitTextField.requestFocus();
            if (relevantDirDepthTitleLimitTextFieldFormatter.getValue() == null) {
                relevantDirDepthTitleLimitTextFieldFormatter.setValue(DEFAULT_RELEVANT_DIR_DEPTH_LIMIT);
            }
        }
        actionEvent.consume();
    }

    public void onRelevantDirDepthTitleLimitChange(Integer newValue) {
        inLock(lock, () -> {
            if (newValue == null && relevantDirDepthTitleLimitedRadioButton.isSelected()) {
                relevantDirDepthTitleLimitTextFieldFormatter.setValue(DEFAULT_RELEVANT_DIR_DEPTH_LIMIT);
            } else {
                preferences = preferences.withRelevantDirDepthLimit(Optional.ofNullable(newValue));
                savePreferences();
            }
        });
    }

    public void onRelevantDirDepthHelp(MouseEvent mouseEvent) {
        javafxApplicationResourcesProvider.get().hostServices().showDocument(
                "https://github.com/ylexus/jiotty-photos-uploader/wiki#configurable-directory-depth-level");
        mouseEvent.consume();
    }
}
