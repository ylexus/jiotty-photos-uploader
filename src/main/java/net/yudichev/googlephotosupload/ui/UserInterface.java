package net.yudichev.googlephotosupload.ui;

import com.google.inject.BindingAnnotation;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.yudichev.jiotty.common.app.ApplicationLifecycleControl;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

final class UserInterface extends BaseLifecycleComponent implements Provider<Stage> {
    private static final Logger logger = LoggerFactory.getLogger(UserInterface.class);

    private final Consumer<Consumer<Stage>> primaryStageHandler;
    private final FxmlContainerFactory fxmlContainerFactory;
    private final ApplicationLifecycleControl applicationLifecycleControl;
    private volatile Stage primaryStage;

    @Inject
    UserInterface(@PrimaryStageHandler Consumer<Consumer<Stage>> primaryStageHandler,
                  FxmlContainerFactory fxmlContainerFactory,
                  ApplicationLifecycleControl applicationLifecycleControl) {
        this.primaryStageHandler = checkNotNull(primaryStageHandler);
        this.fxmlContainerFactory = checkNotNull(fxmlContainerFactory);
        this.applicationLifecycleControl = checkNotNull(applicationLifecycleControl);
    }

    @Override
    public Stage get() {
        return checkNotNull(primaryStage, "primaryStage is not set");
    }

    @Override
    protected void doStart() {
        primaryStageHandler.accept(primaryStage -> {
            Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
                logger.error("Unhandled exception", throwable);
                applicationLifecycleControl.initiateShutdown();
            });

            this.primaryStage = primaryStage;

            FxmlContainer fxmlContainer = fxmlContainerFactory.create("Ui.fxml");
            Parent parent = fxmlContainer.root();
            primaryStage.setScene(new Scene(parent));
            primaryStage.setTitle("Google Photos Uploader");
            primaryStage.show();
            primaryStage.setOnCloseRequest(e -> applicationLifecycleControl.initiateShutdown());
        });
    }

    @Override
    protected void doStop() {
        Platform.exit();
    }

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface PrimaryStageHandler {
    }
}
