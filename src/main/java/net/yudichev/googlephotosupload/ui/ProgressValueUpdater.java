package net.yudichev.googlephotosupload.ui;

import net.yudichev.jiotty.common.lang.Closeable;

interface ProgressValueUpdater extends Closeable {
    void updateSuccess(int newValue);

    void updateFailure(int newValue);
}
