package net.yudichev.googlephotosupload.ui;

import javafx.application.Platform;
import javafx.stage.Stage;
import net.yudichev.googlephotosupload.core.DependenciesModule;
import net.yudichev.googlephotosupload.core.UploadPhotosModule;
import net.yudichev.googlephotosupload.ui.Bindings.AuthBrowser;
import net.yudichev.jiotty.common.app.Application;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkState;
import static net.yudichev.jiotty.common.inject.BindingSpec.annotatedWith;

public final class UiMain extends javafx.application.Application {
    private static final AtomicReference<Consumer<Stage>> primaryStageHandler = new AtomicReference<>();

    public static void main(String[] args) {
        Application.builder()
                .addModule(() -> new UiModule(primaryStageHandler -> {
                    checkState(UiMain.primaryStageHandler.compareAndSet(null, primaryStageHandler), "can only launch once");
                    new Thread(() -> launch(args)).start();
                }))
                .addModule(() -> new UploadPhotosModule(1000))
                .addModule(() -> new DependenciesModule(builder -> builder.setAuthorizationBrowser(annotatedWith(AuthBrowser.class))))
                .addModule(UiAuthorizationBrowserModule::new)
                .build()
                .run();
        Platform.exit();
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStageHandler.get().accept(primaryStage);
    }
}
