package net.yudichev.googlephotosupload.ui;

import com.google.inject.assistedinject.Assisted;
import javafx.stage.Stage;

import java.util.function.Consumer;

interface ModalDialogFactory {
    ModalDialog create(@Assisted("title") String title, @Assisted("fxmlPath") String fxmlPath, Consumer<Stage> customizer);
}
