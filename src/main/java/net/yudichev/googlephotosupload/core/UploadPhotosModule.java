package net.yudichev.googlephotosupload.core;

import com.google.api.client.util.BackOff;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import net.yudichev.jiotty.common.inject.ExposedKeyModule;

import javax.inject.Singleton;
import java.util.concurrent.ExecutorService;

import static net.yudichev.googlephotosupload.core.Bindings.Backpressured;

public final class UploadPhotosModule extends BaseLifecycleComponentModule implements ExposedKeyModule<Uploader> {
    private final int backOffInitialDelayMs;

    public UploadPhotosModule(int backOffInitialDelayMs) {
        this.backOffInitialDelayMs = backOffInitialDelayMs;
    }

    @Override
    protected void configure() {
        install(new FactoryModuleBuilder()
                .implement(StateSaver.class, StateSaverImpl.class)
                .build(StateSaverFactory.class));

        bind(ExecutorService.class).annotatedWith(Backpressured.class)
                .toProvider(boundLifecycleComponent(BackpressuredExecutorServiceProvider.class))
                .in(Singleton.class);

        bind(DirectoryStructureSupplier.class).to(DirectoryStructureSupplierImpl.class);

        bindConstant().annotatedWith(BackOffProvider.InitialDelayMs.class).to(backOffInitialDelayMs);
        bind(BackOff.class).annotatedWith(BackingOffRemoteApiResultHandler.Dependency.class).toProvider(BackOffProvider.class);
        bind(RemoteApiResultHandler.class).annotatedWith(Bindings.Backoff.class).to(BackingOffRemoteApiResultHandler.class);
        bind(RemoteApiResultHandler.class).annotatedWith(GooglePhotosUploaderImpl.InvalidMediaItem.class).to(InvalidMediaItemRemoteApiResultHandler.class);

        bind(FilesystemManager.class).to(FilesystemManagerImpl.class);

        bind(CloudOperationHelper.class).to(CloudOperationHelperImpl.class);
        bind(CloudAlbumsProvider.class).to(boundLifecycleComponent(CloudAlbumsProviderImpl.class));

        bind(AlbumManager.class).to(boundLifecycleComponent(AlbumManagerImpl.class));

        bind(GooglePhotosUploader.class).to(boundLifecycleComponent(GooglePhotosUploaderImpl.class));

        bind(getExposedKey()).to(UploaderImpl.class);
        expose(getExposedKey());
    }
}
