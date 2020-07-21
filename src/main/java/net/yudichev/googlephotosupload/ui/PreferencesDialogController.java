package net.yudichev.googlephotosupload.ui;

import javafx.event.ActionEvent;
import javafx.scene.control.TitledPane;
import net.yudichev.googlephotosupload.core.AddToAlbumMethod;
import net.yudichev.googlephotosupload.core.Preferences;
import net.yudichev.googlephotosupload.core.PreferencesManager;
import net.yudichev.jiotty.common.varstore.VarStore;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.jiotty.common.lang.Locks.inLock;

public final class PreferencesDialogController implements PreferencesManager {
    private static final String VAR_STORE_KEY = "preferences";
    private final VarStore varStore;
    private final Provider<UploaderStrategyChoicePanelController> uploaderStrategyChoicePanelControllerProvider;
    private final Lock lock = new ReentrantLock();
    private final Provider<JavafxApplicationResources> javafxApplicationResourcesProvider;
    public TitledPane uploaderStrategyChoiceContainer;
    public PreferencePatternEditorController excludePanelController;
    public PreferencePatternEditorController includePanelController;
    private Preferences preferences;

    @Inject
    PreferencesDialogController(VarStore varStore,
                                Provider<UploaderStrategyChoicePanelController> uploaderStrategyChoicePanelControllerProvider,
                                Provider<JavafxApplicationResources> javafxApplicationResourcesProvider) {
        this.varStore = checkNotNull(varStore);
        this.uploaderStrategyChoicePanelControllerProvider = checkNotNull(uploaderStrategyChoicePanelControllerProvider);
        preferences = varStore.readValue(Preferences.class, VAR_STORE_KEY).orElseGet(() -> Preferences.builder().build());
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
                "https://docs.oracle.com/en/java/javase/14/docs/api/java.base/java/nio/file/FileSystem.html#getPathMatcher(java.lang.String)");
        actionEvent.consume();
    }
}
