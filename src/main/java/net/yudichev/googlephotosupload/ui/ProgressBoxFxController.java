package net.yudichev.googlephotosupload.ui;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

import java.util.Optional;

import static javafx.application.Platform.runLater;

public final class ProgressBoxFxController {
    public ProgressIndicator progressIndicator;
    public Label nameLabel;
    public Label valueLabel;

    public void init(String name, Optional<Integer> totalCount) {
        runLater(() -> {
            nameLabel.setText(name);
            valueLabel.setText("0");
            totalCount.ifPresent(ignored -> progressIndicator.setProgress(0.0));
        });
    }

    public void updateValue(int value) {
        runLater(() -> valueLabel.setText(Integer.toString(value)));
    }

    public void done() {
        runLater(() -> progressIndicator.setProgress(1.0));
    }
}
