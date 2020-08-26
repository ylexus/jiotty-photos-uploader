package net.yudichev.googlephotosupload.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static net.yudichev.googlephotosupload.core.AppGlobals.APP_SETTINGS_DIR;
import static net.yudichev.googlephotosupload.core.ResourceBundleModule.RESOURCE_BUNDLE;
import static net.yudichev.googlephotosupload.ui.FatalStartupError.showFatalStartupError;

final class SingleInstanceCheck {
    private static final Logger logger = LoggerFactory.getLogger(SingleInstanceCheck.class);
    // keeps the reference to the lock so that it's not garbage collected
    @SuppressWarnings({"FieldCanBeLocal", "StaticVariableMayNotBeInitialized"})
    private static FileLock LOCK;

    public static boolean otherInstanceRunning() {
        var lockFile = APP_SETTINGS_DIR.resolve("instance.lock");
        try {
            LOCK = FileChannel.open(lockFile, CREATE, WRITE).tryLock();
        } catch (IOException | RuntimeException e) {
            logger.error("Exception trying to lock the instance file {}", lockFile, e);
            // should be pretty rare; best we can do is assume the instance is not running
            return false;
        }
        if (LOCK == null) {
            logger.error("Lock could not be acquired on {}, another instance must be running", lockFile);
            showFatalStartupError(RESOURCE_BUNDLE.getString("singleInstanceCheckDialogMessage"));
            return true;
        }
        //noinspection StaticVariableUsedBeforeInitialization it's not
        logger.debug("Acquired instance lock {} on {}", LOCK, lockFile);
        return false;
    }
}
