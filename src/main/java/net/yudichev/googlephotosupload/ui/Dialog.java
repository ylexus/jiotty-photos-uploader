package net.yudichev.googlephotosupload.ui;

import net.yudichev.jiotty.common.lang.Closeable;

interface Dialog extends Closeable {
    void show();

    <T> T controller();

    void sizeToScene();
}
