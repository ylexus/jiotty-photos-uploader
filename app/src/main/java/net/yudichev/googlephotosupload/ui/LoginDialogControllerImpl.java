package net.yudichev.googlephotosupload.ui;

import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.web.WebView;
import net.yudichev.googlephotosupload.core.CustomCredentialsManager;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ResourceBundle;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.ui.PreferencesDialogController.CUSTOM_CREDENTIALS_HELP_URL;

public final class LoginDialogControllerImpl implements LoginDialogController {
    private final CustomCredentialsManager customCredentialsManager;
    private final Provider<JavafxApplicationResources> javafxApplicationResourcesProvider;
    private final Provider<MainScreenController> mainScreenControllerProvider;
    private final ResourceBundle resourceBundle;
    public WebView webView;
    public TextField urlTextField;
    public Label customCredentialsLabel;

    @Inject
    public LoginDialogControllerImpl(CustomCredentialsManager customCredentialsManager,
                                     Provider<JavafxApplicationResources> javafxApplicationResourcesProvider,
                                     Provider<MainScreenController> mainScreenControllerProvider,
                                     ResourceBundle resourceBundle) {
        this.customCredentialsManager = checkNotNull(customCredentialsManager);
        this.javafxApplicationResourcesProvider = checkNotNull(javafxApplicationResourcesProvider);
        this.mainScreenControllerProvider = checkNotNull(mainScreenControllerProvider);
        this.resourceBundle = checkNotNull(resourceBundle);
    }

    public void initialize() {
        webView.getEngine().locationProperty().addListener((observable, oldValue, newValue) -> urlTextField.setText(newValue));
    }

    @Override
    public void load(String url) {
        refreshCustomCredentialsPane();
        webView.getEngine().load(url);
    }

    public void onCredentialsConfigureButtonAction(ActionEvent actionEvent) {
        mainScreenControllerProvider.get().openPreferencesAtCustomCredentials();
        actionEvent.consume();
    }

    public void onCredentialsConfigureHelp(MouseEvent mouseEvent) {
        javafxApplicationResourcesProvider.get().hostServices().showDocument(CUSTOM_CREDENTIALS_HELP_URL);
        mouseEvent.consume();
    }


    private void refreshCustomCredentialsPane() {
        customCredentialsLabel.setText(resourceBundle.getString(customCredentialsManager.usingCustomCredentials() ?
                "loginDialogLabelUsingCustomCredentials" : "loginDialogLabelUsingDefaultCredentials"));
    }
}
