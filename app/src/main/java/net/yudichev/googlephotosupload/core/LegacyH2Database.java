package net.yudichev.googlephotosupload.core;

import com.google.common.collect.ImmutableMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.h2.api.ErrorCode;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/// Migrates an upload state database written by H2 1.4.200 (used up to version 2.1.0 of the app), whose file format the current H2 cannot open.
///
/// The old H2 is bundled as a resource (see `legacyH2` in `build.gradle`) and loaded in an isolated class loader, so it never meets the current H2 on the
/// class path. The old database file is renamed rather than deleted, so nothing is lost if the migration fails.
final class LegacyH2Database {
    private static final Logger logger = LogManager.getLogger(LegacyH2Database.class);

    static final String LEGACY_SUFFIX = "-h2-1.4.200";
    private static final String DB_FILE_EXTENSION = ".mv.db";
    private static final String LEGACY_JAR_RESOURCE = "legacy-h2.jar";

    private LegacyH2Database() {
    }

    /// If the database at `dbPath` was written by H2 1.4.200, renames it aside and returns its contents, which the caller should write into a new database
    /// at `dbPath`. Returns empty if there is no database, if it's already in the current format, or if the old database could not be read, in which case
    /// the error is logged and the renamed file is kept for manual recovery.
    static Optional<Map<String, ItemState>> extractIfLegacy(Path dbPath) throws SQLException, IOException {
        var dbFile = dbFile(dbPath);
        if (!Files.exists(dbFile)) {
            return Optional.empty();
        }
        //noinspection CallToDriverManagerGetConnection only probing the format
        try (var ignored = DriverManager.getConnection(jdbcUrl(dbPath), "sa", "")) {
            return Optional.empty();
        } catch (SQLException e) {
            if (e.getErrorCode() != ErrorCode.FILE_VERSION_ERROR_1) {
                throw e;
            }
        }

        var legacyDbPath = legacyDbPath(dbPath);
        var legacyDbFile = dbFile(legacyDbPath);
        logger.info("Upload state database {} was written by an older version of the app, migrating it; the original is kept as {}", dbFile, legacyDbFile);
        Files.move(dbFile, legacyDbFile, REPLACE_EXISTING);
        try {
            var items = withConnection(legacyDbPath, true, LegacyH2Database::readItems);
            logger.info("Read {} item(s) from the old upload state database", items.size());
            return Optional.of(items);
        } catch (Exception e) {
            logger.error("Failed to read the old upload state database {}; starting with an empty upload state", legacyDbFile, e);
            return Optional.empty();
        }
    }

    static Path legacyDbPath(Path dbPath) {
        return dbPath.resolveSibling(dbPath.getFileName() + LEGACY_SUFFIX);
    }

    static Path dbFile(Path dbPath) {
        return dbPath.resolveSibling(dbPath.getFileName() + DB_FILE_EXTENSION);
    }

    /// Runs `action` against a connection to the database at `dbPath` opened by H2 1.4.200; `mustExist` false creates the database if absent.
    static <T> T withConnection(Path dbPath, boolean mustExist, SqlFunction<T> action) throws SQLException, IOException, ReflectiveOperationException {
        var jarFile = Files.createTempFile("jiotty-photos-uploader-legacy-h2", ".jar");
        try {
            try (var jarStream = LegacyH2Database.class.getResourceAsStream(LEGACY_JAR_RESOURCE)) {
                checkState(jarStream != null, "resource %s not found", LEGACY_JAR_RESOURCE);
                Files.copy(jarStream, jarFile, REPLACE_EXISTING);
            }
            try (var classLoader = new URLClassLoader(new URL[]{jarFile.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
                var driverClass = classLoader.loadClass("org.h2.Driver");
                // loading the class registered it with DriverManager; unregister so it cannot be picked for the current H2's URLs
                try {
                    var driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
                    var properties = new Properties();
                    properties.setProperty("user", "sa");
                    properties.setProperty("password", "");
                    try (var connection = driver.connect(mustExist ? jdbcUrl(dbPath) : "jdbc:h2:" + dbPath.toAbsolutePath(), properties)) {
                        return action.apply(connection);
                    }
                } finally {
                    driverClass.getMethod("unload").invoke(null);
                }
            }
        } finally {
            Files.deleteIfExists(jarFile);
        }
    }

    private static Map<String, ItemState> readItems(Connection connection) throws SQLException {
        var resultBuilder = ImmutableMap.<String, ItemState>builder();
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT PATH, TOKEN, UPL_TIMESTAMP, MEDIA_ID FROM MEDIA_ITEMS")) {
            while (resultSet.next()) {
                var path = resultSet.getString(1);
                var itemStateBuilder = ItemState.builder();
                var token = resultSet.getString(2);
                var uploadTimestamp = resultSet.getObject(3, OffsetDateTime.class);
                if (token != null && uploadTimestamp != null) {
                    itemStateBuilder.setUploadState(UploadMediaItemState.of(token, uploadTimestamp.toInstant()));
                }
                var mediaId = resultSet.getString(4);
                if (mediaId != null) {
                    itemStateBuilder.setMediaId(mediaId);
                }
                resultBuilder.put(path, itemStateBuilder.build());
            }
        }
        return resultBuilder.build();
    }

    private static String jdbcUrl(Path dbPath) {
        return "jdbc:h2:" + dbPath.toAbsolutePath() + ";IFEXISTS=TRUE";
    }

    @FunctionalInterface
    interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }
}
