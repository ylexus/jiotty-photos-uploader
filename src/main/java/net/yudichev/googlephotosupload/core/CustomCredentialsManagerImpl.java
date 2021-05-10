package net.yudichev.googlephotosupload.core;

import com.google.common.hash.Hashing;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.toByteArray;
import static java.nio.file.Files.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static net.yudichev.googlephotosupload.core.AppGlobals.APP_SETTINGS_DIR;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

final class CustomCredentialsManagerImpl implements Provider<URL>, CustomCredentialsManager {
    private static final URL STANDARD_CREDENTIALS_URL = getResource("client_secret.json");
    private static final byte[] STANDARD_CREDENTIALS_URL_HASH = crc32(STANDARD_CREDENTIALS_URL);
    private final PreferencesManager preferencesManager;
    private final Path CUSTOM_CREDENTIALS_PATH = APP_SETTINGS_DIR.resolve("client_secret.json");
    private final AtomicReference<Boolean> usingCustomCredentials = new AtomicReference<>();
    @Nullable
    private byte[] hashOfUsedCredentials;
    @Nullable
    private byte[] hashOfConfiguredCredentials;

    @Inject
    CustomCredentialsManagerImpl(PreferencesManager preferencesManager) {
        this.preferencesManager = checkNotNull(preferencesManager);
    }

    @Override
    public void saveCustomCredentials(Path sourceFile) {
        asUnchecked(() -> copy(sourceFile, CUSTOM_CREDENTIALS_PATH, REPLACE_EXISTING));
        refreshHashOfConfiguredCredentials();
    }

    @Override
    public boolean usingCustomCredentials() {
        return checkNotNull(usingCustomCredentials.get(), "usingCustomCredentials not initialised");
    }

    @Override
    public boolean configuredToUseCustomCredentials() {
        return preferencesManager.get().useCustomCredentials() && isRegularFile(CUSTOM_CREDENTIALS_PATH);
    }

    @Override
    public void deleteCustomCredentials() {
        asUnchecked(() -> deleteIfExists(CUSTOM_CREDENTIALS_PATH));
        refreshHashOfConfiguredCredentials();
    }

    @Override
    public boolean usedCredentialsMatchConfigured() {
        return Arrays.equals(hashOfConfiguredCredentials, hashOfUsedCredentials);
    }

    @Override
    public URL get() {
        var usingCustomCredentials = configuredToUseCustomCredentials();
        this.usingCustomCredentials.set(usingCustomCredentials);
        URL url;
        if (usingCustomCredentials) {
            url = getAsUnchecked(() -> CUSTOM_CREDENTIALS_PATH.toUri().toURL());
            hashOfUsedCredentials = crc32(url);
        } else {
            url = STANDARD_CREDENTIALS_URL;
            hashOfUsedCredentials = STANDARD_CREDENTIALS_URL_HASH;
        }
        refreshHashOfConfiguredCredentials(); // set initial state
        return url;
    }

    private void refreshHashOfConfiguredCredentials() {
        hashOfConfiguredCredentials = configuredToUseCustomCredentials() ?
                crc32(getAsUnchecked(() -> CUSTOM_CREDENTIALS_PATH.toUri().toURL())) :
                STANDARD_CREDENTIALS_URL_HASH;
    }

    private static byte[] crc32(URL url) {
        return Hashing.crc32().hashBytes(getAsUnchecked(() -> toByteArray(url))).asBytes();
    }
}
