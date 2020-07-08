package net.yudichev.googlephotosupload.ui;

import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import net.yudichev.googlephotosupload.core.PreferencesManager;
import net.yudichev.googlephotosupload.core.ProgressStatus;
import net.yudichev.googlephotosupload.core.ProgressStatusFactory;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;

import javax.inject.Singleton;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.ui.OperatingSystemDetection.OSType.MacOS;
import static net.yudichev.googlephotosupload.ui.OperatingSystemDetection.getOperatingSystemType;

@SuppressWarnings({"OverlyCoupledMethod", "OverlyCoupledClass"}) // OK for module
final class UiModule extends BaseLifecycleComponentModule {
    private final Consumer<Consumer<JavafxApplicationResources>> javafxApplicationResourcesHandler;

    UiModule(Consumer<Consumer<JavafxApplicationResources>> javafxApplicationResourcesHandler) {
        this.javafxApplicationResourcesHandler = checkNotNull(javafxApplicationResourcesHandler);
    }

    @Override
    protected void configure() {
        bind(new TypeLiteral<Consumer<Consumer<JavafxApplicationResources>>>() {}).toInstance(javafxApplicationResourcesHandler);

        install(new FactoryModuleBuilder()
                .implement(Dialog.class, DialogImpl.class)
                .build(DialogFactory.class));

        bindControllers();

        bind(SupportPaneController.class).in(Singleton.class);

        bind(JavafxApplicationResources.class).toProvider(boundLifecycleComponent(UserInterface.class)).in(Singleton.class);
        expose(JavafxApplicationResources.class);

        bind(FxmlContainerFactory.class).to(FxmlContainerFactoryImpl.class);

        install(new FactoryModuleBuilder()
                .implement(ProgressStatusBar.class, ProgressStatusBarImpl.class)
                .build(ProgressStatusBarFactory.class));

        bind(ProgressValueUpdaterFactory.class).annotatedWith(ThrottlingProgressStatus.Delegate.class).to(UiProgressStatusFactory.class);
        install(new FactoryModuleBuilder()
                .implement(ProgressStatus.class, ThrottlingProgressStatus.class)
                .build(ProgressStatusFactory.class));

        boundLifecycleComponent(VersionCheck.class);
        boundLifecycleComponent(Diagnostics.class);

        expose(ProgressStatusFactory.class);
        expose(FxmlContainerFactory.class);
        expose(MainScreenController.class);

        expose(PreferencesManager.class);
        expose(DialogFactory.class);
    }

    private void bindControllers() {
        bind(PlatformSpecificMenu.class).to(getOperatingSystemType() == MacOS ? MacPlatformSpecificMenu.class : DefaultPlatformSpecificMenu.class);
        bind(MainScreenControllerImpl.class).in(Singleton.class);
        bind(MainScreenController.class).to(MainScreenControllerImpl.class);

        bind(LoginDialogControllerImpl.class).in(Singleton.class);
        bind(LoginDialogFxController.class).to(LoginDialogControllerImpl.class);

        bind(FolderSelectorController.class).to(boundLifecycleComponent(FolderSelectorControllerImpl.class));

        bind(PreferencesDialogController.class).in(Singleton.class);
        bind(PreferencesManager.class).to(PreferencesDialogController.class);

        var uploadPaneControllerKey = boundLifecycleComponent(UploadPaneControllerImpl.class);
        bind(UploadPaneController.class).to(uploadPaneControllerKey);

        bind(UploaderStrategyChoicePanelControllerImpl.class).in(Singleton.class);
        bind(UploaderStrategyChoicePanelController.class).toProvider(UploaderStrategyChoicePanelControllerProvider.class).in(Singleton.class);

        // needed for FxmlLoader to find them
        expose(MainScreenControllerImpl.class);
        expose(LoginDialogControllerImpl.class);
        expose(FolderSelectorControllerImpl.class);
        expose(PreferencesDialogController.class);
        expose(SupportPaneController.class);
        expose(UploaderStrategyChoicePanelController.class);
        expose(UploaderStrategyChoicePanelControllerImpl.class);
        expose(uploadPaneControllerKey);
    }
}
