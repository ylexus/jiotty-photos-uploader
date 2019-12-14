package net.yudichev.googlephotosupload.cli;

import net.yudichev.googlephotosupload.core.ProgressStatusFactory;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import net.yudichev.jiotty.common.inject.ExposedKeyModule;
import org.apache.commons.cli.CommandLine;

import static com.google.common.base.Preconditions.checkNotNull;

public final class CliStarterModule extends BaseLifecycleComponentModule implements ExposedKeyModule<ProgressStatusFactory> {
    private final CommandLine commandLine;

    public CliStarterModule(CommandLine commandLine) {
        this.commandLine = checkNotNull(commandLine);
    }

    @Override
    protected void configure() {
        bind(CommandLine.class).toInstance(commandLine);
        boundLifecycleComponent(CliStarter.class);

        bind(getExposedKey()).to(LoggingProgressStatusFactory.class);
        expose(getExposedKey());
    }
}
