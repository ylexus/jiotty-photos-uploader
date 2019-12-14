package net.yudichev.googlephotosupload.ui;

import com.google.inject.assistedinject.Assisted;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;

import javax.inject.Inject;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

final class CurrentValueStatusBar implements ProgressStatusBar {
    private final HBox container;
    private final Label nameLabel;
    private final String name;
    private final Label valueLabel;
    private final ProgressIndicator progressIndicator;
    private int value;

    @Inject
    CurrentValueStatusBar(FxmlContainerFactory fxmlContainerFactory,
                          @Assisted String name,
                          @Assisted Optional<Integer> totalCount) {
        nameLabel = new Label(name);
        valueLabel = new Label();

        progressIndicator = new ProgressIndicator();
        totalCount.ifPresent(ignored -> progressIndicator.setProgress(0.0));

        progressIndicator.prefHeight(20); // TODO is it needed?
        container = new HBox(progressIndicator, nameLabel, valueLabel);
        container.setSpacing(4);
        container.setAlignment(Pos.CENTER_LEFT);
        this.name = checkNotNull(name);
    }

    @Override
    public void update(int newValue) {
        // TODO conflation!
        Platform.runLater(() -> {
            value = newValue;
            updateLabel();
        });
    }

    @Override
    public void incrementBy(int increment) {
        Platform.runLater(() -> {
            value += increment;
            updateLabel();
        });
    }

    @Override
    public void close() {
        progressIndicator.setProgress(1.0);
    }

    @Override
    public Node node() {
        return container;
    }

    private void updateLabel() {
        valueLabel.setText(Integer.toString(value));
    }
}
