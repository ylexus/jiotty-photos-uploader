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

public final class CliMain {
    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine commandLine = parser.parse(CliOptions.OPTIONS, args);
            Application.builder()
                    .addModule(DependenciesModule::new)
                    .addModule(() -> new UploadPhotosModule(1000))
                    .addModule(ResourceBundleModule::new)
                    .addModule(() -> new CliModule(commandLine))
                    .build()
                    .run();
        } catch (ParseException e) {
            Logger logger = LoggerFactory.getLogger(CliMain.class);
            logger.error(e.getMessage());
            printHelp(logger);
        }
        LogManager.shutdown();
    }

    private static void printHelp(Logger logger) {
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.setWidth(100);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        helpFormatter.printHelp(pw, helpFormatter.getWidth(),
                "Jiotty Photos Uploader",
                null,
                CliOptions.OPTIONS,
                helpFormatter.getLeftPadding(),
                helpFormatter.getDescPadding(),
                null,
                false);
        pw.flush();
        String help = sw.toString();
        logger.info(help);
    }
}
