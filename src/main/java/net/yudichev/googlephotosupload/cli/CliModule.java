package net.yudichev.googlephotosupload.cli;

import net.yudichev.googlephotosupload.core.PreferencesManager;
import net.yudichev.googlephotosupload.core.ProgressStatusFactory;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import org.apache.commons.cli.CommandLine;

import javax.inject.Singleton;

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

        bind(PreferencesManager.class).to(PreferencesManagerImpl.class).in(Singleton.class);
        expose(PreferencesManager.class);

        bind(ProgressStatusFactory.class).to(LoggingProgressStatusFactory.class);
        expose(ProgressStatusFactory.class);
    }
}
