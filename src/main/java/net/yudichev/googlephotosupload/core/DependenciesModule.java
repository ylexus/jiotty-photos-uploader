package net.yudichev.googlephotosupload.core;

import com.google.inject.AbstractModule;
import net.yudichev.jiotty.common.async.ExecutorModule;
import net.yudichev.jiotty.common.varstore.VarStoreModule;
import net.yudichev.jiotty.connector.google.common.GoogleApiAuthSettings;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosModule;

import java.nio.file.Paths;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.Resources.getResource;

public final class DependenciesModule extends AbstractModule {
    private final Consumer<GoogleApiAuthSettings.Builder> googleApiSettingsCustomiser;

    public DependenciesModule() {
        this(builder -> {});
    }

    public DependenciesModule(Consumer<GoogleApiAuthSettings.Builder> googleApiSettingsCustomiser) {
        this.googleApiSettingsCustomiser = checkNotNull(googleApiSettingsCustomiser);
    }

    @Override
    protected void configure() {
        install(new ExecutorModule());
        install(new VarStoreModule("googlephotosupload"));
        GoogleApiAuthSettings.Builder googleApiSettingsBuilder = GoogleApiAuthSettings.builder()
                .setAuthDataStoreRootDir(Paths.get(System.getProperty("user.home")).resolve(".jiottyphotosuploader").resolve("auth"))
                .setApplicationName("Jiotty Photos Uploader")
                .setCredentialsUrl(getResource("client_secret_641898159424-0tmk9ngs1aog13ef0v4bg1njtnndj1c3.apps.googleusercontent.com.json"));
        googleApiSettingsCustomiser.accept(googleApiSettingsBuilder);
        install(GooglePhotosModule.builder()
                .setSettings(googleApiSettingsBuilder.build())
                .build());
    }
}
