package net.yudichev.googlephotosupload.core;

import java.util.function.Function;
import java.util.function.Supplier;

public interface PreferencesManager extends Supplier<Preferences> {
    void update(Function<Preferences, Preferences> updater);
}
