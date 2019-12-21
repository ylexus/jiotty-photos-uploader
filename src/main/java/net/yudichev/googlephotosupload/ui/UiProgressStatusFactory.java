package net.yudichev.googlephotosupload.ui;

import javax.inject.Inject;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

final class UiProgressStatusFactory implements ProgressValueUpdaterFactory {
    private final UploadPaneController uploadPaneController;
    private final ProgressStatusBarFactory progressStatusBarFactory;

    @Inject
    UiProgressStatusFactory(UploadPaneController uploadPaneController,
                            ProgressStatusBarFactory progressStatusBarFactory) {
        this.uploadPaneController = checkNotNull(uploadPaneController);
        this.progressStatusBarFactory = checkNotNull(progressStatusBarFactory);
    }

    @Override
    public ProgressValueUpdater create(String name, Optional<Integer> totalCount) {
        ProgressStatusBar progressStatusBar = progressStatusBarFactory.create(name, totalCount);
        uploadPaneController.addProgressBox(progressStatusBar.node());
        return progressStatusBar;
    }
}
