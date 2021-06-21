package net.yudichev.googlephotosupload.ui;

import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import net.yudichev.googlephotosupload.core.*;
import net.yudichev.jiotty.common.varstore.VarStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.core.ResourceBundleModule.RESOURCE_BUNDLE;
import static net.yudichev.googlephotosupload.ui.FatalStartupError.showFatalStartupError;
import static net.yudichev.jiotty.common.lang.Locks.inLock;

public final class PreferencesDialogController implements PreferencesManager {
    static final String CUSTOM_CREDENTIALS_HELP_URL = "https://github.com/ylexus/jiotty-photos-uploader/wiki#using-your-own-google-api-client-secret";

    private static final String VAR_STORE_KEY = "preferences";
    private final VarStore varStore;
    private final Provider<UploaderStrategyChoicePanelController> uploaderStrategyChoicePanelControllerProvider;
    private final Restarter restarter;
    private final ResourceBundle resourceBundle;
    private final Lock lock = new ReentrantLock();
    private final Provider<JavafxApplicationResources> javafxApplicationResourcesProvider;
    private final AlbumDelimiter albumDelimiter;
    private final CustomCredentials customCredentials;
    private final RelevantDir relevantDir;
    private final DriveSpace driveSpace;

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

    public RadioButton driveSpacePercentageRadioButton;
    public TextField driveSpacePercentageTextField;
    public RadioButton driveSpaceFreeSpaceRadioButton;
    public TextField driveSpaceFreeSpaceRadioTextField;
    public RadioButton driveSpaceDisabledRadioButton;

    private Preferences preferences;

    @Inject
    PreferencesDialogController(VarStore varStore,
                                Provider<UploaderStrategyChoicePanelController> uploaderStrategyChoicePanelControllerProvider,
                                Provider<JavafxApplicationResources> javafxApplicationResourcesProvider,
                                CustomCredentialsManager customCredentialsManager,
                                Restarter restarter,
                                ResourceBundle resourceBundle) {
        this.varStore = checkNotNull(varStore);
        this.uploaderStrategyChoicePanelControllerProvider = checkNotNull(uploaderStrategyChoicePanelControllerProvider);
        customCredentials = new CustomCredentials(customCredentialsManager);
        albumDelimiter = new AlbumDelimiter();
        relevantDir = new RelevantDir();
        driveSpace = new DriveSpace();
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

            albumDelimiter.initialise();
            relevantDir.initialise();
            driveSpace.initialise();
            customCredentials.initialise();
        });
    }

    public void setSelfCloseAction(Runnable selfCloseAction) {
        customCredentials.setSelfCloseAction(selfCloseAction);
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

    public void onPatternsDocumentationLinkAction(ActionEvent actionEvent) {
        javafxApplicationResourcesProvider.get().hostServices().showDocument(
                "https://docs.oracle.com/en/java/javase/14/docs/api/java.base/java/nio/file/FileSystem.html#getPathMatcher(java.lang.String)");
        actionEvent.consume();
    }

    public void onRelevantDirDepthTypeSelectionChange(ActionEvent actionEvent) {
        relevantDir.onDepthTypeSelectionChange();
        actionEvent.consume();
    }


    public void onDriveSpaceSelectionChange(ActionEvent actionEvent) {
        driveSpace.onSelectionChange();
        actionEvent.consume();
    }

    public void onRelevantDirDepthHelp(MouseEvent mouseEvent) {
        relevantDir.onDepthHelp();
        mouseEvent.consume();
    }

    public void onCustomCredentialsHelp(MouseEvent mouseEvent) {
        customCredentials.onHelp();
        mouseEvent.consume();
    }

    public void focusOnCustomCredentials() {
        customCredentials.focus();
    }

    public void onCustomCredentialsSelectionChange(ActionEvent actionEvent) {
        customCredentials.onSelectionChange();
        actionEvent.consume();
    }

    public void onCustomCredentialsBrowseButtonAction(ActionEvent actionEvent) {
        customCredentials.browseForFile();
        actionEvent.consume();
    }

    public void onLogoutHyperlinkClicked(ActionEvent actionEvent) {
        customCredentials.onLogoutHyperlinkClicked();
        actionEvent.consume();
    }

    private void savePreferences() {
        varStore.saveValue(VAR_STORE_KEY, preferences);
    }

    private final class CustomCredentials {
        private final CustomCredentialsManager customCredentialsManager;
        private Runnable selfCloseAction;
        private SepiaToneEffectAnimatedNode flashingCustomCredentialsPane;

        CustomCredentials(CustomCredentialsManager customCredentialsManager) {
            this.customCredentialsManager = checkNotNull(customCredentialsManager);
        }

        public void initialise() {
            customCredentialsUseStandardRadioButton.setSelected(!preferences.useCustomCredentials());
            customCredentialsUseCustomRadioButton.setSelected(preferences.useCustomCredentials());
            customCredentialsUpdateBrowseButtonDisabled();
            flashingCustomCredentialsPane = new SepiaToneEffectAnimatedNode(customCredentialsPane, 4);

            customCredentials.refreshLogoutHyperlink();
        }

        public void onSelectionChange() {
            customCredentialsUpdateBrowseButtonDisabled();
            inLock(lock, () -> {
                if (customCredentialsUseCustomRadioButton.isSelected()) {
                    if (!customCredentialsManager.configuredToUseCustomCredentials()) {
                        var fileSelected = browseForFile();
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
        }

        public boolean browseForFile() {
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

        public void focus() {
            customCredentialsPane.requestFocus();
            flashingCustomCredentialsPane.show();
        }

        public void onHelp() {
            javafxApplicationResourcesProvider.get().hostServices().showDocument(CUSTOM_CREDENTIALS_HELP_URL);
        }

        public void onLogoutHyperlinkClicked() {
            selfCloseAction.run();
            restarter.initiateLogoutAndRestart();
        }

        private void customCredentialsUpdateBrowseButtonDisabled() {
            customCredentialsBrowseButton.setDisable(customCredentialsUseStandardRadioButton.isSelected());
        }

        private void refreshLogoutHyperlink() {
            logoutHyperlink.setVisible(!customCredentialsManager.usedCredentialsMatchConfigured());
        }

        public void setSelfCloseAction(Runnable selfCloseAction) {
            this.selfCloseAction = checkNotNull(selfCloseAction);
        }
    }

    private final class AlbumDelimiter {

        public void initialise() {
            albumDelimiterTextField.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().length() > 10 ? null : change));
            albumDelimiterTextField.setText(preferences.albumDelimiter());
            updateAlbumDelimiterExampleLabel(preferences.albumDelimiter());
            albumDelimiterTextField.textProperty().addListener(this::onAlbumDelimiterChanged);
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
    }

    private final class RelevantDir {
        private static final int DEFAULT_RELEVANT_DIR_DEPTH_LIMIT = 2;
        private final Pattern RELEVANT_DIR_DEPTH_PATTERN = Pattern.compile("[1-9]\\d{0,2}");
        private TextFormatter<Integer> relevantDirDepthTitleLimitTextFieldFormatter;

        public void initialise() {
            relevantDirDepthTitleLimitTextFieldFormatter = new TextFormatter<>(
                    new IntegerStringConverter(),
                    preferences.relevantDirDepthLimit().orElse(null),
                    change -> change.getControlNewText().isEmpty() || change.getControlNewText().equals(change.getControlText())
                            || RELEVANT_DIR_DEPTH_PATTERN.matcher(change.getControlNewText()).matches() ? change : null);
            relevantDirDepthTitleLimitTextFieldFormatter.valueProperty().addListener(
                    (observable, oldValue, newValue) -> onRelevantDirDepthTitleLimitChange(newValue));
            relevantDirDepthTitleLimitTextField.setTextFormatter(relevantDirDepthTitleLimitTextFieldFormatter);
            relevantDirDepthTitleFullRadioButton.setSelected(preferences.relevantDirDepthLimit().isEmpty());
            relevantDirDepthTitleLimitedRadioButton.setSelected(preferences.relevantDirDepthLimit().isPresent());
        }

        public void onDepthTypeSelectionChange() {
            if (relevantDirDepthTitleFullRadioButton.isSelected()) {
                // this changes the value and triggers saving prefs
                relevantDirDepthTitleLimitTextFieldFormatter.setValue(null);
            } else {
                relevantDirDepthTitleLimitTextField.requestFocus();
                if (relevantDirDepthTitleLimitTextFieldFormatter.getValue() == null) {
                    relevantDirDepthTitleLimitTextFieldFormatter.setValue(DEFAULT_RELEVANT_DIR_DEPTH_LIMIT);
                }
            }
        }

        private void onRelevantDirDepthTitleLimitChange(@Nullable Integer newValue) {
            if (newValue == null && relevantDirDepthTitleLimitedRadioButton.isSelected()) {
                relevantDirDepthTitleLimitTextFieldFormatter.setValue(DEFAULT_RELEVANT_DIR_DEPTH_LIMIT);
            } else {
                inLock(lock, () -> {
                    preferences = preferences.withRelevantDirDepthLimit(Optional.ofNullable(newValue));
                    savePreferences();
                });
            }
        }

        public void onDepthHelp() {
            javafxApplicationResourcesProvider.get().hostServices().showDocument(
                    "https://github.com/ylexus/jiotty-photos-uploader/wiki#configurable-directory-depth-level");
        }
    }

    private final class DriveSpace {
        private Option<Double> percentageOption;
        private Option<Integer> freeSpaceOption;

        public void initialise() {
            percentageOption = new PercentageOption();
            freeSpaceOption = new FreeSpaceOption();
            preferences.failOnDriveSpace().ifPresentOrElse(
                    failOnDriveSpaceOption -> {
                        driveSpacePercentageRadioButton.setSelected(failOnDriveSpaceOption.maxUsedPercentage().isPresent());
                        driveSpaceFreeSpaceRadioButton.setSelected(failOnDriveSpaceOption.minFreeMegabytes().isPresent());
                    },
                    () -> driveSpaceDisabledRadioButton.setSelected(true));
        }

        public void onSelectionChange() {
            if (driveSpaceDisabledRadioButton.isSelected()) {
                percentageOption.deactivate();
                freeSpaceOption.deactivate();
                inLock(lock, () -> {
                    preferences = preferences.withFailOnDriveSpace(Optional.empty());
                    savePreferences();
                });
            } else {
                if (driveSpacePercentageRadioButton.isSelected()) {
                    percentageOption.activate();
                    freeSpaceOption.deactivate();
                } else {
                    freeSpaceOption.activate();
                    percentageOption.deactivate();
                }
            }
        }

        private class Option<T> {
            private final TextFormatter<T> textFormatter;
            private final RadioButton radioButton;
            @Nonnull
            private final TextField textField;
            private final T defaultValue;
            private final Function<T, FailOnDriveSpaceOption> configValueFactory;

            private Option(RadioButton radioButton,
                           TextField textField,
                           T defaultValue,
                           StringConverter<T> converter,
                           Function<FailOnDriveSpaceOption, Optional<T>> preferencesValueExtractor,
                           Predicate<String> valueValidator,
                           Function<T, FailOnDriveSpaceOption> configValueFactory) {
                this.radioButton = radioButton;
                this.textField = textField;
                this.defaultValue = defaultValue;
                this.configValueFactory = configValueFactory;
                textFormatter = new TextFormatter<>(
                        converter,
                        preferences.failOnDriveSpace().flatMap(preferencesValueExtractor).orElse(null),
                        change -> {
                            var controlNewText = change.getControlNewText();
                            if (controlNewText.isEmpty() || controlNewText.equals(change.getControlText())) {
                                return change;
                            }
                            try {
                                return valueValidator.test(controlNewText) ? change : null;
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        });
                textFormatter.valueProperty().addListener((observable, oldValue, newValue) -> onTextChange(newValue));
                textField.setTextFormatter(textFormatter);
            }

            private void onTextChange(@Nullable T newValue) {
                if (newValue == null) {
                    if (radioButton.isSelected()) {
                        textFormatter.setValue(defaultValue);
                    }
                } else {
                    inLock(lock, () -> {
                        preferences = preferences.withFailOnDriveSpace(configValueFactory.apply(newValue));
                        savePreferences();
                    });
                }
            }

            public void deactivate() {
                textFormatter.setValue(null);
            }

            public void activate() {
                textField.requestFocus();
                if (textFormatter.getValue() == null) {
                    textFormatter.setValue(defaultValue);
                }
            }
        }

        private final class PercentageOption extends Option<Double> {
            private static final double DEFAULT_DRIVE_SPACE_PERCENTAGE = 98;

            private PercentageOption() {
                super(driveSpacePercentageRadioButton,
                        driveSpacePercentageTextField,
                        DEFAULT_DRIVE_SPACE_PERCENTAGE,
                        new DoubleStringConverter(),
                        FailOnDriveSpaceOption::maxUsedPercentage,
                        controlNewText -> {
                            var percentage = Double.parseDouble(controlNewText);
                            return percentage > 0 && percentage < 100;
                        },
                        newValue -> FailOnDriveSpaceOption.builder()
                                .setMaxUsedPercentage(newValue)
                                .build());
            }
        }

        private final class FreeSpaceOption extends Option<Integer> {
            private static final int DEFAULT_DRIVE_SPACE_FREE_SPACE_MB = 500;

            private FreeSpaceOption() {
                super(driveSpaceFreeSpaceRadioButton,
                        driveSpaceFreeSpaceRadioTextField,
                        DEFAULT_DRIVE_SPACE_FREE_SPACE_MB,
                        new IntegerStringConverter(),
                        FailOnDriveSpaceOption::minFreeMegabytes,
                        controlNewText -> Integer.parseInt(controlNewText) > 0,
                        newValue -> FailOnDriveSpaceOption.builder()
                                .setMinFreeMegabytes(newValue)
                                .build());
            }
        }
    }
}
