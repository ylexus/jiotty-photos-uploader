package net.yudichev.googlephotosupload.ui;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import net.yudichev.googlephotosupload.core.Restarter;
import net.yudichev.googlephotosupload.core.Uploader;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public VBox progressBox;
    public TextFlow logArea;
    public Button stopButton;

    @Inject
    UploadPaneControllerImpl(Uploader uploader,
                             Restarter restarter) {
        this.uploader = checkNotNull(uploader);
        this.restarter = checkNotNull(restarter);
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
    public CompletableFuture<Void> startUpload(Path path) {
        runLater(() -> stopButton.setDisable(false));
        checkStarted();
        return uploader.upload(path)
                .whenComplete((aVoid, e) -> runLater(() -> {
                    if (isStarted()) {
                        stopButton.setVisible(false);
                        //TODO where do we put warnings about empty albums?
                        ObservableList<Node> logAreaChildren = logArea.getChildren();
                        if (e == null) {
                            logArea.getStyleClass().add("success-background");
                            logAreaChildren.add(new Text("Total success, ladies and gentlemen!"));
                        } else {
                            logger.error("Upload failed", e);
                            logArea.getStyleClass().add("failed-background");
                            logAreaChildren.add(new Text("Something went wrong: "));
                            Text failureText = new Text(humanReadableMessage(e));
                            failureText.getStyleClass().add("failed-text");
                            logAreaChildren.add(failureText);
                        }
                    } else if (e != null) {
                        logger.info("Upload failed after stop", e);
                    }
                }));
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
