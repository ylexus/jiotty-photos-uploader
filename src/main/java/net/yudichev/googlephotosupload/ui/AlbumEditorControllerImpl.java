package net.yudichev.googlephotosupload.ui;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.ObservableValueBase;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import net.yudichev.jiotty.common.async.ExecutorFactory;
import net.yudichev.jiotty.common.async.SchedulingExecutor;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.time.Duration;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Collections.emptyList;

// TODO rebrand Album Editor -> Album Managwr
public final class AlbumEditorControllerImpl extends BaseLifecycleComponent implements AlbumEditorController {
    private static final Logger logger = LoggerFactory.getLogger(AlbumEditorControllerImpl.class);
    private final ExecutorFactory executorFactory;
    private final Provider<JavafxApplicationResources> javafxApplicationResourcesProvider;
    private final GooglePhotosClient googlePhotosClient;
    public TableView<SelectableAlbum> tableView;
    public TableColumn<SelectableAlbum, Boolean> selectColumn;
    public TableColumn<SelectableAlbum, SelectableAlbum> titleColumn;
    public TableColumn<SelectableAlbum, Long> itemCountColumn;
    public ProgressIndicator loadingIndicator;
    public CheckBox selectAllCheckBox;
    public Button deleteButton;
    private int selectedCount;
    private boolean updatingSelectionBoxes;
    private SchedulingExecutor executor;

    @Inject
    AlbumEditorControllerImpl(ExecutorFactory executorFactory,
                              Provider<JavafxApplicationResources> javafxApplicationResourcesProvider,
                              GooglePhotosClient googlePhotosClient) {
        this.executorFactory = checkNotNull(executorFactory);
        this.javafxApplicationResourcesProvider = checkNotNull(javafxApplicationResourcesProvider);
        this.googlePhotosClient = checkNotNull(googlePhotosClient);
    }

    public void initialize() {
        executor = executorFactory.createSingleThreadedSchedulingExecutor("empty-albums");
        executor.scheduleAtFixedRate(Duration.ZERO, Duration.ofMinutes(1), this::refreshAlbums);

        titleColumn.setCellValueFactory(param -> new ObservableValueBase<>() {
            @Override
            public SelectableAlbum getValue() {
                return param.getValue();
            }
        });
        titleColumn.setCellFactory(param -> new TableCell<>() {
            @Override
            protected void updateItem(SelectableAlbum album, boolean empty) {
                super.updateItem(album, empty);
                if (album == null || empty) {
                    setText(null);
                } else {
                    var hyperlink = new Hyperlink(album.googlePhotosAlbum.getTitle());
                    hyperlink.setOnAction(event -> javafxApplicationResourcesProvider.get().hostServices().showDocument(album.googlePhotosAlbum.getAlbumUrl()));
                    setGraphic(hyperlink);
                }
            }
        });

        itemCountColumn.setCellValueFactory(param -> new ObservableValueBase<>() {
            @Override
            public Long getValue() {
                return param.getValue().googlePhotosAlbum.getMediaItemCount();
            }
        });


        selectAllCheckBox = new CheckBox();
        selectAllCheckBox.selectedProperty().addListener(this::onSelectAllChanged);
        selectColumn.setCellValueFactory(data -> data.getValue().selectedProperty);
        selectColumn.setGraphic(selectAllCheckBox);
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));
        selectColumn.setEditable(true);

        tableView.setEditable(true);
    }

    @SuppressWarnings("TypeParameterExtendsFinalClass")
    private void onSelectAllChanged(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        runSelectionUpdateListener(() -> {
            var items = tableView.getItems();
            items.forEach(selectableAlbum -> selectableAlbum.selectedProperty.set(newValue));
            selectedCount = newValue ? items.size() : 0;
            logger.debug("selectedCount={}", selectedCount);
        });
    }

    private void refreshAlbums() {
        Platform.runLater(() -> loadingIndicator.setVisible(true));
        googlePhotosClient.listAlbums(executor)
                .exceptionally(e -> {
                    // TODO reflect in the UI
                    logger.error("Failed to load albums", e);
                    return emptyList();
                })
                .thenAccept(albums -> {
                    var newAlbumIds = albums.stream()
                            .filter(GooglePhotosAlbum::isWriteable)
                            .map(GooglePhotosAlbum::getId).collect(toImmutableSet());
                    Platform.runLater(() -> {
                        loadingIndicator.setVisible(false);
                        var tableItems = tableView.getItems();
                        // remove deleted
                        tableItems.removeIf(selectableAlbum -> !newAlbumIds.contains(selectableAlbum.googlePhotosAlbum.getId()));
                        // add new
                        var existingAlbumIds = tableItems.stream().map(selectableAlbum -> selectableAlbum.googlePhotosAlbum.getId()).collect(toImmutableSet());
                        albums.stream()
                                .filter(GooglePhotosAlbum::isWriteable)
                                .filter(googlePhotosAlbum -> !existingAlbumIds.contains(googlePhotosAlbum.getId()))
                                .forEach(googlePhotosAlbum -> tableItems.add(new SelectableAlbum(googlePhotosAlbum)));
                        // re-index
                        selectedCount = 0;
                        for (var selectableAlbum : tableItems) {
                            if (selectableAlbum.selectedProperty.get()) {
                                selectedCount++;
                            }
                        }
                        runSelectionUpdateListener(() -> {
                            updateSelectAllCheckbox();
                            updateButtons();
                        });
                    });
                });
    }

    private void updateButtons() {
        deleteButton.setDisable(selectedCount == 0);
    }

    private void updateSelectAllCheckbox() {
        logger.debug("selectedCount={}, table items={}", selectedCount, tableView.getItems().size());
        if (selectedCount == tableView.getItems().size()) {
            selectAllCheckBox.setSelected(true);
            selectAllCheckBox.setIndeterminate(false);
        } else if (selectedCount == 0) {
            selectAllCheckBox.setSelected(false);
            selectAllCheckBox.setIndeterminate(false);
        } else {
            selectAllCheckBox.setIndeterminate(true);
        }
    }

    private void runSelectionUpdateListener(Runnable action) {
        if (!updatingSelectionBoxes) {
            updatingSelectionBoxes = true;
            try {
                action.run();
            } finally {
                updatingSelectionBoxes = false;
            }
        }
    }

    @Override
    protected void doStop() {
        // TODO stop all activity, clear table contents
        executor.close();
    }

    public void onDeleteButtonAction(ActionEvent actionEvent) {

        actionEvent.consume();
    }

    private final class SelectableAlbum {
        private final GooglePhotosAlbum googlePhotosAlbum;
        private final BooleanProperty selectedProperty = new SimpleBooleanProperty(false);

        private SelectableAlbum(GooglePhotosAlbum googlePhotosAlbum) {
            this.googlePhotosAlbum = checkNotNull(googlePhotosAlbum);
            selectedProperty.addListener((observable, oldValue, newValue) -> runSelectionUpdateListener(() -> {
                if (newValue) {
                    selectedCount++;
                } else {
                    selectedCount--;
                }
                updateSelectAllCheckbox();
                updateButtons();
            }));
        }
    }
}
