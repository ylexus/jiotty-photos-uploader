package net.yudichev.googlephotosupload.ui;

import net.yudichev.jiotty.common.lang.Closeable;

interface ModalDialog extends Closeable {
    void show();

    <T> T controller();
}
