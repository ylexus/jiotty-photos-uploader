package net.yudichev.googlephotosupload.ui;

import javafx.event.ActionEvent;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;

import javax.inject.Inject;
import javax.inject.Provider;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.core.AppName.APP_TITLE;
import static net.yudichev.googlephotosupload.core.BuildVersion.buildVersion;

public final class AboutDialogFxController {
    private final FxmlContainerFactory fxmlContainerFactory;
    private final Provider<JavafxApplicationResources> javafxApplicationResourcesProvider;
    public Hyperlink titleHyperlink;
    public Pane supportMePane;
    public TextField versionLabel;

    @Inject
    public AboutDialogFxController(FxmlContainerFactory fxmlContainerFactory,
                                   Provider<JavafxApplicationResources> javafxApplicationResourcesProvider) {
        this.fxmlContainerFactory = checkNotNull(fxmlContainerFactory);
        this.javafxApplicationResourcesProvider = checkNotNull(javafxApplicationResourcesProvider);
    }

    public void initialize() {
        titleHyperlink.setText(APP_TITLE);
        versionLabel.setText(buildVersion());
        supportMePane.getChildren().add(fxmlContainerFactory.create("SupportMePane.fxml").root());
    }

    public void onTitleHyperlinkLinkAction(ActionEvent actionEvent) {
        javafxApplicationResourcesProvider.get().hostServices().showDocument("http://jiotty-photos-uploader.yudichev.net");
        actionEvent.consume();
    }
}
