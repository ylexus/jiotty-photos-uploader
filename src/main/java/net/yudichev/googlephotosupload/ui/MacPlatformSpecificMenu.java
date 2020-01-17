package net.yudichev.googlephotosupload.ui;

import de.codecentric.centerdevice.MenuToolkit;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCodeCombination;

import static javafx.scene.input.KeyCode.COMMA;
import static javafx.scene.input.KeyCombination.META_DOWN;

final class MacPlatformSpecificMenu implements PlatformSpecificMenu {
    private MenuItem preferencesMenuItem;

    @Override
    public void initialize(MenuBar menuBar) {
        MenuToolkit tk = MenuToolkit.toolkit();
        // TODO deduplicate application name - this is now in multiple places
        Menu defaultApplicationMenu = tk.createDefaultApplicationMenu("Jiotty Photos Uploader");
        preferencesMenuItem = new MenuItem("Preferences...");
        preferencesMenuItem.setAccelerator(new KeyCodeCombination(COMMA, META_DOWN));
        defaultApplicationMenu.getItems().add(2, new SeparatorMenuItem());
        defaultApplicationMenu.getItems().add(2, preferencesMenuItem);
        tk.setApplicationMenu(defaultApplicationMenu);
    }

    @Override
    public void onExitAction(EventHandler<ActionEvent> onExitEventHandler) {
        // no dedicated exit menu items on Mac
    }

    @Override
    public void onPreferencesAction(EventHandler<ActionEvent> onPreferencesEventHandler) {
        preferencesMenuItem.setOnAction(onPreferencesEventHandler);
    }
}
