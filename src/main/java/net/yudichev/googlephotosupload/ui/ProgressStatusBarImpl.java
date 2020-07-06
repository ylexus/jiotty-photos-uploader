package net.yudichev.googlephotosupload.ui;

import com.google.inject.assistedinject.Assisted;
import javafx.scene.Node;
import javafx.scene.Parent;
import net.yudichev.googlephotosupload.core.KeyedError;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

final class ProgressStatusBarImpl implements ProgressStatusBar {
    private final Parent root;
    private final ProgressBoxFxController controller;
    private final AtomicBoolean hasFailures = new AtomicBoolean();

    @Inject
    ProgressStatusBarImpl(FxmlContainerFactory fxmlContainerFactory,
                          @Assisted String name,
                          @Assisted Optional<Integer> totalCount) {
        var fxmlContainer = fxmlContainerFactory.create("ProgressBox.fxml");
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
        hasFailures.set(true);
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
    public boolean hasFailures() {
        return hasFailures.get();
    }

    @Override
    public void close() {
        controller.close();
    }
}
