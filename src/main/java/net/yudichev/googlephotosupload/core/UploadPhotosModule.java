package net.yudichev.googlephotosupload.core;

import com.google.api.client.util.BackOff;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import net.yudichev.jiotty.common.inject.ExposedKeyModule;

import javax.inject.Singleton;
import java.util.concurrent.ExecutorService;

import static net.yudichev.googlephotosupload.core.Bindings.Backpressured;

@SuppressWarnings("OverlyCoupledClass") // OK for module
public final class UploadPhotosModule extends BaseLifecycleComponentModule implements ExposedKeyModule<Uploader> {
    private final int backOffInitialDelayMs;

    public UploadPhotosModule(int backOffInitialDelayMs) {
        this.backOffInitialDelayMs = backOffInitialDelayMs;
    }

    @Override
    protected void configure() {
        bind(BuildVersion.class).asEagerSingleton();

        install(new FactoryModuleBuilder()
                .implement(StateSaver.class, StateSaverImpl.class)
                .build(StateSaverFactory.class));

        bind(ExecutorService.class).annotatedWith(Backpressured.class)
                .toProvider(boundLifecycleComponent(BackpressuredExecutorServiceProvider.class));

        bind(DirectoryStructureSupplier.class).to(DirectoryStructureSupplierImpl.class);

        bindConstant().annotatedWith(BackOffProvider.InitialDelayMs.class).to(backOffInitialDelayMs);
        bind(BackOff.class).annotatedWith(BackingOffRemoteApiExceptionHandlerImpl.Dependency.class).toProvider(BackOffProvider.class);
        bind(BackingOffRemoteApiExceptionHandler.class).to(BackingOffRemoteApiExceptionHandlerImpl.class);
        bind(FatalUserCorrectableRemoteApiExceptionHandler.class).to(FatalUserCorrectableRemoteApiExceptionHandlerImpl.class);

        bind(CloudOperationHelper.class).to(CloudOperationHelperImpl.class);
        bind(CloudAlbumsProvider.class).to(boundLifecycleComponent(CloudAlbumsProviderImpl.class));

        bind(AlbumManager.class).to(boundLifecycleComponent(AlbumManagerImpl.class));

        bind(UploadStateManager.class).to(UploadStateManagerImpl.class).in(Singleton.class);
        bind(AddToAlbumStrategy.class)
                .annotatedWith(SelectingAddToAlbumStrategy.WhileCreatingItems.class)
                .to(AddToAlbumWhileCreatingStrategy.class)
                .in(Singleton.class);
        bind(AddToAlbumStrategy.class)
                .annotatedWith(SelectingAddToAlbumStrategy.AfterCreatingItemsSorted.class)
                .to(AddToAlbumAfterCreatingStrategy.class)
                .in(Singleton.class);
        bind(AddToAlbumStrategy.class).to(SelectingAddToAlbumStrategy.class);
        bind(GooglePhotosUploader.class).to(boundLifecycleComponent(GooglePhotosUploaderImpl.class));

        bind(getExposedKey()).to(UploaderImpl.class);
        expose(getExposedKey());
    }
}
