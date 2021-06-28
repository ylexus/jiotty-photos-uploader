package net.yudichev.googlephotosupload.ui;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
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
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

import static com.google.api.client.http.HttpStatusCodes.STATUS_CODE_FORBIDDEN;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.getCausalChain;
import static javafx.application.Platform.runLater;
import static net.yudichev.jiotty.common.lang.HumanReadableExceptionMessage.humanReadableMessage;

@SuppressWarnings("ClassWithTooManyFields") // OK for a FX controller
public final class UploadPaneControllerImpl extends BaseLifecycleComponent implements UploadPaneController {
    private static final Logger logger = LoggerFactory.getLogger(UploadPaneControllerImpl.class);

    private final Uploader uploader;
    private final Restarter restarter;
    private final Provider<MainScreenController> mainScreenControllerProvider;
    private final ResourceBundle resourceBundle;
    private final Collection<ProgressBox> progressBoxes = new ArrayList<>();
    public VBox progressBoxContainer;
    public TextFlow logArea;
    public Button stopButton;
    public VBox topVBox;
    public Button uploadMoreButton;
    private volatile boolean everInitialised;

    @Inject
    UploadPaneControllerImpl(Uploader uploader,
                             Restarter restarter,
                             Provider<MainScreenController> mainScreenControllerProvider,
                             ResourceBundle resourceBundle) {
        this.uploader = checkNotNull(uploader);
        this.restarter = checkNotNull(restarter);
        this.mainScreenControllerProvider = checkNotNull(mainScreenControllerProvider);
        this.resourceBundle = checkNotNull(resourceBundle);
    }

    public void initialize() {
        everInitialised = true;
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
        if (!everInitialised) {
            return;
        }
        runLater(() -> {
            logArea.getStyleClass().remove("success-background");
            logArea.getStyleClass().remove("failed-background");
            logArea.getStyleClass().remove("partial-success-background");
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
    public CompletableFuture<Void> startUpload(List<Path> path, boolean resume) {
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
                var logAreaChildren = logArea.getChildren();
                if (exception == null) {
                    if (progressBoxes.stream().anyMatch(ProgressBox::hasFailures)) {
                        logArea.getStyleClass().add("partial-success-background");
                        logAreaChildren.add(new Text(String.format(
                                resourceBundle.getString("uploadPaneLogAreaPartialSuccessLabel"),
                                resourceBundle.getString("progressBoxFailuresHyperlinkText"))));
                    } else {
                        logArea.getStyleClass().add("success-background");
                        logAreaChildren.add(new Text(resourceBundle.getString("uploadPaneLogAreaSuccessLabel")));
                    }
                } else {
                    logger.error("Upload failed", exception);
                    logArea.getStyleClass().add("failed-background");
                    logAreaChildren.add(new Text(resourceBundle.getString("uploadPaneLogAreaFailurePrefix") + " "));
                    var failureText = new Text(toHumanReadableMessage(exception));
                    failureText.getStyleClass().add("failed-text");
                    logAreaChildren.add(failureText);
                }
            } else if (exception != null) {
                logger.info("Upload failed after stop", exception);
            }
        });
    }

    private String toHumanReadableMessage(Throwable exception) {
        return getCausalChain(exception).stream()
                .filter(throwable -> throwable instanceof GoogleJsonResponseException)
                .findFirst()
                .map(throwable -> (GoogleJsonResponseException) throwable)
                .map(jsonResponseException -> {
                    // better error for GoogleJsonResponseException, otherwise there's too much technical details.
                    var details = jsonResponseException.getDetails();
                    if (details != null && details.getMessage() != null) {
                        if (details.getCode() == STATUS_CODE_FORBIDDEN) {
                            return details.getMessage() + ' ' + resourceBundle.getString("uploadPanePermissionErrorSuffix");
                        } else {
                            return details.getMessage();
                        }
                    }
                    return null;
                })
                .orElseGet(() -> humanReadableMessage(exception));
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
