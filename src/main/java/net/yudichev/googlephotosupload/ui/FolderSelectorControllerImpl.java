package net.yudichev.googlephotosupload.ui;

import javafx.application.Platform;
import javafx.beans.value.ObservableValueBase;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.util.Callback;
import net.yudichev.googlephotosupload.core.Uploader;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.common.varstore.VarStore;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.BiConsumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static javafx.geometry.Insets.EMPTY;
import static javafx.scene.Cursor.HAND;
import static javafx.scene.control.OverrunStyle.LEADING_ELLIPSIS;

public final class FolderSelectorControllerImpl extends BaseLifecycleComponent implements FolderSelectorController {
    private static final String VARSTORE_KEY_SOURCE_DIRECTORIES = "sourceDirectories";
    private final Uploader uploader;
    private final ResourceBundle resourceBundle;
    private final VarStore varStore;
    private final Restarter restarter;
    public VBox folderSelector;
    public CheckBox resumeCheckbox;
    public FlowPane resumePane;
    public Label alreadyUploadedLabel;
    public TableView<Path> folderTableView;
    public TableColumn<Path, Path> pathColumn;
    public TableColumn<Path, Void> deleteColumn;
    public VBox folderSelectorBox;
    public Button startUploadButton;
    public Button browseButton;
    private BiConsumer<List<Path>, Boolean> folderSelectionListener;
    private volatile boolean everInitialised;

    @Inject
    FolderSelectorControllerImpl(Uploader uploader,
                                 ResourceBundle resourceBundle,
                                 VarStore varStore,
                                 Restarter restarter) {
        this.uploader = checkNotNull(uploader);
        this.resourceBundle = checkNotNull(resourceBundle);
        this.varStore = checkNotNull(varStore);
        this.restarter = checkNotNull(restarter);
    }

    public void initialize() {
        if (!everInitialised) {
            var sourceDirectories =
                    varStore.readValue(SourceDirectories.class, VARSTORE_KEY_SOURCE_DIRECTORIES).orElseGet(() -> SourceDirectories.builder().build());
            var items = folderTableView.getItems();
            sourceDirectories.paths().stream().map(Paths::get).forEach(items::add);
            items.addListener((ListChangeListener<Path>) this::onFolderListChanged);
            folderTableView.setSelectionModel(null);

            var resizePolicy = new HypnosResizePolicy();
            resizePolicy.registerFixedWidthColumns(deleteColumn);
            folderTableView.setColumnResizePolicy(resizePolicy);

            updateStartUploadButtonState();

            deleteColumn.setCellFactory(new Callback<>() {
                @Override
                public TableCell<Path, Void> call(TableColumn<Path, Void> param) {
                    return new TableCell<>() {
                        private final Button btn = new Button(null) {{
                            var imageView = new ImageView(getClass().getResource("/delete-icon.png").toString());
                            imageView.setFitHeight(20);
                            imageView.setPreserveRatio(true);
                            imageView.setSmooth(true);
                            setGraphic(imageView);
                            setPrefSize(20, 20);
                            setPadding(EMPTY);
                            setStyle("-fx-background-color: transparent;");
                            setCursor(HAND);
                        }};

                        @Override
                        protected void updateItem(Void item, boolean empty) {
                            super.updateItem(item, empty);
                            if (empty) {
                                setGraphic(null);
                            } else {
                                btn.setOnAction(event -> getTableView().getItems().remove(getIndex()));
                                setGraphic(btn);
                            }
                            setText(null);
                        }
                    };
                }
            });
            pathColumn.setCellValueFactory(param -> new ObservableValueBase<>() {
                @Override
                public Path getValue() {
                    return param.getValue();
                }
            });
            pathColumn.setCellFactory(param -> new TableCell<>() {
                {
                    setTextOverrun(LEADING_ELLIPSIS);
                }

                @Override
                protected void updateItem(Path item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.toAbsolutePath().toString());
                }
            });

            refreshViews();

            everInitialised = true;
        }

        if (startUploadButton.isDisabled()) {
            browseButton.requestFocus();
        } else {
            startUploadButton.requestFocus();
        }
    }

    private void updateStartUploadButtonState() {
        startUploadButton.setDisable(folderTableView.getItems().isEmpty());
    }

    private void refreshViews() {
        var numberOfUploadedItems = uploader.numberOfUploadedItems();
        if (numberOfUploadedItems > 0) {
            alreadyUploadedLabel.setText(String.format("(%s %s)", resourceBundle.getString("folderSelectorAlreadyUploadedLabelPrefix"), numberOfUploadedItems));
            resumeCheckbox.setSelected(true);
            resumePane.setVisible(true);
        } else {
            resumePane.setVisible(false);
        }
    }

    @Override
    protected void doStart() {
        // re-initialise on restart
        if (everInitialised) {
            Platform.runLater(this::initialize);
        }
    }

    @SuppressWarnings("MethodMayBeStatic")
    public void folderSelectorOnDragOver(DragEvent event) {
        if (isSingleFolder(event.getDragboard())) {
            event.acceptTransferModes(TransferMode.ANY);
        }
        event.consume();
    }

    @Override
    public void refresh() {
        initialize();
        refreshViews();
    }

    @Override
    public void setFolderSelectedAction(BiConsumer<List<Path>, Boolean> folderSelectionListener) {
        checkState(this.folderSelectionListener == null);
        this.folderSelectionListener = checkNotNull(folderSelectionListener);
    }

    public void folderSelectorOnDragEnter(DragEvent event) {
        var dragboard = event.getDragboard();
        if (isSingleFolder(dragboard)) {
            folderSelectorBox.setEffect(new DropShadow());
            folderSelectorBox.setStyle("-fx-background-color: #6495ed80;");
        }
        event.consume();
    }

    public void folderSelectorOnDragExit(DragEvent event) {
        folderSelectorBox.setEffect(null);
        folderSelectorBox.setStyle(null);
        event.consume();
    }

    private static boolean isSingleFolder(Dragboard dragboard) {
        return dragboard.hasFiles() && dragboard.getFiles().size() == 1 && dragboard.getFiles().get(0).isDirectory();
    }

    public void folderSelectorOnDragDropped(DragEvent event) {
        var dragboard = event.getDragboard();
        if (isSingleFolder(dragboard)) {
            event.setDropCompleted(true);
            addFolder(dragboard.getFiles().get(0));
        } else {
            event.setDropCompleted(false);
        }
        event.consume();
    }

    private void addFolder(File file) {
        var path = file.toPath();
        var items = folderTableView.getItems();
        if (!items.contains(path)) {
            items.add(path);
        }
    }

    private void onFolderListChanged(ListChangeListener.Change<? extends Path> change) {
        updateStartUploadButtonState();

        var builder = SourceDirectories.builder();
        change.getList().forEach(path -> builder.addPaths(path.toAbsolutePath().toString()));
        varStore.saveValue(VARSTORE_KEY_SOURCE_DIRECTORIES, builder.build());
    }

    public void onBrowseButtonClick(ActionEvent actionEvent) {
        var directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(resourceBundle.getString("folderSelectorDirectoryChooserTitle"));
        var file = directoryChooser.showDialog(folderSelector.getScene().getWindow());
        if (file != null) {
            addFolder(file);
        }
        actionEvent.consume();
    }

    public void onStartButtonAction(ActionEvent actionEvent) {
        folderSelectionListener.accept(folderTableView.getItems(), resumeCheckbox.isSelected());
        actionEvent.consume();
    }

    public void onLogoutButtonAction(ActionEvent actionEvent) {
        restarter.initiateLogoutAndRestart();
        actionEvent.consume();
    }
}
