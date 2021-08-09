package net.yudichev.googlephotosupload.ui;

import javafx.stage.Stage;
import net.yudichev.jiotty.common.app.ApplicationLifecycleControl;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.connector.google.common.AuthorizationBrowser;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ResourceBundle;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.application.Platform.runLater;
import static javafx.stage.Modality.APPLICATION_MODAL;

final class UiAuthorizationBrowser extends BaseLifecycleComponent implements AuthorizationBrowser {
    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    private final Provider<MainScreenController> mainScreenControllerProvider;
    private final ApplicationLifecycleControl applicationLifecycleControl;
    private final ResourceBundle resourceBundle;
    private final DialogFactory dialogFactory;
    private Dialog dialog;

    @Inject
    UiAuthorizationBrowser(Provider<MainScreenController> mainScreenControllerProvider,
                           DialogFactory dialogFactory,
                           ApplicationLifecycleControl applicationLifecycleControl,
                           ResourceBundle resourceBundle) {
        this.dialogFactory = checkNotNull(dialogFactory);
        this.mainScreenControllerProvider = checkNotNull(mainScreenControllerProvider);
        this.applicationLifecycleControl = checkNotNull(applicationLifecycleControl);
        this.resourceBundle = checkNotNull(resourceBundle);
    }

    @Override
    public void browse(String url) {
        runLater(() -> {
            if (dialog == null) {
                var dialogTitle = resourceBundle.getString("uiAuthorisationBrowserTitle");
                try {
                    dialog = dialogFactory.create(dialogTitle, "LoginDialog.fxml", this::customizeLoginDialog);
                } catch (UnsatisfiedLinkError e) {
                    if (e.getMessage() != null && e.getMessage().contains("jfxwebkit")) {
                        dialog = dialogFactory.create(dialogTitle, "LoginDialogSimple.fxml", this::customizeSimpleLoginDialog);
                    } else {
                        throw e;
                    }
                }
            }

            dialog.show();
            dialog.<LoginDialogController>controller().load(url);
        });
    }

    @Override
    protected void doStart() {
        runLater(() -> {
            closeDialog();
            mainScreenControllerProvider.get().toFolderSelectionMode();
        });
    }

    @Override
    protected void doStop() {
        runLater(this::closeDialog);
    }

    private void customizeLoginDialog(Stage dialog) {
        customiseCommonDialogProperties(dialog);
        dialog.setMinHeight(500);
        dialog.setMinWidth(500);
    }

    private void customizeSimpleLoginDialog(Stage dialog) {
        customiseCommonDialogProperties(dialog);
        dialog.setResizable(false);
    }

    private void customiseCommonDialogProperties(Stage dialog) {
        dialog.initModality(APPLICATION_MODAL);
        dialog.setOnCloseRequest(event -> {
            applicationLifecycleControl.initiateShutdown();
            event.consume();
        });
    }

    private void closeDialog() {
        if (dialog != null) {
            dialog.close();
        }
    }
}
