package net.yudichev.googlephotosupload.app;

import net.jiotty.common.lang.Closeable;

interface StateSaver extends Closeable {
    void save();
}
