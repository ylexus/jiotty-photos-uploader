package net.yudichev.googlephotosupload.cli;

import net.yudichev.googlephotosupload.core.Preferences;
import net.yudichev.googlephotosupload.core.PreferencesSupplier;
import net.yudichev.googlephotosupload.core.ProgressStatusFactory;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import org.apache.commons.cli.CommandLine;

import static com.google.common.base.Preconditions.checkNotNull;

public final class CliModule extends BaseLifecycleComponentModule {
    private final CommandLine commandLine;

    public CliModule(CommandLine commandLine) {
        this.commandLine = checkNotNull(commandLine);
    }

    @Override
    protected void configure() {
        bind(CommandLine.class).toInstance(commandLine);
        boundLifecycleComponent(CliStarter.class);

        // TODO add taking preferences via command line
        bind(PreferencesSupplier.class).toInstance(() -> Preferences.builder().build());
        expose(PreferencesSupplier.class);

        bind(ProgressStatusFactory.class).to(LoggingProgressStatusFactory.class);
        expose(ProgressStatusFactory.class);
    }
}
