package net.yudichev.googlephotosupload.core;

import com.google.inject.Module;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import net.yudichev.jiotty.common.lang.TypedBuilder;
import net.yudichev.jiotty.connector.google.common.GoogleApiAuthSettings;
import net.yudichev.jiotty.connector.google.common.GoogleAuthorizationModule;
import net.yudichev.jiotty.connector.google.drive.GoogleDriveModule;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosModule;

import java.nio.file.Path;
import java.util.function.Consumer;

import static com.google.api.services.drive.DriveScopes.DRIVE_APPDATA;
import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.core.AppGlobals.APP_TITLE;
import static net.yudichev.jiotty.common.inject.BindingSpec.providedBy;
import static net.yudichev.jiotty.connector.google.photos.GooglePhotosScopes.SCOPE_PHOTOS_LIBRARY;

public final class GoogleServicesModule extends BaseLifecycleComponentModule {
    private final Path authDataStoreRootDir;
    private final Consumer<GoogleApiAuthSettings.Builder> googleApiSettingsCustomiser;

    private GoogleServicesModule(Path authDataStoreRootDir,
                                 Consumer<GoogleApiAuthSettings.Builder> googleApiSettingsCustomiser) {
        this.authDataStoreRootDir = checkNotNull(authDataStoreRootDir);
        this.googleApiSettingsCustomiser = checkNotNull(googleApiSettingsCustomiser);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected void configure() {
        // must start before google services, intentionally
        bind(CustomCredentialsManager.class).to(registerLifecycleComponent(CustomCredentialsManagerImpl.class));

        var googleApiSettingsBuilder = GoogleApiAuthSettings.builder()
                .setAuthDataStoreRootDir(authDataStoreRootDir)
                .setApplicationName(APP_TITLE)
                .setCredentialsUrl(providedBy(CustomCredentialsManagerImpl.class));
        googleApiSettingsCustomiser.accept(googleApiSettingsBuilder);
        var settings = googleApiSettingsBuilder.build();
        installLifecycleComponentModule(GoogleAuthorizationModule.builder()
                .setSettings(settings)
                .addRequiredScopes(DRIVE_APPDATA, SCOPE_PHOTOS_LIBRARY)
                .build());
        var photosModule = GooglePhotosModule.builder().build();
        installLifecycleComponentModule(photosModule);
        var driveModule = GoogleDriveModule.builder().build();
        installLifecycleComponentModule(driveModule);

        registerLifecycleComponent(GoogleLoginListener.class);

        expose(CustomCredentialsManager.class);
        expose(photosModule.getExposedKey());
        expose(driveModule.getExposedKey());
    }

    public static final class Builder implements TypedBuilder<Module> {
        private Path authDataStoreRootDir;
        private Consumer<GoogleApiAuthSettings.Builder> googleApiSettingsCustomiser = ignored -> {};

        public Builder setAuthDataStoreRootDir(Path authDataStoreRootDir) {
            this.authDataStoreRootDir = checkNotNull(authDataStoreRootDir);
            return this;
        }

        public Builder withGoogleApiSettingsCustomiser(Consumer<GoogleApiAuthSettings.Builder> googleApiSettingsCustomiser) {
            this.googleApiSettingsCustomiser = checkNotNull(googleApiSettingsCustomiser);
            return this;
        }

        @Override
        public Module build() {
            return new GoogleServicesModule(authDataStoreRootDir, googleApiSettingsCustomiser);
        }
    }
}
