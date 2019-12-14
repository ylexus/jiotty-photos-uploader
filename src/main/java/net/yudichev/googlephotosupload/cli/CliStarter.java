package net.yudichev.googlephotosupload.cli;

import net.yudichev.googlephotosupload.core.Uploader;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import org.apache.commons.cli.CommandLine;

import javax.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.google.common.base.Preconditions.checkNotNull;

final class CliStarter extends BaseLifecycleComponent {
    private final Path rootDir;
    private final Uploader uploader;

    @Inject
    CliStarter(CommandLine commandLine,
               Uploader uploader) {
        rootDir = Paths.get(commandLine.getOptionValue('r'));
        this.uploader = checkNotNull(uploader);
    }

    @Override
    protected void doStart() {
        uploader.start(rootDir);
    }
}
