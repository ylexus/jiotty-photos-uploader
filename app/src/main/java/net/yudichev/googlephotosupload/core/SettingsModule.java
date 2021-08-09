package net.yudichev.googlephotosupload.core;

import com.google.inject.Key;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import net.yudichev.jiotty.common.varstore.VarStoreModule;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.jiotty.common.inject.BindingSpec.literally;

public final class SettingsModule extends BaseLifecycleComponentModule {
    private final Path settingsRootPath;

    public SettingsModule() {
        this(Paths.get(System.getProperty("user.home"), ".jiottyphotosuploader"));
    }

    public SettingsModule(Path settingsRootPath) {
        this.settingsRootPath = checkNotNull(settingsRootPath);
    }

    public Path getSettingsRootPath() {
        return settingsRootPath;
    }

    public Path getAuthDataStoreRootPath() {
        return settingsRootPath.resolve("auth");
    }

    @Override
    protected void configure() {
        var settingsRootKey = Key.get(Path.class, Bindings.SettingsRoot.class);
        bind(settingsRootKey).toInstance(settingsRootPath);
        expose(settingsRootKey);

        var varStoreModule = VarStoreModule.builder()
                .setPath(literally(settingsRootPath.resolve("data.json")))
                .build();
        installLifecycleComponentModule(varStoreModule);
        expose(varStoreModule.getExposedKey());

        bind(Path.class).annotatedWith(UploadStateManagerImpl.H2DbPath.class).toInstance(settingsRootPath.resolve("data"));
        bind(UploadStateManager.class).to(registerLifecycleComponent(UploadStateManagerImpl.class));
        expose(UploadStateManager.class);
    }
}
