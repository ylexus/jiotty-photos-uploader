package net.yudichev.googlephotosupload.core;

import com.google.common.io.MoreFiles;
import jakarta.inject.Inject;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static net.yudichev.googlephotosupload.core.Bindings.GoogleAuthRootDir;

final class LegacyAuthCleaner extends BaseLifecycleComponent {
    private static final Logger logger = LogManager.getLogger(LegacyAuthCleaner.class);
    private final Path googleAuthRootDir;

    @Inject
    LegacyAuthCleaner(@GoogleAuthRootDir Path googleAuthRootDir) {
        this.googleAuthRootDir = checkNotNull(googleAuthRootDir);
    }

    @Override
    protected void doStart() {
        var legacyAuthDir = googleAuthRootDir.resolve("photos");
        if (Files.exists(legacyAuthDir)) {
            try {
                MoreFiles.deleteRecursively(legacyAuthDir, ALLOW_INSECURE);
            } catch (IOException e) {
                logger.warn("Unable to remove legacy auth dir {}", legacyAuthDir, e);
            }
        }
    }
}
