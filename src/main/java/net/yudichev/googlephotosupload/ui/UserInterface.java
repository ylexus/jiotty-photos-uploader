package net.yudichev.googlephotosupload.ui;

import com.google.inject.BindingAnnotation;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;

import javax.inject.Inject;
import javax.inject.Provider;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

final class UserInterface extends BaseLifecycleComponent implements Provider<Stage> {
    private final Consumer<Consumer<Stage>> primaryStageHandler;
    private final FxmlContainerFactory fxmlContainerFactory;
    private volatile Stage primaryStage;

    @Inject
    UserInterface(@PrimaryStageHandler Consumer<Consumer<Stage>> primaryStageHandler,
                  FxmlContainerFactory fxmlContainerFactory) {
        this.primaryStageHandler = checkNotNull(primaryStageHandler);
        this.fxmlContainerFactory = checkNotNull(fxmlContainerFactory);
    }

    @Override
    public Stage get() {
        return checkNotNull(primaryStage, "primaryStage is not set");
    }

    @Override
    protected void doStart() {
        primaryStageHandler.accept(primaryStage -> {
            this.primaryStage = primaryStage;

            Parent parent = fxmlContainerFactory.create("Ui.fxml");
            primaryStage.setScene(new Scene(parent));
            primaryStage.setTitle("Google Photos Uploader");
            primaryStage.show();
            primaryStage.setOnCloseRequest(e -> System.exit(0));
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
