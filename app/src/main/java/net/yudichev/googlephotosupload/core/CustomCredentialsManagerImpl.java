package net.yudichev.googlephotosupload.core;

import com.google.common.hash.Hashing;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.common.varstore.VarStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.toByteArray;
import static java.nio.file.Files.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static net.yudichev.googlephotosupload.core.Bindings.GoogleAuthRootDir;
import static net.yudichev.googlephotosupload.core.Bindings.SettingsRoot;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

final class CustomCredentialsManagerImpl extends BaseLifecycleComponent implements Provider<URL>, CustomCredentialsManager {
    private static final Logger logger = LoggerFactory.getLogger(CustomCredentialsManagerImpl.class);

    private static final String VAR_STORE_KEY = "customCredentialsManager.hashOfLoggedInCredentials";
    private static final URL STANDARD_CREDENTIALS_URL = getResource("client_secret.json");
    private static final byte[] STANDARD_CREDENTIALS_URL_HASH = crc32(STANDARD_CREDENTIALS_URL);
    private final Path googleAuthRootDir;
    private final PreferencesManager preferencesManager;
    private final Path customCredentialsPath;
    private final VarStore varStore;
    private final AtomicReference<Boolean> usingCustomCredentials = new AtomicReference<>();
    @Nullable
    private byte[] hashOfUsedCredentials;
    @Nullable
    private byte[] hashOfConfiguredCredentials;
    private volatile URL url;

    @Inject
    CustomCredentialsManagerImpl(@SettingsRoot Path settingsRoot,
                                 @GoogleAuthRootDir Path googleAuthRootDir,
                                 PreferencesManager preferencesManager,
                                 VarStore varStore) {
        this.googleAuthRootDir = checkNotNull(googleAuthRootDir);
        this.preferencesManager = checkNotNull(preferencesManager);
        customCredentialsPath = settingsRoot.resolve("client_secret.json");
        this.varStore = checkNotNull(varStore);
    }

    @Override
    public void saveCustomCredentials(Path sourceFile) {
        asUnchecked(() -> copy(sourceFile, customCredentialsPath, REPLACE_EXISTING));
        refreshHashOfConfiguredCredentials();
    }

    @Override
    public boolean usingCustomCredentials() {
        return checkNotNull(usingCustomCredentials.get(), "usingCustomCredentials not initialised");
    }

    @Override
    public boolean configuredToUseCustomCredentials() {
        return preferencesManager.get().useCustomCredentials() && isRegularFile(customCredentialsPath);
    }

    @Override
    public void deleteCustomCredentials() {
        logger.debug("Deleting custom credentials file {}", customCredentialsPath);
        asUnchecked(() -> deleteIfExists(customCredentialsPath));
        refreshHashOfConfiguredCredentials();
    }

    @Override
    public boolean usedCredentialsMatchConfigured() {
        return Arrays.equals(hashOfConfiguredCredentials, hashOfUsedCredentials);
    }

    @Override
    protected void doStart() {
        var usingCustomCredentials = configuredToUseCustomCredentials();
        this.usingCustomCredentials.set(usingCustomCredentials);
        if (usingCustomCredentials) {
            url = getAsUnchecked(() -> customCredentialsPath.toUri().toURL());
            hashOfUsedCredentials = crc32(url);
        } else {
            url = STANDARD_CREDENTIALS_URL;
            hashOfUsedCredentials = STANDARD_CREDENTIALS_URL_HASH;
        }
        refreshHashOfConfiguredCredentials(); // set initial state
        logger.debug("Using credentials URL {}, hash {}", url, hashOfUsedCredentials);
        varStore.readValue(byte[].class, VAR_STORE_KEY).ifPresent(hashOfLoggedInCredentials -> {
            if (!Arrays.equals(hashOfLoggedInCredentials, hashOfUsedCredentials)) {
                if (exists(googleAuthRootDir)) {
                    logger.info("Forcing logout as last successfully used credentials {} does not match configured ones {}",
                            hashOfLoggedInCredentials, hashOfUsedCredentials);
                    asUnchecked(() -> deleteRecursively(googleAuthRootDir, ALLOW_INSECURE));
                }
            }
        });
    }

    @Override
    public URL get() {
        return url;
    }

    @Override
    public void onSuccessfulLogin() {
        varStore.saveValue(VAR_STORE_KEY, hashOfUsedCredentials);
    }

    private void refreshHashOfConfiguredCredentials() {
        hashOfConfiguredCredentials = configuredToUseCustomCredentials() ?
                crc32(getAsUnchecked(() -> customCredentialsPath.toUri().toURL())) :
                STANDARD_CREDENTIALS_URL_HASH;
    }

    private static byte[] crc32(URL url) {
        return Hashing.crc32().hashBytes(getAsUnchecked(() -> toByteArray(url))).asBytes();
    }
}
