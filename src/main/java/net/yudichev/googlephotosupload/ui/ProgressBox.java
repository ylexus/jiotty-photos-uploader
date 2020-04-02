package net.yudichev.googlephotosupload.ui;

import javafx.scene.Node;
import net.yudichev.jiotty.common.lang.Closeable;

interface ProgressBox extends Closeable {
    Node node();
}
