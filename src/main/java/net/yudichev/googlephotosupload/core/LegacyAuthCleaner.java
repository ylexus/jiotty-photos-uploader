package net.yudichev.googlephotosupload.core;

import com.google.common.io.MoreFiles;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static net.yudichev.googlephotosupload.core.Bindings.GoogleAuthRootDir;

final class LegacyAuthCleaner extends BaseLifecycleComponent {
    private static final Logger logger = LoggerFactory.getLogger(LegacyAuthCleaner.class);
    private final Path googleAuthRootDir;

    @Inject
    LegacyAuthCleaner(@GoogleAuthRootDir Path googleAuthRootDir) {
        this.googleAuthRootDir = checkNotNull(googleAuthRootDir);
    }

    @Override
    protected void doStart() {
        var legacyAuthDir = googleAuthRootDir.resolve("photos");
        try {
            MoreFiles.deleteRecursively(legacyAuthDir, ALLOW_INSECURE);
        } catch (IOException e) {
            logger.warn("Unable to remove legacy auth dir {}", legacyAuthDir, e);
        }
    }
}
