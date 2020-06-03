package net.yudichev.googlephotosupload.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import net.yudichev.googlephotosupload.core.DependenciesModule;
import net.yudichev.googlephotosupload.core.ResourceBundleModule;
import net.yudichev.googlephotosupload.core.UploadPhotosModule;
import net.yudichev.googlephotosupload.ui.Bindings.AuthBrowser;
import net.yudichev.jiotty.common.app.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkState;
import static javafx.scene.control.Alert.AlertType.ERROR;
import static javafx.scene.control.Alert.AlertType.INFORMATION;
import static javafx.scene.input.KeyCombination.*;
import static javafx.scene.input.KeyEvent.KEY_RELEASED;
import static net.yudichev.googlephotosupload.core.BuildVersion.buildVersion;
import static net.yudichev.jiotty.common.inject.BindingSpec.annotatedWith;
import static net.yudichev.jiotty.common.lang.HumanReadableExceptionMessage.humanReadableMessage;

public final class UiMain extends javafx.application.Application {
    private static final Logger logger = LoggerFactory.getLogger(UiMain.class);
    private static final AtomicReference<Consumer<JavafxApplicationResources>> javafxApplicationResourcesHandler = new AtomicReference<>();

    public static void main(String[] args) {
        if (SingleInstanceCheck.otherInstanceRunning()) {
            return;
        }
        logger.info("Version {}", buildVersion());
        Application.builder()
                .addModule(() -> new UiModule(handler -> {
                    checkState(javafxApplicationResourcesHandler.compareAndSet(null, handler), "can only launch once");
                    new Thread(() -> launch(args)).start();
                }))
                .addModule(() -> DependenciesModule.builder()
                        .withGoogleApiSettingsCustomiser(builder -> builder.setAuthorizationBrowser(annotatedWith(AuthBrowser.class)))
                        .build())
                .addModule(ResourceBundleModule::new)
                .addModule(() -> new UploadPhotosModule(1000))
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
        installDiagnosticsHotkeys(primaryStage);
        javafxApplicationResourcesHandler.get().accept(JavafxApplicationResources.builder()
                .setHostServices(getHostServices())
                .setPrimaryStage(primaryStage)
                .build());
    }

    private static void installDiagnosticsHotkeys(Stage primaryStage) {
        KeyCombination threadDumpCombination = new KeyCodeCombination(KeyCode.D, CONTROL_DOWN, ALT_DOWN, SHIFT_DOWN);
        primaryStage.addEventHandler(KEY_RELEASED, event -> {
            if (threadDumpCombination.match(event)) {
                new Dumps().writeSeveralThreadDumpsAsync();
            }
        });
        KeyCombination heapDumpCombination = new KeyCodeCombination(KeyCode.H, CONTROL_DOWN, ALT_DOWN, SHIFT_DOWN);
        primaryStage.addEventHandler(KEY_RELEASED, event -> {
            if (heapDumpCombination.match(event)) {
                Dumps.writeHeapDump()
                        .whenComplete((path, e) -> Platform.runLater(() -> {
                            if (e != null) {
                                logger.error("Failed to write heap dump", e);
                                var alert = new Alert(ERROR, humanReadableMessage(e), ButtonType.OK);
                                alert.setHeaderText("Failed to write heap dump");
                                alert.showAndWait();
                            } else {
                                var alert = new Alert(INFORMATION, "Heap dump written to " + path.toAbsolutePath(), ButtonType.OK);
                                alert.setHeaderText("Heap dump");
                                alert.showAndWait();
                            }
                        }));
            }
        });
    }
}
