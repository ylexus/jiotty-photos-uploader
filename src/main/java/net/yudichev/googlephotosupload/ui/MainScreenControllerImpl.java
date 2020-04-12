package net.yudichev.googlephotosupload.ui;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;
import net.yudichev.googlephotosupload.core.Restarter;
import net.yudichev.jiotty.common.app.ApplicationLifecycleControl;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.ResourceBundle;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.application.Platform.runLater;
import static javafx.stage.Modality.APPLICATION_MODAL;

@SuppressWarnings("ClassWithTooManyFields") // OK for a FX controller
public final class MainScreenControllerImpl implements MainScreenController {
    private final ApplicationLifecycleControl applicationLifecycleControl;
    private final Restarter restarter;
    private final DialogFactory dialogFactory;
    private final PlatformSpecificMenu platformSpecificMenu;
    private final ResourceBundle resourceBundle;
    private final Node folderSelectionPane;
    private final UploadPaneController uploadPaneController;
    private final Node uploadPane;

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
                                    ResourceBundle resourceBundle) {
        this.applicationLifecycleControl = checkNotNull(applicationLifecycleControl);
        this.restarter = checkNotNull(restarter);
        this.dialogFactory = checkNotNull(dialogFactory);
        this.platformSpecificMenu = checkNotNull(platformSpecificMenu);
        this.resourceBundle = checkNotNull(resourceBundle);

        FxmlContainer folderSelectorFxmlContainer = fxmlContainerFactory.create("FolderSelector.fxml");
        FolderSelectorController folderSelectorController = folderSelectorFxmlContainer.controller();
        folderSelectorController.setFolderSelectedAction(this::onFolderSelected);
        folderSelectionPane = folderSelectorFxmlContainer.root();

        FxmlContainer uploadPaneFxmlContainer = fxmlContainerFactory.create("UploadPane.fxml");
        uploadPaneController = uploadPaneFxmlContainer.controller();
        uploadPane = uploadPaneFxmlContainer.root();
    }

    public void initialize() {
        platformSpecificMenu.initialize(menuBar);
        platformSpecificMenu.setOnExitAction(this::onMenuExit);
        platformSpecificMenu.setOnPreferencesAction(this::onPreferences);
        platformSpecificMenu.setOnAboutAction(this::onAbout);
    }

    public void onMenuActionLogout(ActionEvent actionEvent) {
        restarter.initiateLogoutAndRestart();
        menuItemLogout.setDisable(true);
        actionEvent.consume();
    }

    @Override
    public void toFolderSelectionMode() {
        runLater(() -> {
            ObservableList<Node> children = root.getChildren();
            children.remove(uploadPane);
            if (!children.contains(folderSelectionPane)) {
                children.add(folderSelectionPane);
            }
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
                    stage -> stage.initModality(APPLICATION_MODAL));
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
                    resourceBundle.getString("mainScreenPreferencesDialogTitle"),
                    "PreferencesDialog.fxml",
                    dialog -> {
                        dialog.initModality(APPLICATION_MODAL);
                        dialog.setMinHeight(500);
                        dialog.setMinWidth(500);
                    });
        }
        preferencesDialog.show();
        actionEvent.consume();
    }

    private void onFolderSelected(Path path, boolean resume) {
        ObservableList<Node> children = root.getChildren();
        children.remove(folderSelectionPane);
        children.add(uploadPane);

        menuItemStopUpload.setDisable(false);
        uploadPaneController.startUpload(path, resume)
                .whenComplete((v, e) -> runLater(() -> menuItemStopUpload.setDisable(true)));
    }
}
