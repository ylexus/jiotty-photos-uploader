package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import org.apache.commons.cli.CommandLine;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

public final class IntegrationTestUploadStarterModule extends BaseLifecycleComponentModule {
    private static Preferences preferences = Preferences.builder().setAddToAlbumStrategy(AddToAlbumMethod.WHILE_CREATING_ITEMS).build();
    private final CommandLine commandLine;
    private final ProgressStatusFactory progressStatusFactory;

    public IntegrationTestUploadStarterModule(CommandLine commandLine, ProgressStatusFactory progressStatusFactory) {
        this.commandLine = checkNotNull(commandLine);
        this.progressStatusFactory = checkNotNull(progressStatusFactory);
    }

    public static void setPreferences(Preferences preferences) {
        IntegrationTestUploadStarterModule.preferences = checkNotNull(preferences);
    }

    @Override
    protected void configure() {
        bind(CommandLine.class).toInstance(commandLine);
        boundLifecycleComponent(IntegrationTestUploadStarter.class);

        bind(PreferencesManager.class).toInstance(new PreferencesManager() {
            @Override
            public void update(Function<Preferences, Preferences> updater) {
            }

            @Override
            public Preferences get() {
                return preferences;
            }
        });
        expose(PreferencesManager.class);

        bind(ProgressStatusFactory.class).toInstance(progressStatusFactory);
        expose(ProgressStatusFactory.class);
    }
}
