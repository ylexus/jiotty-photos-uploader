package net.yudichev.googlephotosupload.cli;

import net.yudichev.googlephotosupload.core.DependenciesModule;
import net.yudichev.googlephotosupload.core.UploadPhotosModule;
import net.yudichev.jiotty.common.app.Application;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class CliMain {
    private static final Options OPTIONS = new Options()
            .addOption(Option.builder("r")
                    .longOpt("root-dir")
                    .hasArg()
                    .argName("PATH")
                    .desc("Path to root directory to scan")
                    .required()
                    .build());

    public static void main(String[] args) {
        // TODO https://bugs.openjdk.java.net/browse/JDK-8221253, remove when moved to JDK13
        System.setProperty("jdk.tls.client.protocols", "TLSv1,TLSv1.1,TLSv1.2");
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine commandLine = parser.parse(OPTIONS, args);
            Application.builder()
                    .addModule(DependenciesModule::new)
                    .addModule(() -> new CliStarterModule(commandLine))
                    .addModule(() -> new UploadPhotosModule(1000))
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
