package net.yudichev.googlephotosupload.app;

import com.google.api.client.util.BackOff;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;

import javax.inject.Singleton;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

import static net.yudichev.googlephotosupload.app.Bindings.Backpressured;
import static net.yudichev.googlephotosupload.app.Bindings.RootDir;

final class UploadPhotosModule extends BaseLifecycleComponentModule {
    private final int backOffInitialDelayMs;

    UploadPhotosModule(int backOffInitialDelayMs) {
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

        bind(Path.class).annotatedWith(RootDir.class).toProvider(RootDirProvider.class);
        bind(FilesystemManager.class).to(FilesystemManagerImpl.class);

        bind(CloudOperationHelper.class).to(CloudOperationHelperImpl.class);
        bind(CloudAlbumsProvider.class).to(CloudAlbumsProviderImpl.class);

        bind(AlbumManager.class).to(AlbumManagerImpl.class);

        bind(GooglePhotosUploader.class).to(boundLifecycleComponent(GooglePhotosUploaderImpl.class));

        boundLifecycleComponent(UploadPhotos.class);
    }
}
