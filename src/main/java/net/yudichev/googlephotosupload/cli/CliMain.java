package net.yudichev.googlephotosupload.cli;

import net.yudichev.googlephotosupload.core.DependenciesModule;
import net.yudichev.googlephotosupload.core.ResourceBundleModule;
import net.yudichev.googlephotosupload.core.UploadPhotosModule;
import net.yudichev.jiotty.common.app.Application;
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
        try {
            var commandLine = parser.parse(CliOptions.OPTIONS, args);
            if (commandLine.hasOption('v')) {
                logger.info("Version {}", buildVersion());
            }
            if (commandLine.hasOption('h')) {
                printHelp();
            } else {
                if (commandLine.hasOption('r')) {
                    if (otherInstanceRunning()) {
                        logger.error("Another copy of the app is already running");
                    } else {
                        startApp(commandLine);
                    }
                } else {
                    logger.error("Missing option -r");
                }
            }
        } catch (ParseException e) {
            logger.error(e.getMessage());
            printHelp();
        }
        LogManager.shutdown();
    }

    private static void startApp(CommandLine commandLine) {
        Application.builder()
                .addModule(() -> DependenciesModule.builder().build())
                .addModule(() -> new UploadPhotosModule(1000))
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
