package net.yudichev.googlephotosupload.app;

import net.jiotty.common.inject.BaseLifecycleComponentModule;
import net.jiotty.common.inject.ExposedKeyModule;
import net.jiotty.connector.google.photos.GooglePhotosClient;

import static com.google.common.base.Preconditions.checkNotNull;

final class MockGooglePhotosModule extends BaseLifecycleComponentModule implements ExposedKeyModule<GooglePhotosClient> {
    private GooglePhotosClient googlePhotosClient;

    MockGooglePhotosModule(GooglePhotosClient googlePhotosClient) {
        this.googlePhotosClient = checkNotNull(googlePhotosClient);
    }

    @Override
    protected void configure() {
        bind(getExposedKey()).toInstance(googlePhotosClient);
        expose(getExposedKey());
    }
}
