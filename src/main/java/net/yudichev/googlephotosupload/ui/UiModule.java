package net.yudichev.googlephotosupload.ui;

import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import javafx.stage.Stage;
import net.yudichev.googlephotosupload.core.ProgressStatus;
import net.yudichev.googlephotosupload.core.ProgressStatusFactory;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import net.yudichev.jiotty.common.time.TimeModule;

import javax.inject.Singleton;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.ui.Bindings.Primary;

final class UiModule extends BaseLifecycleComponentModule {
    private final Consumer<Consumer<Stage>> primaryStageHandler;

    UiModule(Consumer<Consumer<Stage>> primaryStageHandler) {
        this.primaryStageHandler = checkNotNull(primaryStageHandler);
    }

    @Override
    protected void configure() {
        bind(new TypeLiteral<Consumer<Consumer<Stage>>>() {}).annotatedWith(UserInterface.PrimaryStageHandler.class).toInstance(primaryStageHandler);

        bind(MainScreenControllerImpl.class).in(Singleton.class);
        bind(MainScreenController.class).to(MainScreenControllerImpl.class);

        bind(LoginDialogFxControllerImpl.class).in(Singleton.class);
        bind(LoginDialogFxController.class).to(LoginDialogFxControllerImpl.class);

        bind(FolderSelectorControllerImpl.class).in(Singleton.class);
        bind(FolderSelectorController.class).to(FolderSelectorControllerImpl.class);

        Key<UploadPaneControllerImpl> uploadPaneControllerKey = boundLifecycleComponent(UploadPaneControllerImpl.class);
        bind(UploadPaneController.class).to(uploadPaneControllerKey);

        Key<Stage> stageKey = Key.get(Stage.class, Primary.class);
        bind(stageKey).toProvider(boundLifecycleComponent(UserInterface.class)).in(Singleton.class);
        expose(stageKey);

        bind(FxmlContainerFactory.class).to(FxmlContainerFactoryImpl.class);

        install(new FactoryModuleBuilder()
                .implement(ProgressStatusBar.class, ProgressBox.class)
                .build(ProgressStatusBarFactory.class));

        installLifecycleComponentModule(new TimeModule());
        bind(ProgressValueUpdaterFactory.class).annotatedWith(ThrottlingProgressStatus.Delegate.class).to(UiProgressStatusFactory.class);
        install(new FactoryModuleBuilder()
                .implement(ProgressStatus.class, ThrottlingProgressStatus.class)
                .build(ProgressStatusFactory.class));
        expose(ProgressStatusFactory.class);
        expose(FxmlContainerFactory.class);
        expose(MainScreenController.class);

        // needed for FxmlLoader to find them
        expose(MainScreenControllerImpl.class);
        expose(LoginDialogFxControllerImpl.class);
        expose(FolderSelectorControllerImpl.class);
        expose(uploadPaneControllerKey);
    }
}
