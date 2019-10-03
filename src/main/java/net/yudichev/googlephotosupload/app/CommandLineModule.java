package net.yudichev.googlephotosupload.app;

import com.google.inject.AbstractModule;
import org.apache.commons.cli.CommandLine;

import static com.google.common.base.Preconditions.checkNotNull;

final class CommandLineModule extends AbstractModule {
    private CommandLine commandLine;

    CommandLineModule(CommandLine commandLine) {
        this.commandLine = checkNotNull(commandLine);
    }

    @Override
    protected void configure() {
        bind(CommandLine.class).toInstance(commandLine);
    }
}
