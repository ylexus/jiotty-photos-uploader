package net.yudichev.googlephotosupload.ui;

import javafx.event.ActionEvent;

import javax.inject.Inject;
import javax.inject.Provider;

import static com.google.common.base.Preconditions.checkNotNull;

public final class SupportMePaneController {
    private final Provider<JavafxApplicationResources> javafxApplicationResourcesProvider;

    @Inject
    SupportMePaneController(Provider<JavafxApplicationResources> javafxApplicationResourcesProvider) {
        this.javafxApplicationResourcesProvider = checkNotNull(javafxApplicationResourcesProvider);
    }

    public void onSupportLinkAction(ActionEvent actionEvent) {
        javafxApplicationResourcesProvider.get().hostServices().showDocument("https://paypal.me/yudichev");
        actionEvent.consume();
    }
}
