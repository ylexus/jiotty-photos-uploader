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

        bind(FxController.class).in(Singleton.class);
        bind(MainScreenController.class).to(FxController.class);

        Key<Stage> stageKey = Key.get(Stage.class, Primary.class);
        bind(stageKey).toProvider(boundLifecycleComponent(UserInterface.class));
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
        expose(FxController.class); // needed for FxmlLoader to find it
        expose(MainScreenController.class);
    }
}
