package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.persistence.varstore.VarStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static java.sql.Types.NULL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@ExtendWith(MockitoExtension.class)
final class UploadStateManagerImplTest {
    private static final Instant UPLOAD_INSTANT = Instant.parse("2021-06-01T10:15:30Z");

    @TempDir
    Path tempDir;
    @Mock
    VarStore varStore;

    private Path dbPath;
    private UploadStateManagerImpl manager;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("data");
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.stop();
        }
    }

    @Test
    void startsWithEmptyStateWhenNoDatabase() {
        start();

        assertThat(manager.loadUploadedMediaItemIdByAbsolutePath(), is(anEmptyMap()));
    }

    @Test
    void migratesDatabaseWrittenByH2Version1() throws Exception {
        LegacyH2Database.withConnection(dbPath, false, connection -> {
            try (var statement = connection.createStatement()) {
                // the schema as created by app versions up to 2.1.0
                statement.execute("CREATE TABLE MEDIA_ITEMS(PATH VARCHAR(4096) PRIMARY KEY, TOKEN VARCHAR(1024), " +
                        "UPL_TIMESTAMP TIMESTAMP WITH TIME ZONE, MEDIA_ID VARCHAR(256))");
            }
            try (var insert = connection.prepareStatement("INSERT INTO MEDIA_ITEMS VALUES (?,?,?,?)")) {
                insertRow(insert, "/photos/uploadedNotCreated.jpg", "token1", OffsetDateTime.ofInstant(UPLOAD_INSTANT, ZoneOffset.ofHours(1)), null);
                insertRow(insert, "/photos/created.jpg", null, null, "mediaId2");
                insertRow(insert, "/photos/both.jpg", "token3", OffsetDateTime.ofInstant(UPLOAD_INSTANT, ZoneOffset.UTC), "mediaId3");
            }
            return null;
        });

        start();

        var expectedItems = Map.of(
                "/photos/uploadedNotCreated.jpg", ItemState.builder().setUploadState(UploadMediaItemState.of("token1", UPLOAD_INSTANT)).build(),
                "/photos/created.jpg", ItemState.builder().setMediaId("mediaId2").build(),
                "/photos/both.jpg", ItemState.builder().setUploadState(UploadMediaItemState.of("token3", UPLOAD_INSTANT)).setMediaId("mediaId3").build());
        assertThat(manager.loadUploadedMediaItemIdByAbsolutePath(), equalTo(expectedItems));
        assertThat("old database kept", Files.exists(LegacyH2Database.dbFile(LegacyH2Database.legacyDbPath(dbPath))), is(true));

        // restart: the migrated database opens directly and keeps the data
        manager.stop();
        start();
        assertThat(manager.loadUploadedMediaItemIdByAbsolutePath(), equalTo(expectedItems));
    }

    @Test
    void startsWithEmptyStateWhenOldDatabaseUnreadable() throws Exception {
        // a valid old-format database without the expected table
        LegacyH2Database.withConnection(dbPath, false, connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE SOMETHING_ELSE(ID INT)");
            }
            return null;
        });

        start();

        assertThat(manager.loadUploadedMediaItemIdByAbsolutePath(), is(anEmptyMap()));
        assertThat("old database kept", Files.exists(LegacyH2Database.dbFile(LegacyH2Database.legacyDbPath(dbPath))), is(true));
    }

    private void start() {
        manager = new UploadStateManagerImpl(varStore, dbPath);
        manager.start();
    }

    private static void insertRow(java.sql.PreparedStatement insert, String path, String token, OffsetDateTime timestamp, String mediaId)
            throws java.sql.SQLException {
        insert.setString(1, path);
        if (token == null) {
            insert.setNull(2, NULL);
        } else {
            insert.setString(2, token);
        }
        if (timestamp == null) {
            insert.setNull(3, NULL);
        } else {
            insert.setObject(3, timestamp);
        }
        if (mediaId == null) {
            insert.setNull(4, NULL);
        } else {
            insert.setString(4, mediaId);
        }
        insert.executeUpdate();
    }
}
