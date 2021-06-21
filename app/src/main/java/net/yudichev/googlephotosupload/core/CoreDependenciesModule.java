package net.yudichev.googlephotosupload.core;

import com.google.inject.AbstractModule;
import net.yudichev.jiotty.common.async.ExecutorModule;

import java.nio.file.Path;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.core.Bindings.GoogleAuthRootDir;

public final class CoreDependenciesModule extends AbstractModule {
    private final Path authDataStoreRootDir;

    public CoreDependenciesModule(Path authDataStoreRootDir) {
        this.authDataStoreRootDir = checkNotNull(authDataStoreRootDir);
    }

    @Override
    protected void configure() {
        install(new ExecutorModule());

        bind(Path.class).annotatedWith(GoogleAuthRootDir.class).toInstance(authDataStoreRootDir);
    }
}
