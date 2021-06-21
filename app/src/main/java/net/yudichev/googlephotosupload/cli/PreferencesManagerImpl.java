package net.yudichev.googlephotosupload.cli;

import net.yudichev.googlephotosupload.core.Preferences;
import net.yudichev.googlephotosupload.core.PreferencesManager;
import net.yudichev.jiotty.common.varstore.VarStore;

import javax.inject.Inject;
import java.util.function.Function;

final class PreferencesManagerImpl implements PreferencesManager {
    private static final String VAR_STORE_KEY = "preferences";
    private final Preferences preferences;

    @Inject
    PreferencesManagerImpl(VarStore varStore) {
        preferences = varStore.readValue(Preferences.class, VAR_STORE_KEY).orElseGet(() -> Preferences.builder().build());
    }

    @Override
    public void update(Function<Preferences, Preferences> updater) {
        throw new UnsupportedOperationException("updating preferences not supported in CLI");
    }

    @Override
    public Preferences get() {
        return preferences;
    }
}
