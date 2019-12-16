package net.yudichev.googlephotosupload.ui;

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
import static javafx.application.Platform.runLater;
import static javafx.geometry.Pos.TOP_CENTER;
import static net.yudichev.googlephotosupload.ui.Bindings.Primary;

final class UiAuthorizationBrowser extends BaseLifecycleComponent implements AuthorizationBrowser {
    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    private final Provider<Stage> primaryStageProvider;
    private final MainScreenController mainScreenController;
    private Stage dialog;

    @Inject
    UiAuthorizationBrowser(@Primary Provider<Stage> primaryStageProvider,
                           MainScreenController mainScreenController) {
        this.primaryStageProvider = checkNotNull(primaryStageProvider);
        this.mainScreenController = checkNotNull(mainScreenController);
    }

    @Override
    public void browse(String url) {
        runLater(() -> {
            Stage primaryStage = primaryStageProvider.get();

            // TODO use FxmlContainerFactory
            dialog = new Stage();
            WebView webView = new WebView();
            webView.getEngine().load(url);
            VBox vBox = new VBox(new Label("Please log in to Google"), webView);
            vBox.setAlignment(TOP_CENTER);
            dialog.setScene(new Scene(vBox));

            dialog.initOwner(primaryStage);
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.show();
        });
    }

    @Override
    protected void doStart() {
        runLater(() -> {
            if (dialog != null) {
                dialog.close();
                dialog = null;
            }
            mainScreenController.toFolderSelectionMode();
        });
    }
}
