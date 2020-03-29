package net.yudichev.googlephotosupload.ui;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Optional;
import java.util.ResourceBundle;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.application.Platform.runLater;

public final class ProgressBoxFxController {
    private static final long BACKOFF_DELAY_BEFORE_ICON_APPEARS_MS = Duration.ofSeconds(20).toMillis();
    private final ResourceBundle resourceBundle;
    public ProgressIndicator progressIndicator;
    public Label nameLabel;
    public Label valueLabel;
    public Label failureCountLabel;
    public Text progressIndicatorFailureText;
    public ImageView backoffInfoIcon;
    public GridPane topPane;
    private Optional<Integer> totalCount;
    private SepiaToneEffectAnimatedNode animatedBackoffInfoIcon;
    private Tooltip backoffTooltip;

    @Inject
    public ProgressBoxFxController(ResourceBundle resourceBundle) {
        this.resourceBundle = checkNotNull(resourceBundle);
    }

    public void initialize() {
        backoffTooltip = new Tooltip();
        backoffTooltip.setShowDelay(javafx.util.Duration.millis(10));
        backoffTooltip.setWrapText(true);
        backoffTooltip.setPrefWidth(400);
        backoffTooltip.setShowDuration(javafx.util.Duration.INDEFINITE);
        Tooltip.install(backoffInfoIcon, backoffTooltip);
        animatedBackoffInfoIcon = new SepiaToneEffectAnimatedNode(backoffInfoIcon);
    }

    public void init(String name, Optional<Integer> totalCount) {
        this.totalCount = checkNotNull(totalCount);
        runLater(() -> {
            nameLabel.setText(name);
            valueLabel.setText("0");
            totalCount.ifPresent(ignored -> progressIndicator.setProgress(0.0));
            animatedBackoffInfoIcon.hide();
        });
    }

    public void updateSuccess(int value) {
        runLater(() -> {
            totalCount.ifPresent(count -> progressIndicator.setProgress((double) value / count));
            valueLabel.setText(Integer.toString(value));
            animatedBackoffInfoIcon.hide();
        });
    }

    public void updateFailure(int failureCount) {
        if (failureCount > 0) {
            runLater(() -> {
                failureCountLabel.setText("Failed: " + failureCount);
                animatedBackoffInfoIcon.hide();
            });
        }
    }

    public void done(boolean success) {
        runLater(() -> {
                    animatedBackoffInfoIcon.hide();
                    if (success) {
                        progressIndicator.setProgress(1.0);
                    } else {
                        if (totalCount.isEmpty()) {
                            progressIndicator.setVisible(false);
                            progressIndicatorFailureText.setVisible(true);
                        }
                    }
                }
        );
    }

    public void onBackoffDelay(long backoffDelayMs) {
        runLater(() -> {
            if (backoffDelayMs > BACKOFF_DELAY_BEFORE_ICON_APPEARS_MS) {
                backoffTooltip.setText(resourceBundle.getString("backoffNotice") + ' ' + Duration.ofMillis(backoffDelayMs).getSeconds());
                animatedBackoffInfoIcon.show();
            }
        });
    }
}
