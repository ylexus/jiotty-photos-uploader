package net.yudichev.googlephotosupload.ui;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import javafx.event.ActionEvent;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.core.AppGlobals.APP_TITLE;
import static net.yudichev.googlephotosupload.core.BuildVersion.buildVersion;

public final class AboutDialogFxController {
    private final Provider<JavafxApplicationResources> javafxApplicationResourcesProvider;
    public Hyperlink titleHyperlink;
    public Pane supportPane;
    public TextField versionLabel;

    @Inject
    public AboutDialogFxController(Provider<JavafxApplicationResources> javafxApplicationResourcesProvider) {
        this.javafxApplicationResourcesProvider = checkNotNull(javafxApplicationResourcesProvider);
    }

    public void initialize() {
        titleHyperlink.setText(APP_TITLE);
        versionLabel.setText(buildVersion());
    }

    public void onTitleHyperlinkLinkAction(ActionEvent actionEvent) {
        javafxApplicationResourcesProvider.get().hostServices().showDocument("http://jiotty-photos-uploader.yudichev.net");
        actionEvent.consume();
    }
}
