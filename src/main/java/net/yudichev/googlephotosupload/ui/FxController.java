package net.yudichev.googlephotosupload.ui;

import javafx.scene.control.TextArea;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import net.yudichev.googlephotosupload.core.Uploader;

import javax.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;

public final class FxController implements UiComponents {
    private final Uploader uploader;
    public VBox folderSelector;
    public VBox progressBox;
    public TextArea logArea;
    public GridPane progressPane;

    @Inject
    public FxController(Uploader uploader) {
        this.uploader = checkNotNull(uploader);
    }

    public void folderSelectorOnDragEnter(DragEvent event) {
        Dragboard dragboard = event.getDragboard();
        if (isSingleFolder(dragboard)) {
            folderSelector.setEffect(new DropShadow());
            folderSelector.setStyle("-fx-background-color: #6495ed80;");
        }
        event.consume();
    }

    @Override
    public VBox progressBox() {
        return checkNotNull(progressBox);
    }

    @Override
    public TextArea logArea() {
        return checkNotNull(logArea);
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
            uploader.start(dragboard.getFiles().get(0).toPath());
            folderSelector.setVisible(false);
            progressPane.setVisible(true);
            event.setDropCompleted(true);
        } else {
            event.setDropCompleted(false);
        }
        event.consume();
    }

    private boolean isSingleFolder(Dragboard dragboard) {
        return dragboard.hasFiles() && dragboard.getFiles().size() == 1 && dragboard.getFiles().get(0).isDirectory();
    }
}
