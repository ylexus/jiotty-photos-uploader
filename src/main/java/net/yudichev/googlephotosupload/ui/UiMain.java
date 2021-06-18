package net.yudichev.googlephotosupload.ui;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import net.yudichev.googlephotosupload.core.*;
import net.yudichev.googlephotosupload.ui.Bindings.AuthBrowser;
import net.yudichev.jiotty.common.app.Application;
import net.yudichev.jiotty.common.time.TimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkState;
import static net.yudichev.googlephotosupload.core.BuildVersion.buildVersion;
import static net.yudichev.googlephotosupload.core.ResourceBundleModule.RESOURCE_BUNDLE;
import static net.yudichev.googlephotosupload.core.SingleInstanceCheck.otherInstanceRunning;
import static net.yudichev.googlephotosupload.ui.FatalStartupError.showFatalStartupError;
import static net.yudichev.jiotty.common.inject.BindingSpec.annotatedWith;

public final class UiMain extends javafx.application.Application {
    private static final Logger logger = LoggerFactory.getLogger(UiMain.class);
    private static final AtomicReference<Consumer<JavafxApplicationResources>> javafxApplicationResourcesHandler = new AtomicReference<>();

    public static void main(String[] args) {
        var settingsModule = new SettingsModule();
        if (otherInstanceRunning(settingsModule.getSettingsRootPath())) {
            showFatalStartupError(RESOURCE_BUNDLE.getString("singleInstanceCheckDialogMessage"));
            return;
        }
        logger.info("Version {}", buildVersion());
        logger.info("System properties {}", System.getProperties());
        logger.info("Environment {}", System.getenv());
        Application.builder()
                .addModule(() -> settingsModule)
                .addModule(() -> new UiModule(handler -> {
                    checkState(javafxApplicationResourcesHandler.compareAndSet(null, handler), "can only launch once");
                    new Thread(() -> launch(args)).start();
                }))
                .addModule(TimeModule::new)
                .addModule(() -> new CoreDependenciesModule(settingsModule.getAuthDataStoreRootPath()))
                .addModule(() -> GoogleServicesModule.builder()
                        .setAuthDataStoreRootDir(settingsModule.getAuthDataStoreRootPath())
                        .withGoogleApiSettingsCustomiser(builder -> builder.setAuthorizationBrowser(annotatedWith(AuthBrowser.class)))
                        .build())
                .addModule(ResourceBundleModule::new)
                .addModule(UploadPhotosModule::new)
                .addModule(UiAuthorizationBrowserModule::new)
                .build()
                .run();
        Platform.exit();
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setMinWidth(500);
        primaryStage.setMinHeight(500);
        primaryStage.getIcons().add(new Image(getClass().getResource("/Icon1024.png").toString()));
        javafxApplicationResourcesHandler.get().accept(JavafxApplicationResources.builder()
                .setHostServices(getHostServices())
                .setPrimaryStage(primaryStage)
                .build());
    }
}
