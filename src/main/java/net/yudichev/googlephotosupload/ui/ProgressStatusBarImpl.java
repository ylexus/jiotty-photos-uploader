package net.yudichev.googlephotosupload.ui;

import com.google.inject.assistedinject.Assisted;
import javafx.scene.Node;
import javafx.scene.Parent;
import net.yudichev.googlephotosupload.core.KeyedError;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Optional;

final class ProgressStatusBarImpl implements ProgressStatusBar {
    private final Parent root;
    private final ProgressBoxFxController controller;

    @Inject
    ProgressStatusBarImpl(FxmlContainerFactory fxmlContainerFactory,
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
    public void addFailures(Collection<KeyedError> failures) {
        controller.addFailures(failures);
    }

    @Override
    public void completed(boolean success) {
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

    @Override
    public void close() {
        controller.close();
    }
}
