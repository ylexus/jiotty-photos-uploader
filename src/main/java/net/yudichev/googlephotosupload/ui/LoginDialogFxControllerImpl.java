package net.yudichev.googlephotosupload.ui;

import javafx.scene.web.WebView;

public final class LoginDialogFxControllerImpl implements LoginDialogFxController {
    public WebView webView;

    @Override
    public void load(String url) {
        webView.getEngine().load(url);
    }
}
