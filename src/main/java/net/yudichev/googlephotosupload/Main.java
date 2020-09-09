package net.yudichev.googlephotosupload;

import net.yudichev.googlephotosupload.cli.CliMain;
import net.yudichev.googlephotosupload.ui.UiMain;

import static java.util.Locale.ENGLISH;
import static java.util.Locale.setDefault;

public final class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            setLoggingConfiguration("log4j2-ui.yaml");
            UiMain.main(args);
        } else {
            setDefault(ENGLISH);
            setLoggingConfiguration("log4j2-cli.yaml");
            CliMain.main(args);
        }
    }

    private static String setLoggingConfiguration(String configPath) {
        return System.setProperty("log4j.configurationFile", configPath);
    }
}
