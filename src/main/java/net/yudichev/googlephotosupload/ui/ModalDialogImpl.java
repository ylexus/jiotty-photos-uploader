package net.yudichev.googlephotosupload.ui;

import com.google.inject.assistedinject.Assisted;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.function.Consumer;

import static net.yudichev.googlephotosupload.ui.Bindings.Primary;

final class ModalDialogImpl implements ModalDialog {
    private final Object fxController;
    private final Stage dialog;

    @Inject
    ModalDialogImpl(@Primary Provider<Stage> primaryStageProvider,
                    FxmlContainerFactory fxmlContainerFactory,
                    @Assisted("title") String title,
                    @Assisted("fxmlPath") String fxmlPath,
                    @Assisted Consumer<Stage> customizer) {
        FxmlContainer preferencesDialogFxContainer = fxmlContainerFactory.create(fxmlPath);
        fxController = preferencesDialogFxContainer.controller();
        Stage primaryStage = primaryStageProvider.get();
        dialog = new Stage();
        dialog.setMinHeight(500);
        dialog.setMinWidth(500);
        dialog.setTitle(title);
        dialog.setScene(new Scene(preferencesDialogFxContainer.root()));
        dialog.initOwner(primaryStage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        customizer.accept(dialog);
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
