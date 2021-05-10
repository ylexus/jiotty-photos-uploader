package net.yudichev.googlephotosupload.ui;

import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javafx.util.converter.IntegerStringConverter;
import net.yudichev.googlephotosupload.core.*;
import net.yudichev.jiotty.common.varstore.VarStore;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.core.ResourceBundleModule.RESOURCE_BUNDLE;
import static net.yudichev.googlephotosupload.ui.FatalStartupError.showFatalStartupError;
import static net.yudichev.jiotty.common.lang.Locks.inLock;

public final class PreferencesDialogController implements PreferencesManager {
    static final String CUSTOM_CREDENTIALS_HELP_URL = "https://github.com/ylexus/jiotty-photos-uploader/wiki#using-your-own-google-api-client-secret";

    private static final String VAR_STORE_KEY = "preferences";
    private static final Pattern RELEVANT_DIR_DEPTH_PATTERN = Pattern.compile("(|[1-9]\\d{0,2})");
    private static final int DEFAULT_RELEVANT_DIR_DEPTH_LIMIT = 2;
    private final VarStore varStore;
    private final Provider<UploaderStrategyChoicePanelController> uploaderStrategyChoicePanelControllerProvider;
    private final CustomCredentialsManager customCredentialsManager;
    private final Restarter restarter;
    private final ResourceBundle resourceBundle;
    private final Lock lock = new ReentrantLock();
    private final Provider<JavafxApplicationResources> javafxApplicationResourcesProvider;
    public TitledPane uploaderStrategyChoiceContainer;
    public PreferencePatternEditorController excludePanelController;
    public PreferencePatternEditorController includePanelController;
    public RadioButton relevantDirDepthTitleFullRadioButton;
    public RadioButton relevantDirDepthTitleLimitedRadioButton;
    public TextField relevantDirDepthTitleLimitTextField;
    public TextField albumDelimiterTextField;
    public Label albumDelimiterExampleLabel;
    public TitledPane customCredentialsPane;
    public RadioButton customCredentialsUseStandardRadioButton;
    public RadioButton customCredentialsUseCustomRadioButton;
    public Button customCredentialsBrowseButton;
    public Hyperlink logoutHyperlink;
    private Runnable selfCloseAction;
    private Preferences preferences;
    private TextFormatter<Integer> relevantDirDepthTitleLimitTextFieldFormatter;
    private SepiaToneEffectAnimatedNode flashingCustomCredentialsPane;

    @Inject
    PreferencesDialogController(VarStore varStore,
                                Provider<UploaderStrategyChoicePanelController> uploaderStrategyChoicePanelControllerProvider,
                                Provider<JavafxApplicationResources> javafxApplicationResourcesProvider,
                                CustomCredentialsManager customCredentialsManager,
                                Restarter restarter,
                                ResourceBundle resourceBundle) {
        this.varStore = checkNotNull(varStore);
        this.uploaderStrategyChoicePanelControllerProvider = checkNotNull(uploaderStrategyChoicePanelControllerProvider);
        this.customCredentialsManager = checkNotNull(customCredentialsManager);
        this.restarter = checkNotNull(restarter);
        this.resourceBundle = checkNotNull(resourceBundle);
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

            albumDelimiterTextField.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().length() > 10 ? null : change));
            albumDelimiterTextField.setText(preferences.albumDelimiter());
            updateAlbumDelimiterExampleLabel(preferences.albumDelimiter());
            albumDelimiterTextField.textProperty().addListener(this::onAlbumDelimiterChanged);
            relevantDirDepthTitleLimitTextFieldFormatter = new TextFormatter<>(
                    new IntegerStringConverter(),
                    preferences.relevantDirDepthLimit().orElse(null),
                    change -> RELEVANT_DIR_DEPTH_PATTERN.matcher(change.getText()).matches() ? change : null);
            relevantDirDepthTitleLimitTextFieldFormatter.valueProperty().addListener(
                    (observable, oldValue, newValue) -> onRelevantDirDepthTitleLimitChange(newValue));
            relevantDirDepthTitleLimitTextField.setTextFormatter(relevantDirDepthTitleLimitTextFieldFormatter);
            relevantDirDepthTitleFullRadioButton.setSelected(preferences.relevantDirDepthLimit().isEmpty());
            relevantDirDepthTitleLimitedRadioButton.setSelected(preferences.relevantDirDepthLimit().isPresent());

            customCredentialsUseStandardRadioButton.setSelected(!preferences.useCustomCredentials());
            customCredentialsUseCustomRadioButton.setSelected(preferences.useCustomCredentials());
            customCredentialsUpdateBrowseButtonDisabled();
            flashingCustomCredentialsPane = new SepiaToneEffectAnimatedNode(customCredentialsPane, 4);

            refreshLogoutHyperlink();
        });
    }

    public void setSelfCloseAction(Runnable selfCloseAction) {
        this.selfCloseAction = checkNotNull(selfCloseAction);
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
                "https://docs.oracle.com/en/java/javase/14/docs/api/java.base/java/nio/file/FileSystem.html#getPathMatcher(java.lang.String)");
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

    public void onCustomCredentialsHelp(MouseEvent mouseEvent) {
        javafxApplicationResourcesProvider.get().hostServices().showDocument(CUSTOM_CREDENTIALS_HELP_URL);
        mouseEvent.consume();
    }

    public void focusOnCustomCredentials() {
        customCredentialsPane.requestFocus();
        flashingCustomCredentialsPane.show();
    }

    public void onCustomCredentialsSelectionChange(ActionEvent actionEvent) {
        customCredentialsUpdateBrowseButtonDisabled();
        inLock(lock, () -> {
            if (customCredentialsUseCustomRadioButton.isSelected()) {
                if (!customCredentialsManager.configuredToUseCustomCredentials()) {
                    var fileSelected = browseForCustomCredentialsFile();
                    if (!fileSelected) {
                        customCredentialsUseCustomRadioButton.setSelected(false);
                        customCredentialsUseStandardRadioButton.setSelected(true);
                        customCredentialsUpdateBrowseButtonDisabled();
                    }
                }
            } else {
                customCredentialsManager.deleteCustomCredentials();
            }
            inLock(lock, () -> {
                preferences = preferences.withUseCustomCredentials(customCredentialsUseCustomRadioButton.isSelected());
                savePreferences();
            });
            refreshLogoutHyperlink();
        });
        actionEvent.consume();
    }

    private void customCredentialsUpdateBrowseButtonDisabled() {
        customCredentialsBrowseButton.setDisable(customCredentialsUseStandardRadioButton.isSelected());
    }

    public void onCustomCredentialsBrowseButtonAction(ActionEvent actionEvent) {
        browseForCustomCredentialsFile();
        actionEvent.consume();
    }

    private boolean browseForCustomCredentialsFile() {
        var fileChooser = new FileChooser();
        fileChooser.setTitle(resourceBundle.getString("preferencesCustomCredentialsFileChooserTitle"));
        var file = fileChooser.showOpenDialog(customCredentialsUseCustomRadioButton.getScene().getWindow());
        if (file != null) {
            inLock(lock, () -> {
                preferences = preferences.withUseCustomCredentials(true);
                savePreferences();
            });
            customCredentialsManager.saveCustomCredentials(file.toPath());
            refreshLogoutHyperlink();
            return true;
        }
        return false;
    }

    public void onLogoutHyperlinkClicked(ActionEvent actionEvent) {
        selfCloseAction.run();
        restarter.initiateLogoutAndRestart();
        actionEvent.consume();
    }

    @SuppressWarnings("TypeParameterExtendsFinalClass")
    private void onAlbumDelimiterChanged(ObservableValue<? extends String> observable, String oldValue, String newValue) {
        updateAlbumDelimiterExampleLabel(newValue);
        inLock(lock, () -> {
            preferences = preferences.withAlbumDelimiter(newValue);
            savePreferences();
        });
    }

    private void updateAlbumDelimiterExampleLabel(String newValue) {
        albumDelimiterExampleLabel.setText(
                String.format(resourceBundle.getString("preferencesDialogAlbumDelimiterExampleLabel"), newValue, newValue, newValue));
    }

    private void refreshLogoutHyperlink() {
        logoutHyperlink.setVisible(!customCredentialsManager.usedCredentialsMatchConfigured());
    }
}
