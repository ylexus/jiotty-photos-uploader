package net.yudichev.googlephotosupload.core;

import com.google.inject.AbstractModule;
import net.yudichev.jiotty.connector.google.drive.GoogleDriveClient;
import net.yudichev.jiotty.connector.google.drive.InMemoryGoogleDriveClient;

import static com.google.common.base.Preconditions.checkNotNull;

final class MockGoogleDriveModule extends AbstractModule {

    private final InMemoryGoogleDriveClient googleDriveClient;

    MockGoogleDriveModule(InMemoryGoogleDriveClient googleDriveClient) {
        this.googleDriveClient = checkNotNull(googleDriveClient);
    }

    @Override
    protected void configure() {
        bind(GoogleDriveClient.class).toInstance(googleDriveClient);
    }
}
