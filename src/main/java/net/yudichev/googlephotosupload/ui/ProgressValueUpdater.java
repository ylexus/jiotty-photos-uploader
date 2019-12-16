package net.yudichev.googlephotosupload.ui;

import net.yudichev.jiotty.common.lang.Closeable;

interface ProgressValueUpdater extends Closeable {
    void update(int newValue);
}
