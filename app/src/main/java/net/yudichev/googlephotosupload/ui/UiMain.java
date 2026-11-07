package net.yudichev.googlephotosupload.ui;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import net.yudichev.googlephotosupload.core.*;
import net.yudichev.googlephotosupload.ui.Bindings.AuthBrowser;
import net.yudichev.jiotty.common.app.Application;
import net.yudichev.jiotty.common.time.TimeModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Objects.requireNonNull;
import static net.yudichev.googlephotosupload.core.BuildVersion.buildVersion;
import static net.yudichev.googlephotosupload.core.ResourceBundleModule.RESOURCE_BUNDLE;
import static net.yudichev.googlephotosupload.core.SingleInstanceCheck.otherInstanceRunning;
import static net.yudichev.googlephotosupload.ui.FatalStartupError.showFatalStartupError;
import static net.yudichev.jiotty.common.inject.BindingSpec.annotatedWith;

public final class UiMain extends javafx.application.Application {
    private static final Logger logger = LogManager.getLogger(UiMain.class);
    private static final AtomicReference<Consumer<JavafxApplicationResources>> javafxApplicationResourcesHandler = new AtomicReference<>();
    private static final List<String> DIAGNOSTIC_SYSTEM_PROPERTIES = List.of(
            "os.name", "os.version", "os.arch",
            "java.version", "java.vendor", "java.runtime.version", "java.vm.name",
            "javafx.version", "javafx.runtime.version", "prism.order",
            "user.language", "user.country", "file.encoding", "sun.jnu.encoding", "stdout.encoding");
    /// Set once JavaFX has started. JavaFX can only be launched once per JVM, while every application restart (e.g. on Stop or logout) creates a new
    /// injector, and so a new [UserInterface] that asks for the resources again; later requests get the existing ones.
    private static final AtomicReference<JavafxApplicationResources> javafxApplicationResources = new AtomicReference<>();
    /// What to do if JavaFX is stopped other than by the application shutting down (e.g. Quit from the Dock): set by the current [UserInterface].
    private static volatile Runnable javafxStopHandler = () -> {};

    // run() stops the application before it returns
    @SuppressWarnings("resource")
    public static void main(String[] args) {
        var settingsModule = new SettingsModule();
        if (otherInstanceRunning(settingsModule.getSettingsRootPath())) {
            showFatalStartupError(RESOURCE_BUNDLE.getString("singleInstanceCheckDialogMessage"));
            return;
        }
        logger.info("Version {}", buildVersion());
        // Only properties useful for diagnosing platform issues: users are asked to share logs, and the full property set and the environment carry
        // user names, home paths and, potentially, secrets.
        logger.info("System properties {}", DIAGNOSTIC_SYSTEM_PROPERTIES.stream()
                .filter(name -> System.getProperty(name) != null)
                .collect(toImmutableMap(name -> name, System::getProperty)));
        logger.info("Input Arguments {}", ManagementFactory.getRuntimeMXBean().getInputArguments());
        Application.builder()
                .addModule(() -> settingsModule)
                .addModule(() -> new UiModule(handler -> {
                    var existingResources = javafxApplicationResources.get();
                    if (existingResources == null) {
                        checkState(javafxApplicationResourcesHandler.compareAndSet(null, handler), "can only launch once");
                        new Thread(() -> launch(args)).start();
                    } else {
                        Platform.runLater(() -> handler.accept(existingResources));
                    }
                }))
                .addModule(() -> TimeModule.builder().build())
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
        javafxStopHandler = () -> {};
        Platform.exit();
    }

    static void setJavafxStopHandler(Runnable handler) {
        javafxStopHandler = handler;
    }

    @Override
    public void stop() {
        logger.info("JavaFX is stopping");
        javafxStopHandler.run();
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setMinWidth(500);
        primaryStage.setMinHeight(500);
        primaryStage.getIcons().add(new Image(requireNonNull(getClass().getResource("/Icon1024.png")).toString()));
        var resources = JavafxApplicationResources.builder()
                .setHostServices(getHostServices())
                .setPrimaryStage(primaryStage)
                .build();
        javafxApplicationResources.set(resources);
        javafxApplicationResourcesHandler.get().accept(resources);
    }
}
