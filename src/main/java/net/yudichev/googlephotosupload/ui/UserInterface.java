package net.yudichev.googlephotosupload.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;
import net.yudichev.jiotty.common.app.ApplicationLifecycleControl;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static net.yudichev.googlephotosupload.core.AppName.APP_TITLE;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

final class UserInterface extends BaseLifecycleComponent implements Provider<JavafxApplicationResources> {
    private static final Logger logger = LoggerFactory.getLogger(UserInterface.class);

    private final Consumer<Consumer<JavafxApplicationResources>> javafxApplicationResourcesHandler;
    private final FxmlContainerFactory fxmlContainerFactory;
    private final ApplicationLifecycleControl applicationLifecycleControl;
    private volatile JavafxApplicationResources javafxApplicationResources;

    @Inject
    UserInterface(Consumer<Consumer<JavafxApplicationResources>> javafxApplicationResourcesHandler,
                  FxmlContainerFactory fxmlContainerFactory,
                  ApplicationLifecycleControl applicationLifecycleControl) {
        this.javafxApplicationResourcesHandler = checkNotNull(javafxApplicationResourcesHandler);
        this.fxmlContainerFactory = checkNotNull(fxmlContainerFactory);
        this.applicationLifecycleControl = checkNotNull(applicationLifecycleControl);
    }

    @Override
    public JavafxApplicationResources get() {
        return checkNotNull(javafxApplicationResources, "primaryStage is not set");
    }

    @Override
    protected void doStart() {
        if (javafxApplicationResources == null) {
            var initLatch = new CountDownLatch(1);
            javafxApplicationResourcesHandler.accept(javafxApplicationResources -> {
                Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
                    logger.error("Unhandled exception", throwable);
                    applicationLifecycleControl.initiateShutdown();
                });

                this.javafxApplicationResources = javafxApplicationResources;
                var primaryStage = javafxApplicationResources.primaryStage();

                var fxmlContainer = fxmlContainerFactory.create("MainScreen.fxml");
                Parent parent = fxmlContainer.root();
                primaryStage.setScene(new Scene(parent));
                primaryStage.setTitle(APP_TITLE);
                primaryStage.show();
                primaryStage.setOnHiding(e -> applicationLifecycleControl.initiateShutdown());
                initLatch.countDown();
            });
            logger.info("Waiting for 10 seconds until UI is initialized");
            checkState(getAsUnchecked(() -> initLatch.await(10, TimeUnit.SECONDS)), "UI was not initialized in 10 seconds");
        }
    }
}
