package net.yudichev.googlephotosupload.ui;

import javafx.event.ActionEvent;

import javax.inject.Inject;
import javax.inject.Provider;

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
