package net.yudichev.googlephotosupload.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import static java.awt.Image.SCALE_SMOOTH;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;
import static net.yudichev.googlephotosupload.core.AppGlobals.APP_SETTINGS_DIR;
import static net.yudichev.googlephotosupload.core.AppGlobals.APP_TITLE;
import static net.yudichev.googlephotosupload.core.ResourceBundleModule.RESOURCE_BUNDLE;

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
            logger.error("Exception trying to lock the instance file", e);
            // should be pretty rare; best we can do is assume the instance is not running
            return false;
        }
        if (LOCK == null) {
            var image = new ImageIcon(SingleInstanceCheck.class.getResource("/Icon1024.png")).getImage();
            var icon = new ImageIcon(image.getScaledInstance(40, 40, SCALE_SMOOTH));
            showMessageDialog(null,
                    RESOURCE_BUNDLE.getString("singleInstanceCheckDialogMessage"),
                    APP_TITLE,
                    ERROR_MESSAGE,
                    icon);
            return true;
        }
        //noinspection StaticVariableUsedBeforeInitialization it's not
        logger.debug("Acquired instance lock {} on {}", LOCK, lockFile);
        return false;
    }
}
