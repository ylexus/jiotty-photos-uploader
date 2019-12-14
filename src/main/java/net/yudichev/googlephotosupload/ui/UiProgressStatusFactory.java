package net.yudichev.googlephotosupload.ui;

import javafx.application.Platform;
import net.yudichev.googlephotosupload.core.ProgressStatus;
import net.yudichev.googlephotosupload.core.ProgressStatusFactory;

import javax.inject.Inject;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

final class UiProgressStatusFactory implements ProgressStatusFactory {
    private final UiComponents uiComponents;
    private final ProgressStatusBarFactory progressStatusBarFactory;

    @Inject
    UiProgressStatusFactory(UiComponents uiComponents,
                            ProgressStatusBarFactory progressStatusBarFactory) {
        this.uiComponents = checkNotNull(uiComponents);
        this.progressStatusBarFactory = checkNotNull(progressStatusBarFactory);
    }

    @Override
    public ProgressStatus create(String name, Optional<Integer> totalCount) {
        ProgressStatusBar progressStatusBar = progressStatusBarFactory.create(name, totalCount);
        Platform.runLater(() -> uiComponents.progressBox().getChildren().add(progressStatusBar.node()));
        return progressStatusBar;
    }
}
