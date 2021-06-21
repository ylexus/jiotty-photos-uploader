package net.yudichev.googlephotosupload.ui;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.MenuBar;

interface PlatformSpecificMenu {
    void initialize(MenuBar menuBar);

    void setOnExitAction(EventHandler<ActionEvent> onExitEventHandler);

    void setOnPreferencesAction(EventHandler<ActionEvent> onPreferencesEventHandler);

    void setOnAboutAction(EventHandler<ActionEvent> onAboutAction);
}
