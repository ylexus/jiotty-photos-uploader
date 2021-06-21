package net.yudichev.googlephotosupload.cli;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public final class CliOptions {
    public static final Options OPTIONS = new Options()
            .addOption(Option.builder("r")
                    .longOpt("root-dir")
                    .hasArg()
                    .argName("PATH")
                    .desc("Path to root directory to scan")
                    .build())
            .addOption(Option.builder("n")
                    .longOpt("no-resume")
                    .desc("Forget previous state and force re-upload of all files")
                    .build())
            .addOption(Option.builder("v")
                    .longOpt("version")
                    .desc("Print app version")
                    .build())
            .addOption(Option.builder("h")
                    .longOpt("help")
                    .desc("Print help and exit")
                    .build());

    private CliOptions() {
    }
}
