package net.yudichev.googlephotosupload.ui;

import javafx.event.ActionEvent;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public final class FolderSelectorControllerImpl implements FolderSelectorController {
    public VBox folderSelector;
    private Consumer<Path> folderSelectionListener;

    @Override
    public void setFolderSelectedAction(Consumer<Path> folderSelectionListener) {
        checkState(this.folderSelectionListener == null);
        this.folderSelectionListener = checkNotNull(folderSelectionListener);
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
            event.setDropCompleted(true);
            notifyListener(dragboard.getFiles().get(0));
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
            notifyListener(file);
        }
        actionEvent.consume();
    }

    private void notifyListener(File file) {
        folderSelectionListener.accept(file.toPath());
    }

    private boolean isSingleFolder(Dragboard dragboard) {
        return dragboard.hasFiles() && dragboard.getFiles().size() == 1 && dragboard.getFiles().get(0).isDirectory();
    }
}
