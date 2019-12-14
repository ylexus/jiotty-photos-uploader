package net.yudichev.googlephotosupload.ui;

import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import javafx.stage.Stage;
import net.yudichev.googlephotosupload.core.ProgressStatus;
import net.yudichev.googlephotosupload.core.ProgressStatusFactory;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import net.yudichev.jiotty.common.lang.throttling.ThresholdThrottlingConsumerModule;
import net.yudichev.jiotty.common.time.TimeModule;

import javax.inject.Singleton;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.ui.Bindings.Primary;
import static net.yudichev.jiotty.common.inject.SpecifiedAnnotation.forAnnotation;

final class UiModule extends BaseLifecycleComponentModule {
    private final Consumer<Consumer<Stage>> primaryStageHandler;

    UiModule(Consumer<Consumer<Stage>> primaryStageHandler) {
        this.primaryStageHandler = checkNotNull(primaryStageHandler);
    }

    @Override
    protected void configure() {
        bind(new TypeLiteral<Consumer<Consumer<Stage>>>() {}).annotatedWith(UserInterface.PrimaryStageHandler.class).toInstance(primaryStageHandler);

        bind(FxController.class).in(Singleton.class);
        bind(UiComponents.class).to(FxController.class);

        Key<Stage> stageKey = Key.get(Stage.class, Primary.class);
        bind(stageKey).toProvider(boundLifecycleComponent(UserInterface.class));
        expose(stageKey);

        bind(FxmlContainerFactory.class).to(FxmlContainerFactoryImpl.class);

        install(new FactoryModuleBuilder()
                .implement(ProgressStatusBar.class, CurrentValueStatusBar.class)
                .build(ProgressStatusBarFactory.class));

        installLifecycleComponentModule(new TimeModule());
        installLifecycleComponentModule(ThresholdThrottlingConsumerModule.builder()
                .setValueType(Runnable.class)
                .withAnnotation(forAnnotation(ThrottlingProgressStatus.Delegate.class))
                .build());
        bind(ProgressStatusFactory.class).annotatedWith(ThrottlingProgressStatus.Delegate.class).to(UiProgressStatusFactory.class);
        install(new FactoryModuleBuilder()
                .implement(ProgressStatus.class, ThrottlingProgressStatus.class)
                .build(ProgressStatusFactory.class));
        expose(ProgressStatusFactory.class);
    }
}
