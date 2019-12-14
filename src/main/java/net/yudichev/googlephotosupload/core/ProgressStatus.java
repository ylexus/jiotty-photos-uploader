package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.lang.Closeable;

public interface ProgressStatus extends Closeable {
    void update(int newValue);

    void incrementBy(int increment);

    default void increment() {
        incrementBy(1);
    }
}
