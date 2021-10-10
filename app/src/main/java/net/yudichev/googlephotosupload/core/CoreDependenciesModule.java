package net.yudichev.googlephotosupload.core;

import com.google.inject.Key;
import net.yudichev.jiotty.common.async.ExecutorModule;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.core.Bindings.Backpressured;
import static net.yudichev.googlephotosupload.core.Bindings.GoogleAuthRootDir;

public final class CoreDependenciesModule extends BaseLifecycleComponentModule {
    private final Path authDataStoreRootDir;

    public CoreDependenciesModule(Path authDataStoreRootDir) {
        this.authDataStoreRootDir = checkNotNull(authDataStoreRootDir);
    }

    @Override
    protected void configure() {
        var executorModule = new ExecutorModule();
        installLifecycleComponentModule(executorModule);

        // Google Client components logically depend on this component, because executors are passed directly to the API calls
        // Hence, this service must start before Google Client, and anything else, really, as passing executors to services is a common practice.
        var executorServiceKey = Key.get(ExecutorService.class, Backpressured.class);
        bind(executorServiceKey).toProvider(registerLifecycleComponent(BackpressuredExecutorServiceProvider.class));

        var googleAuthRootDirKey = Key.get(Path.class, GoogleAuthRootDir.class);
        bind(googleAuthRootDirKey).toInstance(authDataStoreRootDir);

        expose(executorModule.getExposedKey());
        expose(executorServiceKey);
        expose(googleAuthRootDirKey);
    }
}
