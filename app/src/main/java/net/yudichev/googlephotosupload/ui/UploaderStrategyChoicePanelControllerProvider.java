package net.yudichev.googlephotosupload.ui;

import javax.inject.Inject;
import javax.inject.Provider;

import static com.google.common.base.Preconditions.checkNotNull;

final class UploaderStrategyChoicePanelControllerProvider implements Provider<UploaderStrategyChoicePanelController> {
    private final FxmlContainerFactory fxmlContainerFactory;

    @Inject
    UploaderStrategyChoicePanelControllerProvider(FxmlContainerFactory fxmlContainerFactory) {
        this.fxmlContainerFactory = checkNotNull(fxmlContainerFactory);
    }

    @Override
    public UploaderStrategyChoicePanelController get() {
        return fxmlContainerFactory.create("UploaderStrategyChoicePanel.fxml").controller();
    }
}
