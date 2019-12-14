package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.lang.Closeable;

interface StateSaver extends Closeable {
    void save();
}
