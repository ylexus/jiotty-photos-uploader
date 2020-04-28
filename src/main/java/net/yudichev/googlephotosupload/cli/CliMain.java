package net.yudichev.googlephotosupload.cli;

import net.yudichev.googlephotosupload.core.DependenciesModule;
import net.yudichev.googlephotosupload.core.ResourceBundleModule;
import net.yudichev.googlephotosupload.core.UploadPhotosModule;
import net.yudichev.jiotty.common.app.Application;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class CliMain {
    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        try {
            var commandLine = parser.parse(CliOptions.OPTIONS, args);
            Application.builder()
                    .addModule(() -> DependenciesModule.builder().build())
                    .addModule(() -> new UploadPhotosModule(1000))
                    .addModule(ResourceBundleModule::new)
                    .addModule(() -> new CliModule(commandLine))
                    .build()
                    .run();
        } catch (ParseException e) {
            var logger = LoggerFactory.getLogger(CliMain.class);
            logger.error(e.getMessage());
            printHelp(logger);
        }
        LogManager.shutdown();
    }

    private static void printHelp(Logger logger) {
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
