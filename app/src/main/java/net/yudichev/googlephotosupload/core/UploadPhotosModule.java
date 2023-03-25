package net.yudichev.googlephotosupload.core;

import com.google.inject.TypeLiteral;
import net.yudichev.jiotty.common.async.AsyncOperationFailureHandler;
import net.yudichev.jiotty.common.async.AsyncOperationRetry;
import net.yudichev.jiotty.common.async.AsyncOperationRetryImpl;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import net.yudichev.jiotty.common.inject.ExposedKeyModule;

import javax.inject.Singleton;
import java.time.Duration;
import java.util.Optional;

@SuppressWarnings({"OverlyCoupledClass", "OverlyCoupledMethod"}) // OK for module
public final class UploadPhotosModule extends BaseLifecycleComponentModule implements ExposedKeyModule<Uploader> {
    private final Optional<Duration> globalInitialDelayOverride;

    public UploadPhotosModule() {
        globalInitialDelayOverride = Optional.empty();
    }

    UploadPhotosModule(Duration globalOverride) {
        globalInitialDelayOverride = Optional.of(globalOverride);
    }

    @Override
    protected void configure() {
        bind(BuildVersion.class).asEagerSingleton();
        registerLifecycleComponent(LegacyLogCleaner.class);
        registerLifecycleComponent(LegacyAuthCleaner.class);

        bind(DirectoryStructureSupplier.class).to(DirectoryStructureSupplierImpl.class);

        bind(new TypeLiteral<Optional<Duration>>() {}).annotatedWith(BackingOffRemoteApiExceptionHandlerImpl.GlobalInitialDelayOverride.class)
                .toInstance(globalInitialDelayOverride);
        bind(AsyncOperationFailureHandler.class).to(BackingOffRemoteApiExceptionHandlerImpl.class);
        bind(FatalUserCorrectableRemoteApiExceptionHandler.class).to(FatalUserCorrectableRemoteApiExceptionHandlerImpl.class);

        bind(AsyncOperationRetry.class).to(AsyncOperationRetryImpl.class);
        bind(CloudAlbumsProvider.class).to(registerLifecycleComponent(CloudAlbumsProviderImpl.class));

        bind(AlbumManager.class).to(registerLifecycleComponent(AlbumManagerImpl.class));

        bind(AddToAlbumStrategy.class)
                .annotatedWith(SelectingAddToAlbumStrategy.WhileCreatingItems.class)
                .to(AddToAlbumWhileCreatingStrategy.class)
                .in(Singleton.class);
        bind(AddToAlbumStrategy.class)
                .annotatedWith(SelectingAddToAlbumStrategy.AfterCreatingItemsSorted.class)
                .to(AddToAlbumAfterCreatingStrategy.class)
                .in(Singleton.class);
        bind(AddToAlbumStrategy.class).to(SelectingAddToAlbumStrategy.class);
        bind(DriveSpaceTracker.class).to(DriveSpaceTrackerImpl.class).in(Singleton.class);
        bind(GooglePhotosUploader.class).to(registerLifecycleComponent(GooglePhotosUploaderImpl.class));

        bind(getExposedKey()).to(UploaderImpl.class);
        expose(getExposedKey());
    }
}
