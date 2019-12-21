package net.yudichev.googlephotosupload.ui;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.application.Platform.runLater;

public final class ProgressBoxFxController {
    public ProgressIndicator progressIndicator;
    public Label nameLabel;
    public Label valueLabel;
    public Label failureCountLabel;
    private Optional<Integer> totalCount;

    public void init(String name, Optional<Integer> totalCount) {
        this.totalCount = checkNotNull(totalCount);
        runLater(() -> {
            nameLabel.setText(name);
            valueLabel.setText("0");
            totalCount.ifPresent(ignored -> progressIndicator.setProgress(0.0));
        });
    }

    public void updateSuccess(int value) {
        runLater(() -> {
            totalCount.ifPresent(count -> progressIndicator.setProgress((double) value / count));
            valueLabel.setText(Integer.toString(value));
        });
    }

    public void updateFailure(int failureCount) {
        if (failureCount > 0) {
            runLater(() -> failureCountLabel.setText("Failed: " + failureCount));
        }
    }

    public void done() {
        runLater(() -> progressIndicator.setProgress(1.0));
    }
}
