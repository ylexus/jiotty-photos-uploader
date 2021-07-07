package net.yudichev.googlephotosupload.cli;

import net.yudichev.googlephotosupload.core.*;
import net.yudichev.jiotty.common.app.Application;
import net.yudichev.jiotty.common.time.TimeModule;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

import static net.yudichev.googlephotosupload.core.BuildVersion.buildVersion;
import static net.yudichev.googlephotosupload.core.SingleInstanceCheck.otherInstanceRunning;

public final class CliMain {
    private static final Logger logger = LoggerFactory.getLogger(CliMain.class);

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        var exitCode = 0;
        try {
            var commandLine = parser.parse(CliOptions.OPTIONS, args);
            var helpRequested = commandLine.hasOption('h');
            if (helpRequested) {
                printHelp();
            }
            var versionRequested = commandLine.hasOption('v');
            if (versionRequested) {
                logger.info("Version {}", buildVersion());
            }
            if (commandLine.hasOption('r')) {
                var settingsModule = new SettingsModule();
                if (otherInstanceRunning(settingsModule.getSettingsRootPath())) {
                    logger.error("Another copy of the app is already running");
                    exitCode = 1;
                } else {
                    startApp(settingsModule, commandLine);
                    if (!CliStarter.isCompletedSuccessfully()) {
                        exitCode = 2;
                    }
                }
            } else if (!helpRequested && !versionRequested) {
                logger.error("Missing option -r");
                exitCode = 3;
                printHelp();
            }
        } catch (ParseException e) {
            logger.error(e.getMessage());
            exitCode = 4;
            printHelp();
        } finally {
            LogManager.shutdown();
        }
        System.exit(exitCode);
    }

    private static void startApp(SettingsModule settingsModule, CommandLine commandLine) {
        Application.builder()
                .addModule(() -> settingsModule)
                .addModule(TimeModule::new)
                .addModule(() -> new CoreDependenciesModule(settingsModule.getAuthDataStoreRootPath()))
                .addModule(() -> GoogleServicesModule.builder()
                        .setAuthDataStoreRootDir(settingsModule.getAuthDataStoreRootPath())
                        .build())
                .addModule(UploadPhotosModule::new)
                .addModule(ResourceBundleModule::new)
                .addModule(() -> new CliModule(commandLine))
                .build()
                .run();
    }

    private static void printHelp() {
        var helpFormatter = new HelpFormatter();
        helpFormatter.setWidth(100);
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        helpFormatter.printHelp(pw, helpFormatter.getWidth(),
                "Jiotty Photos Uploader",
                null,
                CliOptions.OPTIONS,
                helpFormatter.getLeftPadding(),
                helpFormatter.getDescPadding(),
                null,
                false);
        pw.flush();
        var help = sw.toString();
        logger.info(help);
    }
}
