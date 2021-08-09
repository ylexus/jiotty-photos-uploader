package net.yudichev.googlephotosupload.core;

import java.nio.file.Path;

public interface CustomCredentialsManager {
    void saveCustomCredentials(Path sourceFile);

    boolean usingCustomCredentials();

    boolean configuredToUseCustomCredentials();

    void deleteCustomCredentials();

    boolean usedCredentialsMatchConfigured();

    void onSuccessfulLogin();
}
