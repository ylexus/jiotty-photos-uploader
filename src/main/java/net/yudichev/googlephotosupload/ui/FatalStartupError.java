package net.yudichev.googlephotosupload.ui;

import javax.swing.*;

import static java.awt.Image.SCALE_SMOOTH;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;
import static javax.swing.UIManager.setLookAndFeel;
import static net.yudichev.googlephotosupload.core.AppGlobals.APP_TITLE;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;

final class FatalStartupError {
    private FatalStartupError() {
    }

    static void showFatalStartupError(String text) {
        var image = new ImageIcon(SingleInstanceCheck.class.getResource("/Icon1024.png")).getImage();
        var icon = new ImageIcon(image.getScaledInstance(40, 40, SCALE_SMOOTH));
        asUnchecked(() -> setLookAndFeel(UIManager.getSystemLookAndFeelClassName()));
        showMessageDialog(null,
                text,
                APP_TITLE,
                ERROR_MESSAGE,
                icon);
    }
}
