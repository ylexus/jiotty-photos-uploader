package net.yudichev.googlephotosupload.core;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import net.yudichev.jiotty.common.async.ExecutorModule;
import net.yudichev.jiotty.common.lang.TypedBuilder;
import net.yudichev.jiotty.common.time.TimeModule;
import net.yudichev.jiotty.common.varstore.VarStoreModule;
import net.yudichev.jiotty.connector.google.common.GoogleApiAuthSettings;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosModule;

import javax.inject.Singleton;
import java.nio.file.Path;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.core.AppGlobals.*;
import static net.yudichev.jiotty.common.inject.BindingSpec.providedBy;

public final class DependenciesModule extends AbstractModule {
    private final Consumer<GoogleApiAuthSettings.Builder> googleApiSettingsCustomiser;

    private DependenciesModule(Consumer<GoogleApiAuthSettings.Builder> googleApiSettingsCustomiser) {
        this.googleApiSettingsCustomiser = checkNotNull(googleApiSettingsCustomiser);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected void configure() {
        install(new TimeModule());
        install(new ExecutorModule());
        install(new VarStoreModule(APP_SETTINGS_DIR_NAME));

        var authDataStoreRootDir = APP_SETTINGS_AUTH_DIR;
        bind(Restarter.class).to(RestarterImpl.class);
        bind(Path.class).annotatedWith(RestarterImpl.GoogleAuthRootDir.class).toInstance(authDataStoreRootDir);

        bind(CustomCredentialsManagerImpl.class).in(Singleton.class);
        bind(CustomCredentialsManager.class).to(CustomCredentialsManagerImpl.class);
        var googleApiSettingsBuilder = GoogleApiAuthSettings.builder()
                .setAuthDataStoreRootDir(authDataStoreRootDir)
                .setApplicationName(APP_TITLE)
                .setCredentialsUrl(providedBy(CustomCredentialsManagerImpl.class));
        googleApiSettingsCustomiser.accept(googleApiSettingsBuilder);
        install(GooglePhotosModule.builder()
                .setSettings(googleApiSettingsBuilder.build())
                .build());
    }

    public static final class Builder implements TypedBuilder<Module> {
        private Consumer<GoogleApiAuthSettings.Builder> googleApiSettingsCustomiser = ignored -> {};

        public Builder withGoogleApiSettingsCustomiser(Consumer<GoogleApiAuthSettings.Builder> googleApiSettingsCustomiser) {
            this.googleApiSettingsCustomiser = checkNotNull(googleApiSettingsCustomiser);
            return this;
        }

        @Override
        public Module build() {
            return new DependenciesModule(googleApiSettingsCustomiser);
        }
    }
}
