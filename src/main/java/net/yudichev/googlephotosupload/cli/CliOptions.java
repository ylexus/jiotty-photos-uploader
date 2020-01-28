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
                    .required()
                    .build())
            .addOption(Option.builder("n")
                    .longOpt("no-resume")
                    .desc("Forget previous state and force re-uploading all files")
                    .build());

    private CliOptions() {
    }
}
