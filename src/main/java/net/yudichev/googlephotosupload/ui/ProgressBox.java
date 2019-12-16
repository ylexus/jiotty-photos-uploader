package net.yudichev.googlephotosupload.ui;

import com.google.inject.assistedinject.Assisted;
import javafx.scene.Node;
import javafx.scene.Parent;

import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static net.yudichev.jiotty.common.lang.Locks.inLock;

final class ProgressBox implements ProgressStatusBar {
    private final Parent root;
    private final ProgressBoxFxController controller;
    private final Lock lock = new ReentrantLock();
    private int value;

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
    public void update(int newValue) {
        inLock(lock, () -> {
            value = newValue;
            controller.updateValue(value);
        });
    }

    @Override
    public void close() {
        controller.done();
    }

    @Override
    public Node node() {
        return root;
    }
}
