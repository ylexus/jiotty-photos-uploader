package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import org.apache.commons.cli.CommandLine;

import static com.google.common.base.Preconditions.checkNotNull;

public final class IntegrationTestUploadStarterModule extends BaseLifecycleComponentModule {
    private final CommandLine commandLine;
    private final ProgressStatusFactory progressStatusFactory;

    public IntegrationTestUploadStarterModule(CommandLine commandLine, ProgressStatusFactory progressStatusFactory) {
        this.commandLine = checkNotNull(commandLine);
        this.progressStatusFactory = checkNotNull(progressStatusFactory);
    }

    @Override
    protected void configure() {
        bind(CommandLine.class).toInstance(commandLine);
        boundLifecycleComponent(IntegrationTestUploadStarter.class);

        bind(PreferencesSupplier.class).toInstance(() -> Preferences.builder().build());
        expose(PreferencesSupplier.class);

        bind(ProgressStatusFactory.class).toInstance(progressStatusFactory);
        expose(ProgressStatusFactory.class);
    }
}
