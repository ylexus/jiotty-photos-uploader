package net.yudichev.googlephotosupload.ui;

import com.google.inject.Key;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import net.yudichev.jiotty.common.inject.ExposedKeyModule;
import net.yudichev.jiotty.connector.google.common.AuthorizationBrowser;

final class UiAuthorizationBrowserModule extends BaseLifecycleComponentModule implements ExposedKeyModule<AuthorizationBrowser> {

    private final Key<AuthorizationBrowser> exposedKey;

    UiAuthorizationBrowserModule() {
        exposedKey = Key.get(AuthorizationBrowser.class, Bindings.AuthBrowser.class);
    }

    @Override
    public Key<AuthorizationBrowser> getExposedKey() {
        return exposedKey;
    }

    @Override
    protected void configure() {
        bind(exposedKey).to(registerLifecycleComponent(UiAuthorizationBrowser.class));
        expose(exposedKey);
    }
}
