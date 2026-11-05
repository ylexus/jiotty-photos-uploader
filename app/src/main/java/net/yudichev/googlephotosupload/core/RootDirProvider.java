package net.yudichev.googlephotosupload.core;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.apache.commons.cli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;

final class RootDirProvider implements Provider<Path> {
    private final Path rootDir;

    @Inject
    RootDirProvider(CommandLine commandLine) {
        rootDir = Paths.get(commandLine.getOptionValue('r'));
    }

    @Override
    public Path get() {
        return rootDir;
    }
}
