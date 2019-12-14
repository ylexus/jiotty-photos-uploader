package net.yudichev.googlephotosupload.ui;

import javafx.scene.Node;
import net.yudichev.googlephotosupload.core.ProgressStatus;

interface ProgressStatusBar extends ProgressStatus {
    Node node();
}
