package net.yudichev.googlephotosupload.cli;

import com.google.common.collect.ImmutableList;
import net.yudichev.googlephotosupload.core.Uploader;
import net.yudichev.jiotty.common.app.ApplicationLifecycleControl;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import org.apache.commons.cli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ResourceBundle;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.jiotty.common.lang.CompletableFutures.logErrorOnFailure;

final class CliStarter extends BaseLifecycleComponent {
    private static final Logger logger = LoggerFactory.getLogger(CliStarter.class);
    private final Path rootDir;
    private final Uploader uploader;
    private final ApplicationLifecycleControl applicationLifecycleControl;
    private final ResourceBundle resourceBundle;
    private final boolean resume;

    @Inject
    CliStarter(CommandLine commandLine,
               Uploader uploader,
               ApplicationLifecycleControl applicationLifecycleControl,
               ResourceBundle resourceBundle) {
        rootDir = Paths.get(commandLine.getOptionValue('r'));
        resume = !commandLine.hasOption('n');
        this.uploader = checkNotNull(uploader);
        this.applicationLifecycleControl = checkNotNull(applicationLifecycleControl);
        this.resourceBundle = checkNotNull(resourceBundle);
    }

    @Override
    protected void doStart() {
        logger.info(resourceBundle.getString("googleStorageWarning"));
        uploader.upload(ImmutableList.of(rootDir), resume)
                .whenComplete(logErrorOnFailure(logger, "Failed"))
                .whenComplete((ignored1, ignored2) -> applicationLifecycleControl.initiateShutdown());
    }
}
