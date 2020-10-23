package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;

import static net.yudichev.googlephotosupload.core.AppGlobals.APP_SETTINGS_DIR;

final class LegacyLogCleaner extends BaseLifecycleComponent {
    private static final Logger logger = LoggerFactory.getLogger(LegacyLogCleaner.class);

    @Override
    protected void doStart() {
        var legacyLogFileNamePattern = Pattern.compile("main.log.\\d{4}(-\\d{2}){4}");
        var logDir = APP_SETTINGS_DIR.resolve("log");
        try (var list = Files.list(logDir)) {
            list
                    .filter(path -> legacyLogFileNamePattern.matcher(path.getFileName().toString()).matches())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            logger.info("Deleted legacy log file {}", path);
                        } catch (IOException e) {
                            logger.warn("Unable to delete {}", path, e);
                        }
                    });
        } catch (IOException e) {
            logger.warn("Unable to list files in {}", logDir, e);
        }
    }
}
