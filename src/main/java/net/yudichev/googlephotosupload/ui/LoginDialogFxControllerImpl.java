package net.yudichev.googlephotosupload.ui;

import javafx.scene.control.TextField;
import javafx.scene.web.WebView;

public final class LoginDialogFxControllerImpl implements LoginDialogFxController {
    public WebView webView;
    public TextField urlTextField;

    public void initialize() {
        webView.getEngine().locationProperty().addListener((observable, oldValue, newValue) -> urlTextField.setText(newValue));
    }

    @Override
    public void load(String url) {
        webView.getEngine().load(url);
    }
}
