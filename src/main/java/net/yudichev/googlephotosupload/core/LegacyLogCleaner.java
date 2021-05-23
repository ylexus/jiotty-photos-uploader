package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.core.Bindings.SettingsRoot;

final class LegacyLogCleaner extends BaseLifecycleComponent {
    private static final Logger logger = LoggerFactory.getLogger(LegacyLogCleaner.class);
    private final Path settingsRoot;

    @Inject
    LegacyLogCleaner(@SettingsRoot Path settingsRoot) {
        this.settingsRoot = checkNotNull(settingsRoot);
    }

    @Override
    protected void doStart() {
        var legacyLogFileNamePattern = Pattern.compile("main.log.\\d{4}(-\\d{2}){4}");
        var logDir = settingsRoot.resolve("log");
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
