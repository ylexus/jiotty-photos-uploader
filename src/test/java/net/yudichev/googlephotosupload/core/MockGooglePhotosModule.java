package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import net.yudichev.jiotty.common.inject.ExposedKeyModule;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosClient;

import static com.google.common.base.Preconditions.checkNotNull;

final class MockGooglePhotosModule extends BaseLifecycleComponentModule implements ExposedKeyModule<GooglePhotosClient> {
    private final GooglePhotosClient googlePhotosClient;

    MockGooglePhotosModule(GooglePhotosClient googlePhotosClient) {
        this.googlePhotosClient = checkNotNull(googlePhotosClient);
    }

    @Override
    protected void configure() {
        bind(getExposedKey()).toInstance(googlePhotosClient);
        expose(getExposedKey());
    }
}
