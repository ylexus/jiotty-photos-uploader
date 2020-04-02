package net.yudichev.googlephotosupload.ui;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import net.yudichev.googlephotosupload.core.Restarter;
import net.yudichev.googlephotosupload.core.Uploader;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.common.lang.Closeable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.application.Platform.runLater;
import static net.yudichev.jiotty.common.lang.HumanReadableExceptionMessage.humanReadableMessage;

public final class UploadPaneControllerImpl extends BaseLifecycleComponent implements UploadPaneController {
    private static final Logger logger = LoggerFactory.getLogger(UploadPaneControllerImpl.class);

    private final Uploader uploader;
    private final Restarter restarter;
    private final FxmlContainerFactory fxmlContainerFactory;
    private final Provider<MainScreenController> mainScreenControllerProvider;
    private final Collection<Closeable> progressBoxes = new ArrayList<>();
    public VBox progressBoxContainer;
    public TextFlow logArea;
    public Button stopButton;
    public VBox topVBox;
    public Button uploadMoreButton;

    @Inject
    UploadPaneControllerImpl(Uploader uploader,
                             Restarter restarter,
                             FxmlContainerFactory fxmlContainerFactory,
                             Provider<MainScreenController> mainScreenControllerProvider) {
        this.uploader = checkNotNull(uploader);
        this.restarter = checkNotNull(restarter);
        this.fxmlContainerFactory = checkNotNull(fxmlContainerFactory);
        this.mainScreenControllerProvider = checkNotNull(mainScreenControllerProvider);
    }

    public void initialize() {
        Pane supportMePane = fxmlContainerFactory.create("SupportMePane.fxml").root();
        topVBox.getChildren().add(supportMePane);
    }

    @Override
    public void addProgressBox(ProgressBox progressBox) {
        runLater(() -> {
            progressBoxes.add(progressBox);
            progressBoxContainer.getChildren().add(progressBox.node());
        });
    }

    @Override
    public void reset() {
        runLater(() -> {
            logArea.getStyleClass().remove("success-background");
            logArea.getStyleClass().remove("failed-background");
            logArea.getChildren().clear();
            logArea.setVisible(false);

            progressBoxContainer.getChildren().clear();
            progressBoxes.forEach(Closeable::close);
            progressBoxes.clear();

            stopButton.setDisable(true);
            uploadMoreButton.setDisable(true);
        });
    }

    @Override
    public CompletableFuture<Void> startUpload(Path path, boolean resume) {
        checkStarted();
        stopButton.setDisable(false);
        return uploader.upload(path, resume)
                .whenComplete((aVoid, e) -> onUploadComplete(e));
    }

    public void onUploadMoreButtonPressed(ActionEvent actionEvent) {
        mainScreenControllerProvider.get().toFolderSelectionMode();
        actionEvent.consume();
    }

    private void onUploadComplete(@Nullable Throwable exception) {
        runLater(() -> {
            if (isStarted() && !stopButton.isDisabled()) {
                stopButton.setDisable(true);
                uploadMoreButton.setDisable(false);
                logArea.setVisible(true);
                ObservableList<Node> logAreaChildren = logArea.getChildren();
                if (exception == null) {
                    logArea.getStyleClass().add("success-background");
                    logAreaChildren.add(new Text("Total success, ladies and gentlemen!"));
                } else {
                    logger.error("Upload failed", exception);
                    logArea.getStyleClass().add("failed-background");
                    logAreaChildren.add(new Text("Something went wrong: "));
                    Text failureText = new Text(humanReadableMessage(exception));
                    failureText.getStyleClass().add("failed-text");
                    logAreaChildren.add(failureText);
                }
            } else if (exception != null) {
                logger.info("Upload failed after stop", exception);
            }
        });
    }

    @Override
    public void stopUpload() {
        stopButton.setDisable(true);
        restarter.initiateRestart();
    }

    public void onStopButtonPressed(ActionEvent actionEvent) {
        stopUpload();
        actionEvent.consume();
    }

    @Override
    protected void doStop() {
        reset();
    }
}
