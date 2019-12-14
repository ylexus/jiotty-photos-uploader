package net.yudichev.googlephotosupload.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.connector.google.common.AuthorizationBrowser;

import javax.inject.Inject;
import javax.inject.Provider;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.geometry.Pos.TOP_CENTER;
import static net.yudichev.googlephotosupload.ui.Bindings.Primary;

final class UiAuthorizationBrowser extends BaseLifecycleComponent implements AuthorizationBrowser {
    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    private final Provider<Stage> primaryStageProvider;
    private volatile Stage dialog;

    @Inject
    UiAuthorizationBrowser(@Primary Provider<Stage> primaryStageProvider) {
        this.primaryStageProvider = checkNotNull(primaryStageProvider);
    }

    @Override
    public void browse(String url) {
        Platform.runLater(() -> {
            Stage primaryStage = primaryStageProvider.get();

            Stage dialog = new Stage();
            WebView webView = new WebView();
            webView.getEngine().load(url);
            VBox vBox = new VBox(new Label("Please log in to Google"), webView);
            vBox.setAlignment(TOP_CENTER);
            dialog.setScene(new Scene(vBox));

            dialog.initOwner(primaryStage);
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.show();
            this.dialog = dialog;
        });
    }

    @Override
    protected void doStart() {
        Stage dialog = this.dialog;
        if (dialog != null) {
            Platform.runLater(dialog::close);
        }
    }
}
