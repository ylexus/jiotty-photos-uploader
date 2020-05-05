package net.yudichev.googlephotosupload.ui;

import com.google.inject.assistedinject.Assisted;
import javafx.scene.Scene;
import javafx.scene.image.Image;
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
        var preferencesDialogFxContainer = fxmlContainerFactory.create(fxmlPath);
        fxController = preferencesDialogFxContainer.controller();
        var primaryStage = primaryStageProvider.get().primaryStage();
        dialog = new Stage();
        dialog.getIcons().add(new Image(getClass().getResourceAsStream("/Icon1024.png")));
        dialog.setTitle(title);
        dialog.setScene(new Scene(preferencesDialogFxContainer.root()));
        dialog.initOwner(primaryStage);
        customizer.accept(dialog);
    }

    @Override
    public void show() {
        dialog.show();
        dialog.toFront();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T controller() {
        return (T) fxController;
    }

    @Override
    public void sizeToScene() {
        dialog.sizeToScene();
    }

    @Override
    public void close() {
        dialog.close();
    }
}
