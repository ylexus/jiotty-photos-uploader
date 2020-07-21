package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import org.apache.commons.cli.CommandLine;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.core.AddToAlbumMethod.AFTER_CREATING_ITEMS_SORTED;

public final class IntegrationTestUploadStarterModule extends BaseLifecycleComponentModule {
    private static Preferences preferences;

    static {
        setDefaultPreferences();
    }

    private final CommandLine commandLine;
    private final ProgressStatusFactory progressStatusFactory;

    public IntegrationTestUploadStarterModule(CommandLine commandLine, ProgressStatusFactory progressStatusFactory) {
        this.commandLine = checkNotNull(commandLine);
        this.progressStatusFactory = checkNotNull(progressStatusFactory);
    }

    public static void setDefaultPreferences() {
        preferences = Preferences.builder().setAddToAlbumStrategy(AFTER_CREATING_ITEMS_SORTED).build();
    }

    public static void modifyPreferences(Function<Preferences, Preferences> modifier) {
        preferences = modifier.apply(preferences);
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
