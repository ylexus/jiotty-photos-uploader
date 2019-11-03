package net.yudichev.googlephotosupload.app;

import net.yudichev.jiotty.common.app.Application;
import net.yudichev.jiotty.common.async.ExecutorModule;
import net.yudichev.jiotty.common.varstore.VarStoreModule;
import net.yudichev.jiotty.connector.google.common.GoogleApiSettings;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosModule;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;

import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

public final class UploadPhotosMain {
    private static final Options OPTIONS = new Options()
            .addOption(Option.builder("r")
                    .longOpt("root-dir")
                    .hasArg()
                    .argName("PATH")
                    .desc("Path to root directory to scan")
                    .required()
                    .build())
            .addOption(Option.builder("c")
                    .longOpt("credentials-file-url")
                    .hasArg()
                    .argName("URL")
                    .desc("URL of the Google API credentials file, get it on https://console.cloud.google.com/apis/credentials")
                    .required()
                    .build());

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine commandLine = parser.parse(OPTIONS, args);
            Application.builder()
                    .addModule(() -> new CommandLineModule(commandLine))
                    .addModule(ExecutorModule::new)
                    .addModule(() -> new VarStoreModule("googlephotosupload"))
                    .addModule(() -> GooglePhotosModule.builder()
                            .setSettings(GoogleApiSettings.of("Google Photos Uploader", getAsUnchecked(() -> new URL(commandLine.getOptionValue('c')))))
                            .build())
                    .addModule(() -> new UploadPhotosModule(1000))
                    .build()
                    .run();
        } catch (ParseException e) {
            Logger logger = LoggerFactory.getLogger(UploadPhotosMain.class);
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
                "Google Photos Uploader",
                null,
                OPTIONS,
                helpFormatter.getLeftPadding(),
                helpFormatter.getDescPadding(),
                null,
                false);
        pw.flush();
        String help = sw.toString();
        logger.info(help);
    }
}
