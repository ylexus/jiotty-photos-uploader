package net.yudichev.googlephotosupload.ui;

import com.google.inject.assistedinject.Assisted;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.inject.Inject;
import javax.inject.Provider;

import static net.yudichev.googlephotosupload.ui.Bindings.Primary;

final class ModalDialogImpl implements ModalDialog {
    private final Object fxController;
    private final Stage dialog;

    @Inject
    ModalDialogImpl(@Primary Provider<Stage> primaryStageProvider,
                    FxmlContainerFactory fxmlContainerFactory,
                    @Assisted("title") String title,
                    @Assisted("fxmlPath") String fxmlPath) {
        FxmlContainer preferencesDialogFxContainer = fxmlContainerFactory.create(fxmlPath);
        fxController = preferencesDialogFxContainer.controller();
        Stage primaryStage = primaryStageProvider.get();
        dialog = new Stage();
        dialog.setTitle(title);
        dialog.setScene(new Scene(preferencesDialogFxContainer.root()));
        dialog.initOwner(primaryStage);
        dialog.initModality(Modality.APPLICATION_MODAL);
    }

    @Override
    public void show() {
        dialog.show();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T controller() {
        return (T) fxController;
    }

    @Override
    public void close() {
        dialog.close();
    }
}
