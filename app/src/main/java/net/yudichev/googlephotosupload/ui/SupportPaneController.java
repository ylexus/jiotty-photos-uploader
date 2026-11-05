package net.yudichev.googlephotosupload.ui;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import javafx.event.ActionEvent;

import static com.google.common.base.Preconditions.checkNotNull;

public final class SupportPaneController {
    private final Provider<JavafxApplicationResources> javafxApplicationResourcesProvider;

    @Inject
    SupportPaneController(Provider<JavafxApplicationResources> javafxApplicationResourcesProvider) {
        this.javafxApplicationResourcesProvider = checkNotNull(javafxApplicationResourcesProvider);
    }

    public void onPaymentLinkAction(ActionEvent actionEvent) {
        openUrl("https://paypal.me/yudichev");
        actionEvent.consume();
    }

    public void onReportIssueAction(ActionEvent actionEvent) {
        openUrl("https://github.com/ylexus/jiotty-photos-uploader/issues");
        actionEvent.consume();
    }

    private void openUrl(String s) {
        javafxApplicationResourcesProvider.get().hostServices().showDocument(s);
    }
}
