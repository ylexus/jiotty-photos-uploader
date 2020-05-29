package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import net.yudichev.jiotty.common.inject.ExposedKeyModule;

import java.util.ResourceBundle;

public final class ResourceBundleModule extends BaseLifecycleComponentModule implements ExposedKeyModule<ResourceBundle> {
    public static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle("i18n.Resources");

    @Override
    protected void configure() {
        bind(getExposedKey()).toInstance(RESOURCE_BUNDLE);
        expose(getExposedKey());
    }
}