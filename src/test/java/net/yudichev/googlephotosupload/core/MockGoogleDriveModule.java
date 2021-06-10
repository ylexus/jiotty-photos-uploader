package net.yudichev.googlephotosupload.core;

import com.google.inject.AbstractModule;
import net.yudichev.jiotty.connector.google.drive.GoogleDriveClient;
import net.yudichev.jiotty.connector.google.drive.InMemoryGoogleDriveClient;

final class MockGoogleDriveModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(GoogleDriveClient.class).toInstance(new InMemoryGoogleDriveClient());
    }
}
