package net.yudichev.googlephotosupload.cli;

import net.yudichev.googlephotosupload.core.Uploader;
import net.yudichev.jiotty.common.app.ApplicationLifecycleControl;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import org.apache.commons.cli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.jiotty.common.lang.CompletableFutures.logErrorOnFailure;

final class CliStarter extends BaseLifecycleComponent {
    private static final Logger logger = LoggerFactory.getLogger(CliStarter.class);
    private final Path rootDir;
    private final Uploader uploader;
    private final ApplicationLifecycleControl applicationLifecycleControl;
    private final boolean resume;

    @Inject
    CliStarter(CommandLine commandLine,
               Uploader uploader,
               ApplicationLifecycleControl applicationLifecycleControl) {
        rootDir = Paths.get(commandLine.getOptionValue('r'));
        resume = !commandLine.hasOption('n');
        this.uploader = checkNotNull(uploader);
        this.applicationLifecycleControl = checkNotNull(applicationLifecycleControl);
    }

    @Override
    protected void doStart() {
        uploader.upload(rootDir, resume)
                .whenComplete(logErrorOnFailure(logger, "Failed"))
                .whenComplete((ignored1, ignored2) -> applicationLifecycleControl.initiateShutdown());
    }
}
