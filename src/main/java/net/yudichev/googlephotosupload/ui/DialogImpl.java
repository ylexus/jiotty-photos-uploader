package net.yudichev.googlephotosupload.ui;

import com.google.inject.assistedinject.Assisted;
import javafx.scene.Scene;
import javafx.stage.Stage;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.function.Consumer;

final class DialogImpl implements Dialog {
    private final Object fxController;
    private final Stage dialog;

    @Inject
    DialogImpl(Provider<JavafxApplicationResources> primaryStageProvider,
               FxmlContainerFactory fxmlContainerFactory,
               @Assisted("title") String title,
               @Assisted("fxmlPath") String fxmlPath,
               @Assisted Consumer<Stage> customizer) {
        FxmlContainer preferencesDialogFxContainer = fxmlContainerFactory.create(fxmlPath);
        fxController = preferencesDialogFxContainer.controller();
        Stage primaryStage = primaryStageProvider.get().primaryStage();
        dialog = new Stage();
        dialog.setTitle(title);
        dialog.setScene(new Scene(preferencesDialogFxContainer.root()));
        dialog.initOwner(primaryStage);
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
