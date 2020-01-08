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

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.application.Platform.runLater;

public final class MainScreenControllerImpl implements MainScreenController {
    private final ApplicationLifecycleControl applicationLifecycleControl;
    private final Restarter restarter;
    private final ModalDialogFactory modalDialogFactory;
    private final Node folderSelectionPane;
    private final UploadPaneController uploadPaneController;
    private final Node uploadPane;

    public MenuBar menuBar;
    public MenuItem menuItemLogout;
    public MenuItem menuItemStopUpload;
    public VBox root;
    public MenuItem menuItemPreferences;
    private ModalDialog preferencesDialog;

    @Inject
    public MainScreenControllerImpl(ApplicationLifecycleControl applicationLifecycleControl,
                                    FxmlContainerFactory fxmlContainerFactory,
                                    Restarter restarter,
                                    ModalDialogFactory modalDialogFactory) {
        this.applicationLifecycleControl = checkNotNull(applicationLifecycleControl);
        this.restarter = checkNotNull(restarter);
        this.modalDialogFactory = checkNotNull(modalDialogFactory);

        FxmlContainer folderSelectorFxmlContainer = fxmlContainerFactory.create("FolderSelector.fxml");
        FolderSelectorController folderSelectorController = folderSelectorFxmlContainer.controller();
        folderSelectorController.setFolderSelectedAction(this::onFolderSelected);
        folderSelectionPane = folderSelectorFxmlContainer.root();

        FxmlContainer uploadPaneFxmlContainer = fxmlContainerFactory.create("UploadPane.fxml");
        uploadPaneController = uploadPaneFxmlContainer.controller();
        uploadPane = uploadPaneFxmlContainer.root();
    }

    public void onMenuClose(ActionEvent actionEvent) {
        menuBar.setDisable(true);
        applicationLifecycleControl.initiateShutdown();
        actionEvent.consume();
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

    public void onPreferences(ActionEvent actionEvent) {
        if (preferencesDialog == null) {
            preferencesDialog = modalDialogFactory.create("Preferences", "PreferencesDialog.fxml");
        }
        preferencesDialog.show();
        actionEvent.consume();
    }

    private void onFolderSelected(Path path) {
        ObservableList<Node> children = root.getChildren();
        children.remove(folderSelectionPane);
        children.add(uploadPane);

        menuItemStopUpload.setDisable(false);
        uploadPaneController.startUpload(path)
                .whenComplete((v, e) -> runLater(() -> menuItemStopUpload.setDisable(true)));
    }
}
