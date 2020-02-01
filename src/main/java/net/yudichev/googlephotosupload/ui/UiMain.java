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
    private static final AtomicReference<Consumer<JavafxApplicationResources>> javafxApplicationResourcesHandler = new AtomicReference<>();

    public static void main(String[] args) {
        // TODO https://bugs.openjdk.java.net/browse/JDK-8221253, remove when moved to JDK13
        System.setProperty("jdk.tls.client.protocols", "TLSv1,TLSv1.1,TLSv1.2");
        Application.builder()
                .addModule(() -> new UiModule(javafxApplicationResourcesHandler -> {
                    checkState(UiMain.javafxApplicationResourcesHandler.compareAndSet(null, javafxApplicationResourcesHandler), "can only launch once");
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
        primaryStage.setMinWidth(500);
        primaryStage.setMinHeight(500);
        javafxApplicationResourcesHandler.get().accept(JavafxApplicationResources.builder()
                .setHostServices(getHostServices())
                .setPrimaryStage(primaryStage)
                .build());
    }
}
