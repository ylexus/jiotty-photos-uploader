package net.yudichev.googlephotosupload.cli;

import net.yudichev.googlephotosupload.core.Preferences;
import net.yudichev.googlephotosupload.core.PreferencesManager;

import java.util.function.Function;

final class PreferencesManagerImpl implements PreferencesManager {
    @Override
    public void update(Function<Preferences, Preferences> updater) {
    }

    // TODO add taking preferences via command line
    @Override
    public Preferences get() {
        return Preferences.builder().build();
    }
}
