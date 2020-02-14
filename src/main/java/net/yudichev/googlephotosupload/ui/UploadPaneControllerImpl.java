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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.application.Platform.runLater;
import static net.yudichev.jiotty.common.lang.HumanReadableExceptionMessage.humanReadableMessage;

public final class UploadPaneControllerImpl extends BaseLifecycleComponent implements UploadPaneController {
    private static final Logger logger = LoggerFactory.getLogger(UploadPaneControllerImpl.class);

    private final Uploader uploader;
    private final Restarter restarter;
    private final FxmlContainerFactory fxmlContainerFactory;
    public VBox progressBox;
    public TextFlow logArea;
    public Button stopButton;
    public VBox topVBox;

    @Inject
    UploadPaneControllerImpl(Uploader uploader,
                             Restarter restarter,
                             FxmlContainerFactory fxmlContainerFactory) {
        this.uploader = checkNotNull(uploader);
        this.restarter = checkNotNull(restarter);
        this.fxmlContainerFactory = checkNotNull(fxmlContainerFactory);
    }

    public void initialize() {
        Pane supportMePane = fxmlContainerFactory.create("SupportMePane.fxml").root();
        topVBox.getChildren().add(supportMePane);
    }

    @Override
    public void addProgressBox(Node node) {
        runLater(() -> progressBox.getChildren().add(node));
    }

    @Override
    public void reset() {
        runLater(() -> {
            logArea.getStyleClass().remove("success-background");
            logArea.getStyleClass().remove("failed-background");
            ObservableList<Node> logAreaChildren = logArea.getChildren();
            logAreaChildren.clear();

            progressBox.getChildren().clear();

            stopButton.setVisible(true);
            stopButton.setDisable(true);
        });
    }

    @Override
    public CompletableFuture<Void> startUpload(Path path, boolean resume) {
        checkStarted();
        stopButton.setDisable(false);
        return uploader.upload(path, resume)
                .whenComplete((aVoid, e) -> onUploadComplete(e));
    }

    private void onUploadComplete(@Nullable Throwable exception) {
        runLater(() -> {
            if (isStarted()) {
                stopButton.setVisible(false);
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
