package net.yudichev.googlephotosupload.app;

import net.yudichev.jiotty.common.lang.Closeable;

interface StateSaver extends Closeable {
    void save();
}
