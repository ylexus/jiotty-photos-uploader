package net.yudichev.googlephotosupload.ui;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;

import javax.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.core.AppName.APP_TITLE;
import static net.yudichev.googlephotosupload.core.BuildVersion.buildVersion;

public final class AboutDialogFxController {
    private final FxmlContainerFactory fxmlContainerFactory;
    public Label titleLabel;
    public Pane supportMePane;
    public TextField versionLabel;

    @Inject
    public AboutDialogFxController(FxmlContainerFactory fxmlContainerFactory) {
        this.fxmlContainerFactory = checkNotNull(fxmlContainerFactory);
    }

    public void initialize() {
        titleLabel.setText(APP_TITLE);
        versionLabel.setText(buildVersion());
        supportMePane.getChildren().add(fxmlContainerFactory.create("SupportMePane.fxml").root());
    }
}
