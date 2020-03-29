package net.yudichev.googlephotosupload.ui;

import com.google.inject.assistedinject.Assisted;
import javafx.scene.Node;
import javafx.scene.Parent;

import javax.inject.Inject;
import java.util.Optional;

final class ProgressBox implements ProgressStatusBar {
    private final Parent root;
    private final ProgressBoxFxController controller;

    @Inject
    ProgressBox(FxmlContainerFactory fxmlContainerFactory,
                @Assisted String name,
                @Assisted Optional<Integer> totalCount) {
        FxmlContainer fxmlContainer = fxmlContainerFactory.create("ProgressBox.fxml");
        controller = fxmlContainer.controller();
        controller.init(name, totalCount);
        root = fxmlContainer.root();
    }

    @Override
    public void updateSuccess(int newValue) {
        controller.updateSuccess(newValue);
    }

    @Override
    public void updateFailure(int newValue) {
        controller.updateFailure(newValue);
    }

    @Override
    public void close(boolean success) {
        controller.done(success);
    }

    @Override
    public void onBackoffDelay(long backoffDelayMs) {
        controller.onBackoffDelay(backoffDelayMs);
    }

    @Override
    public Node node() {
        return root;
    }
}
