package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;

import javax.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;

final class GoogleLoginListener extends BaseLifecycleComponent {
    private final CustomCredentialsManager customCredentialsManager;

    @Inject
    GoogleLoginListener(CustomCredentialsManager customCredentialsManager) {
        this.customCredentialsManager = checkNotNull(customCredentialsManager);
    }

    @Override
    protected void doStart() {
        customCredentialsManager.onSuccessfulLogin();
    }
}
