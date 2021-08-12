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
                if (windows732Bit()) {
                    // known issue on Windows 7 32 bit - jfxwebkit is not properly working with Google Login dialog (javascript issues)
                    dialog = createSimpleLoginDialog();
                } else {
                    try {
                        dialog = createFullLoginDialog();
                    } catch (UnsatisfiedLinkError e) {
                        if (e.getMessage() != null && e.getMessage().contains("jfxwebkit")) {
                            dialog = createSimpleLoginDialog();
                        } else {
                            throw e;
                        }
                    }
                }
            }

            dialog.show();
            dialog.<LoginDialogController>controller().load(url);
        });
    }

    private Dialog createFullLoginDialog() {
        return dialogFactory.create(resourceBundle.getString("uiAuthorisationBrowserTitle"), "LoginDialog.fxml", this::customizeLoginDialog);
    }

    private Dialog createSimpleLoginDialog() {
        return dialogFactory.create(resourceBundle.getString("uiAuthorisationBrowserTitle"), "LoginDialogSimple.fxml", this::customizeSimpleLoginDialog);
    }

    private static boolean windows732Bit() {
        return "32".equals(System.getProperty("sun.arch.data.model")) && "Windows 7".equals(System.getProperty("os.name"));
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
