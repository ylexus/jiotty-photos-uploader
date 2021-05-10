package net.yudichev.googlephotosupload.core;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppGlobals {
    public static final String APP_TITLE = "Jiotty Photos Uploader";
    public static final String APP_SETTINGS_DIR_NAME = "jiottyphotosuploader";
    public static final Path APP_SETTINGS_DIR = Paths.get(System.getProperty("user.home"), "." + APP_SETTINGS_DIR_NAME);
    static final Path APP_SETTINGS_AUTH_DIR = APP_SETTINGS_DIR.resolve("auth");

    private AppGlobals() {
    }
}
