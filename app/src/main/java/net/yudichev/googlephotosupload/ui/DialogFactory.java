package net.yudichev.googlephotosupload.ui;

import com.google.inject.assistedinject.Assisted;
import javafx.stage.Stage;

import java.util.function.Consumer;

interface DialogFactory {
    Dialog create(@Assisted("title") String title, @Assisted("fxmlPath") String fxmlPath, Consumer<Stage> customizer);
}
