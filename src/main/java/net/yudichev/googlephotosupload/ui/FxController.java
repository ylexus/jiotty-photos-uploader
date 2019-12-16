package net.yudichev.googlephotosupload.ui;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import net.yudichev.googlephotosupload.core.Uploader;
import net.yudichev.jiotty.common.app.ApplicationLifecycleControl;

import javax.inject.Inject;
import java.io.File;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.application.Platform.runLater;

public final class FxController implements MainScreenController {
    private final Uploader uploader;
    private final ApplicationLifecycleControl applicationLifecycleControl;
    public VBox folderSelector;
    public VBox progressBox;
    public TextArea logArea;
    public GridPane progressPane;
    public MenuBar menuBar;
    public MenuItem menuItemLogout;

    @Inject
    public FxController(Uploader uploader,
                        ApplicationLifecycleControl applicationLifecycleControl) {
        this.uploader = checkNotNull(uploader);
        this.applicationLifecycleControl = checkNotNull(applicationLifecycleControl);
    }

    public void folderSelectorOnDragEnter(DragEvent event) {
        Dragboard dragboard = event.getDragboard();
        if (isSingleFolder(dragboard)) {
            folderSelector.setEffect(new DropShadow());
            folderSelector.setStyle("-fx-background-color: #6495ed80;");
        }
        event.consume();
    }

    public void folderSelectorOnDragExit(DragEvent event) {
        folderSelector.setEffect(null);
        folderSelector.setStyle(null);
        event.consume();
    }

    public void folderSelectorOnDragOver(DragEvent event) {
        if (isSingleFolder(event.getDragboard())) {
            event.acceptTransferModes(TransferMode.ANY);
        }
        event.consume();
    }

    public void folderSelectorOnDragDropped(DragEvent event) {
        Dragboard dragboard = event.getDragboard();
        if (isSingleFolder(dragboard)) {
            startUpload(dragboard.getFiles().get(0));
            event.setDropCompleted(true);
        } else {
            event.setDropCompleted(false);
        }
        event.consume();
    }

    public void onBrowseButtonClick(ActionEvent actionEvent) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select folder with photos");
        File file = directoryChooser.showDialog(folderSelector.getScene().getWindow());
        if (file != null) {
            startUpload(file);
        }
        actionEvent.consume();
    }

    public void onMenuClose(ActionEvent actionEvent) {
        menuBar.setDisable(true);
        applicationLifecycleControl.initiateShutdown();
        actionEvent.consume();
    }

    public void onMenuActionLogout(ActionEvent actionEvent) {
        // TODO implement
        actionEvent.consume();
    }

    @Override
    public void toFolderSelectionMode() {
        runLater(() -> {
            // TODO clean all contents
            folderSelector.setVisible(true);
            progressPane.setVisible(false);

            menuItemLogout.setDisable(false);
        });
    }

    @Override
    public void addProgressBox(Node node) {
        runLater(() -> progressBox.getChildren().add(node));
    }

    private void startUpload(File rootDirectory) {
        folderSelector.setVisible(false);
        progressPane.setVisible(true);
        menuItemLogout.setDisable(true);

        uploader.upload(rootDirectory.toPath())
                .whenComplete((aVoid, e) -> runLater(() -> {
                    //TODO use success/error color
                    //TODO where do we put warnings about empty albums?
                    //TODO Stop button
                    //TODO Logout button
                    if (e == null) {
                        logArea.setText("Total success, ladies and gentlemen!");
                    } else {
                        logArea.setText(String.format("Something went wrong: %s", e.getMessage()));
                    }
                }));
    }

    private boolean isSingleFolder(Dragboard dragboard) {
        return dragboard.hasFiles() && dragboard.getFiles().size() == 1 && dragboard.getFiles().get(0).isDirectory();
    }
}
