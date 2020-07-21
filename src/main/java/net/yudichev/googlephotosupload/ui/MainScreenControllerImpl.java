package net.yudichev.googlephotosupload.ui;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;
import net.yudichev.googlephotosupload.core.AddToAlbumMethod;
import net.yudichev.googlephotosupload.core.PreferencesManager;
import net.yudichev.googlephotosupload.core.Restarter;
import net.yudichev.jiotty.common.app.ApplicationLifecycleControl;

import javax.inject.Inject;
import javax.inject.Provider;
import java.nio.file.Path;
import java.util.ResourceBundle;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.application.Platform.runLater;
import static javafx.stage.Modality.APPLICATION_MODAL;
import static javafx.stage.StageStyle.UTILITY;
import static net.yudichev.googlephotosupload.core.AddToAlbumMethod.WHILE_CREATING_ITEMS;

@SuppressWarnings("ClassWithTooManyFields") // OK for a FX controller
public final class MainScreenControllerImpl implements MainScreenController {
    private final ApplicationLifecycleControl applicationLifecycleControl;
    private final Restarter restarter;
    private final DialogFactory dialogFactory;
    private final PlatformSpecificMenu platformSpecificMenu;
    private final ResourceBundle resourceBundle;
    private final PreferencesManager preferencesManager;
    private final UploaderStrategyChoicePanelController uploaderStrategyChoicePanelController;
    private final Provider<JavafxApplicationResources> javafxApplicationResourcesProvider;
    private final Node folderSelectionPane;
    private final UploadPaneController uploadPaneController;
    private final Node uploadPane;
    private final FolderSelectorController folderSelectorController;
    public MenuBar menuBar;
    public MenuItem menuItemLogout;
    public MenuItem menuItemStopUpload;
    public VBox root;
    private Dialog preferencesDialog;
    private Dialog aboutDialog;

    @Inject
    public MainScreenControllerImpl(ApplicationLifecycleControl applicationLifecycleControl,
                                    FxmlContainerFactory fxmlContainerFactory,
                                    Restarter restarter,
                                    DialogFactory dialogFactory,
                                    PlatformSpecificMenu platformSpecificMenu,
                                    ResourceBundle resourceBundle,
                                    PreferencesManager preferencesManager,
                                    UploaderStrategyChoicePanelController uploaderStrategyChoicePanelController,
                                    Provider<JavafxApplicationResources> javafxApplicationResourcesProvider) {
        this.applicationLifecycleControl = checkNotNull(applicationLifecycleControl);
        this.restarter = checkNotNull(restarter);
        this.dialogFactory = checkNotNull(dialogFactory);
        this.platformSpecificMenu = checkNotNull(platformSpecificMenu);
        this.resourceBundle = checkNotNull(resourceBundle);
        this.preferencesManager = checkNotNull(preferencesManager);
        this.uploaderStrategyChoicePanelController = checkNotNull(uploaderStrategyChoicePanelController);
        this.javafxApplicationResourcesProvider = checkNotNull(javafxApplicationResourcesProvider);

        var folderSelectorFxmlContainer = fxmlContainerFactory.create("FolderSelector.fxml");
        folderSelectorController = folderSelectorFxmlContainer.controller();
        folderSelectorController.setFolderSelectedAction(this::onFolderSelected);
        folderSelectionPane = folderSelectorFxmlContainer.root();

        var uploadPaneFxmlContainer = fxmlContainerFactory.create("UploadPane.fxml");
        uploadPaneController = uploadPaneFxmlContainer.controller();
        uploadPane = uploadPaneFxmlContainer.root();
    }

    public void initialize() {
        platformSpecificMenu.initialize(menuBar);
        platformSpecificMenu.setOnExitAction(this::onMenuExit);
        platformSpecificMenu.setOnPreferencesAction(this::onPreferences);
        platformSpecificMenu.setOnAboutAction(this::onAbout);

        if (preferencesManager.get().addToAlbumStrategy().isEmpty()) {
            runLater(() -> {
                var dialog = new javafx.scene.control.Dialog<AddToAlbumMethod>();
                dialog.setTitle(resourceBundle.getString("mainScreenInitialUploadMethodDialogTitle"));
                dialog.initStyle(UTILITY);
                dialog.initModality(APPLICATION_MODAL);
                dialog.initOwner(javafxApplicationResourcesProvider.get().primaryStage());
                dialog.getDialogPane().setContent(uploaderStrategyChoicePanelController.getRoot());
                dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
                dialog.getDialogPane().getStylesheets().add("style.css");
                dialog.getDialogPane().setPrefWidth(700);

                uploaderStrategyChoicePanelController.setSelection(WHILE_CREATING_ITEMS);
                preferencesManager.update(preferences -> preferences.withAddToAlbumStrategy(WHILE_CREATING_ITEMS));
                var subscription = uploaderStrategyChoicePanelController.addSelectionChangeListener(method ->
                        preferencesManager.update(preferences -> preferences.withAddToAlbumStrategy(method)));

                dialog.showAndWait();
                subscription.close();
            });
        }
    }

    public void onMenuActionLogout(ActionEvent actionEvent) {
        restarter.initiateLogoutAndRestart();
        menuItemLogout.setDisable(true);
        actionEvent.consume();
    }

    @Override
    public void toFolderSelectionMode() {
        runLater(() -> {
            var children = root.getChildren();
            children.remove(uploadPane);
            if (!children.contains(folderSelectionPane)) {
                children.add(folderSelectionPane);
            }
            folderSelectorController.refresh();
            uploadPaneController.reset();

            menuItemLogout.setDisable(false);
        });
    }

    public void onStopUpload(ActionEvent actionEvent) {
        menuItemStopUpload.setDisable(true);
        uploadPaneController.stopUpload();
        actionEvent.consume();
    }

    private void onAbout(ActionEvent actionEvent) {
        if (aboutDialog == null) {
            aboutDialog = dialogFactory.create(
                    resourceBundle.getString("mainScreenAboutDialogTitle"),
                    "AboutDialog.fxml",
                    stage -> {
                        stage.initModality(APPLICATION_MODAL);
                        stage.setResizable(false);
                    });
        }
        aboutDialog.show();
        actionEvent.consume();

    }

    private void onMenuExit(ActionEvent actionEvent) {
        applicationLifecycleControl.initiateShutdown();
        actionEvent.consume();
    }

    private void onPreferences(ActionEvent actionEvent) {
        if (preferencesDialog == null) {
            preferencesDialog = dialogFactory.create(
                    resourceBundle.getString("preferencesDialogTitle"),
                    "PreferencesDialog.fxml",
                    dialog -> {
                        dialog.initModality(APPLICATION_MODAL);
                        dialog.setMinHeight(600);
                        dialog.setMinWidth(600);
                    });
        }
        preferencesDialog.show();
        actionEvent.consume();
    }

    private void onFolderSelected(Path path, boolean resume) {
        var children = root.getChildren();
        children.remove(folderSelectionPane);
        children.add(uploadPane);

        menuItemStopUpload.setDisable(false);
        uploadPaneController.startUpload(path, resume)
                .whenComplete((v, e) -> runLater(() -> menuItemStopUpload.setDisable(true)));
    }
}
