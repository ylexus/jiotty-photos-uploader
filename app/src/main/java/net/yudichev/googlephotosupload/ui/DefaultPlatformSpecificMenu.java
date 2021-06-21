package net.yudichev.googlephotosupload.ui;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import javax.inject.Inject;
import java.util.ResourceBundle;

import static com.google.common.base.Preconditions.checkNotNull;

final class DefaultPlatformSpecificMenu implements PlatformSpecificMenu {
    private final ResourceBundle resourceBundle;
    private MenuItem exitMenuItem;
    private MenuItem preferencesMenuItem;
    private MenuItem aboutMenuItem;

    @Inject
    DefaultPlatformSpecificMenu(ResourceBundle resourceBundle) {
        this.resourceBundle = checkNotNull(resourceBundle);
    }

    @Override
    public void initialize(MenuBar menuBar) {
        var fileMenu = new Menu(resourceBundle.getString("menuItemDefaultFile"));
        var fileMenuItems = fileMenu.getItems();

        aboutMenuItem = new MenuItem(resourceBundle.getString("menuItemDefaultAbout"));
        fileMenuItems.add(aboutMenuItem);

        preferencesMenuItem = new MenuItem(resourceBundle.getString("menuItemDefaultSettings"));
        fileMenuItems.add(preferencesMenuItem);

        fileMenuItems.add(new SeparatorMenuItem());
        exitMenuItem = new MenuItem(resourceBundle.getString("menuItemDefaultExit"));

        fileMenuItems.add(exitMenuItem);
        menuBar.getMenus().add(0, fileMenu);
    }

    @Override
    public void setOnExitAction(EventHandler<ActionEvent> onExitEventHandler) {
        exitMenuItem.setOnAction(onExitEventHandler);
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
