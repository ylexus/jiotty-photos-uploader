package net.yudichev.googlephotosupload.ui;

import de.codecentric.centerdevice.MenuToolkit;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCodeCombination;

import javax.inject.Inject;
import java.util.ResourceBundle;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.scene.input.KeyCode.COMMA;
import static javafx.scene.input.KeyCombination.META_DOWN;
import static net.yudichev.googlephotosupload.core.AppName.APP_TITLE;

final class MacPlatformSpecificMenu implements PlatformSpecificMenu {
    private final ResourceBundle resourceBundle;
    private MenuItem preferencesMenuItem;
    private MenuItem aboutMenuItem;

    @Inject
    MacPlatformSpecificMenu(ResourceBundle resourceBundle) {
        this.resourceBundle = checkNotNull(resourceBundle);
    }

    @Override
    public void initialize(MenuBar menuBar) {
        MenuToolkit tk = MenuToolkit.toolkit();
        Menu defaultApplicationMenu = tk.createDefaultApplicationMenu(APP_TITLE);

        aboutMenuItem = tk.createAboutMenuItem(APP_TITLE);
        defaultApplicationMenu.getItems().set(0, aboutMenuItem);

        preferencesMenuItem = new MenuItem(resourceBundle.getString("menuItemMacPreferences"));
        preferencesMenuItem.setAccelerator(new KeyCodeCombination(COMMA, META_DOWN));
        defaultApplicationMenu.getItems().add(2, new SeparatorMenuItem());
        defaultApplicationMenu.getItems().add(2, preferencesMenuItem);

        defaultApplicationMenu.getItems().add(4, tk.createCloseWindowMenuItem());

        tk.setApplicationMenu(defaultApplicationMenu);
    }

    @Override
    public void setOnExitAction(EventHandler<ActionEvent> onExitEventHandler) {
        // no dedicated exit menu items on Mac
    }

    @Override
    public void setOnPreferencesAction(EventHandler<ActionEvent> onPreferencesEventHandler) {
        preferencesMenuItem.setOnAction(onPreferencesEventHandler);
    }

    @Override
    public void setOnAboutAction(EventHandler<ActionEvent> onAboutAction) {
        aboutMenuItem.setOnAction(onAboutAction);
    }
}
