package net.yudichev.googlephotosupload.ui;

import javafx.stage.Stage;
import net.yudichev.jiotty.common.app.ApplicationLifecycleControl;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.connector.google.common.AuthorizationBrowser;

import javax.inject.Inject;
import javax.inject.Provider;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.application.Platform.runLater;

final class UiAuthorizationBrowser extends BaseLifecycleComponent implements AuthorizationBrowser {
    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    private final Provider<MainScreenController> mainScreenControllerProvider;
    private final ApplicationLifecycleControl applicationLifecycleControl;
    private final ModalDialogFactory modalDialogFactory;
    private ModalDialog dialog;

    @Inject
    UiAuthorizationBrowser(Provider<MainScreenController> mainScreenControllerProvider,
                           ModalDialogFactory modalDialogFactory,
                           ApplicationLifecycleControl applicationLifecycleControl) {
        this.modalDialogFactory = checkNotNull(modalDialogFactory);
        this.mainScreenControllerProvider = checkNotNull(mainScreenControllerProvider);
        this.applicationLifecycleControl = checkNotNull(applicationLifecycleControl);
    }

    @Override
    public void browse(String url) {
        runLater(() -> {
            if (dialog == null) {
                dialog = modalDialogFactory.create("Login to Google", "LoginDialog.fxml", this::customizeLoginDialog);
            }

            dialog.show();
            dialog.<LoginDialogFxController>controller().load(url);
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

    private void customizeLoginDialog(Stage stage) {
        stage.setOnCloseRequest(event -> {
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
