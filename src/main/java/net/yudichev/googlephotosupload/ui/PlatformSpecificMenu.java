package net.yudichev.googlephotosupload.ui;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.MenuBar;

interface PlatformSpecificMenu {
    void initialize(MenuBar menuBar);

    void onExitAction(EventHandler<ActionEvent> onExitEventHandler);

    void onPreferencesAction(EventHandler<ActionEvent> onPreferencesEventHandler);
}
