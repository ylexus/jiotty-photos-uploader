package net.yudichev.googlephotosupload.ui;

import javafx.scene.Parent;

interface FxmlContainerFactory {
    Parent create(String fxmlResourcePath);
}
