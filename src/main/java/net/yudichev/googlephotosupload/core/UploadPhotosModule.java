package net.yudichev.googlephotosupload.core;

import com.google.inject.assistedinject.FactoryModuleBuilder;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import net.yudichev.jiotty.common.inject.ExposedKeyModule;

import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.core.Bindings.Backpressured;

@SuppressWarnings({"OverlyCoupledClass", "OverlyCoupledMethod"}) // OK for module
public final class UploadPhotosModule extends BaseLifecycleComponentModule implements ExposedKeyModule<Uploader> {
    private final Duration normalBackOffInitialDelay;
    private final Duration resourceExhaustedBackOffInitialDelay;

    public UploadPhotosModule() {
        // defaults as per https://developers.google.com/photos/library/guides/best-practices#retrying-failed-requests
        normalBackOffInitialDelay = Duration.ofSeconds(1);
        resourceExhaustedBackOffInitialDelay = Duration.ofSeconds(30);
    }

    UploadPhotosModule(Duration backOffInitialDelayMs) {
        normalBackOffInitialDelay = checkNotNull(backOffInitialDelayMs);
        resourceExhaustedBackOffInitialDelay = backOffInitialDelayMs;
    }

    @Override
    protected void configure() {
        bind(BuildVersion.class).asEagerSingleton();
        boundLifecycleComponent(LegacyLogCleaner.class);

        install(new FactoryModuleBuilder()
                .implement(StateSaver.class, StateSaverImpl.class)
                .build(StateSaverFactory.class));

        bind(ExecutorService.class).annotatedWith(Backpressured.class)
                .toProvider(boundLifecycleComponent(BackpressuredExecutorServiceProvider.class));

        bind(DirectoryStructureSupplier.class).to(DirectoryStructureSupplierImpl.class);

        bind(Duration.class).annotatedWith(BackingOffRemoteApiExceptionHandlerImpl.NormalBackoffInitialDelay.class)
                .toInstance(normalBackOffInitialDelay);
        bind(Duration.class).annotatedWith(BackingOffRemoteApiExceptionHandlerImpl.ResourceExhaustedBackoffInitialDelay.class)
                .toInstance(resourceExhaustedBackOffInitialDelay);
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
