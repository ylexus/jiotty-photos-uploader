package net.yudichev.googlephotosupload.ui;

import de.jangassen.MenuToolkit;
import jakarta.inject.Inject;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCodeCombination;

import java.util.ResourceBundle;

import static com.google.common.base.Preconditions.checkNotNull;
import static javafx.scene.input.KeyCode.COMMA;
import static javafx.scene.input.KeyCombination.META_DOWN;
import static net.yudichev.googlephotosupload.core.AppGlobals.APP_TITLE;

final class MacPlatformSpecificMenu implements PlatformSpecificMenu {
    private final ResourceBundle resourceBundle;
    private MenuItem preferencesMenuItem;
    private MenuItem aboutMenuItem;
    private MenuItem quitMenuItem;

    @Inject
    MacPlatformSpecificMenu(ResourceBundle resourceBundle) {
        this.resourceBundle = checkNotNull(resourceBundle);
    }

    @Override
    public void initialize(MenuBar menuBar) {
        aboutMenuItem = new MenuItem(String.format(resourceBundle.getString("menuItemMacAbout"), APP_TITLE));
        preferencesMenuItem = new MenuItem(resourceBundle.getString("menuItemMacPreferences"));
        preferencesMenuItem.setAccelerator(new KeyCodeCombination(COMMA, META_DOWN));

        var tk = MenuToolkit.toolkit(resourceBundle.getLocale());
        // keeps NSMenuFX's localised label and Cmd+Q, but its action (Platform.exit()) would stop JavaFX behind the application's back
        quitMenuItem = tk.createQuitMenuItem(APP_TITLE);
        tk.setApplicationMenu(new Menu("Apple",
                null,
                aboutMenuItem,
                new SeparatorMenuItem(),
                preferencesMenuItem,
                new SeparatorMenuItem(),
                tk.createHideMenuItem(APP_TITLE),
                tk.createHideOthersMenuItem(),
                tk.createUnhideAllMenuItem(),
                new SeparatorMenuItem(),
                quitMenuItem));
    }

    @Override
    public void setOnExitAction(EventHandler<ActionEvent> onExitEventHandler) {
        quitMenuItem.setOnAction(onExitEventHandler);
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
