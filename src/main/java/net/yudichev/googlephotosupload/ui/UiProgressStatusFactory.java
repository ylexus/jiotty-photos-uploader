package net.yudichev.googlephotosupload.ui;

import javax.inject.Inject;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

final class UiProgressStatusFactory implements ProgressValueUpdaterFactory {
    private final MainScreenController mainScreenController;
    private final ProgressStatusBarFactory progressStatusBarFactory;

    @Inject
    UiProgressStatusFactory(MainScreenController mainScreenController,
                            ProgressStatusBarFactory progressStatusBarFactory) {
        this.mainScreenController = checkNotNull(mainScreenController);
        this.progressStatusBarFactory = checkNotNull(progressStatusBarFactory);
    }

    @Override
    public ProgressValueUpdater create(String name, Optional<Integer> totalCount) {
        ProgressStatusBar progressStatusBar = progressStatusBarFactory.create(name, totalCount);
        mainScreenController.addProgressBox(progressStatusBar.node());
        return progressStatusBar;
    }
}
