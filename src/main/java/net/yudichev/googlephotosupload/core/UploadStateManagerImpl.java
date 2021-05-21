package net.yudichev.googlephotosupload.core;

import com.google.common.collect.ImmutableMap;
import com.google.inject.BindingAnnotation;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.common.varstore.VarStore;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static net.yudichev.jiotty.common.lang.Closeable.closeIfNotNull;
import static net.yudichev.jiotty.common.lang.Locks.inLock;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

final class UploadStateManagerImpl extends BaseLifecycleComponent implements UploadStateManager {
    private static final Logger logger = LoggerFactory.getLogger(UploadStateManagerImpl.class);

    private static final String VAR_STORE_KEY = "photosUploader";
    private final VarStore varStore;
    private final Path h2DbPath;
    private final Lock lock = new ReentrantLock();

    private Connection connection;
    private PreparedStatement queryAllStmt;
    private PreparedStatement removeAllStmt;
    private PreparedStatement updateOneStateStmt;
    private PreparedStatement queryCountStmt;

    @Inject
    UploadStateManagerImpl(VarStore varStore,
                           @H2DbPath Path h2DbPath) {
        this.varStore = checkNotNull(varStore);
        this.h2DbPath = checkNotNull(h2DbPath);
    }

    @Override
    protected void doStart() {
        connection = getAsUnchecked(() -> {
            @SuppressWarnings("CallToDriverManagerGetConnection") // no need
            var conn = DriverManager.getConnection("jdbc:h2:" + h2DbPath.toAbsolutePath(), "sa", "");
            conn.setAutoCommit(false);
            return conn;
        });
        inLock(lock, () -> asUnchecked(() -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS MEDIA_ITEMS(" +
                        "PATH VARCHAR(4096) PRIMARY KEY, " +
                        "TOKEN VARCHAR(1024), " +
                        "UPL_TIMESTAMP TIMESTAMP WITH TIME ZONE, " +
                        "MEDIA_ID VARCHAR(256)" +
                        ");");
            }
            migrateFromOldStorage();
        }));
        queryAllStmt = getAsUnchecked(() -> connection.prepareStatement("SELECT PATH, TOKEN, UPL_TIMESTAMP, MEDIA_ID FROM MEDIA_ITEMS"));
        queryCountStmt = getAsUnchecked(() -> connection.prepareStatement("SELECT COUNT(*) FROM MEDIA_ITEMS"));
        removeAllStmt = getAsUnchecked(() -> connection.prepareStatement("TRUNCATE TABLE MEDIA_ITEMS"));
        updateOneStateStmt = getAsUnchecked(() -> connection.prepareStatement(
                "MERGE INTO MEDIA_ITEMS (PATH, TOKEN, UPL_TIMESTAMP, MEDIA_ID) VALUES (?,?,?,?)"));
    }

    private void migrateFromOldStorage() throws SQLException {
        var uploadState = varStore.readValue(UploadState.class, VAR_STORE_KEY).orElseGet(() -> UploadState.builder().build());
        if (!uploadState.uploadedMediaItemIdByAbsolutePath().isEmpty()) {
            logger.info("Migrating {} items to new state storage...", uploadState.uploadedMediaItemIdByAbsolutePath().size());
            try (var statement = connection
                    .prepareStatement("INSERT INTO MEDIA_ITEMS (PATH, TOKEN, UPL_TIMESTAMP, MEDIA_ID) VALUES (?,?,?,?)")) {
                for (var entry : uploadState.uploadedMediaItemIdByAbsolutePath().entrySet()) {
                    addRowUpdateBatch(statement, entry.getKey(), entry.getValue());
                }
                statement.executeBatch();
            }
            connection.commit();
            varStore.saveValue(VAR_STORE_KEY, uploadState);
            logger.info("Migrated successfully");
        }
    }

    @Override
    protected void doStop() {
        closeIfNotNull(connection);
    }

    @Override
    public Map<String, ItemState> loadUploadedMediaItemIdByAbsolutePath() {
        return inLock(lock, () -> getAsUnchecked(() -> {
            try (var resultSet = queryAllStmt.executeQuery()) {
                var resultBuilder = ImmutableMap.<String, ItemState>builder();
                while (resultSet.next()) {
                    var path = resultSet.getString(1);
                    var itemStateBuilder = ItemState.builder();
                    var token = resultSet.getString(2);
                    if (token != null) {
                        itemStateBuilder
                                .setUploadState(UploadMediaItemState.of(token, resultSet.getObject(3, Instant.class)));
                    }
                    var mediaId = resultSet.getString(4);
                    if (mediaId != null) {
                        itemStateBuilder.setMediaId(mediaId);
                    }
                    resultBuilder.put(path, itemStateBuilder.build());
                }
                return resultBuilder.build();
            }
        }));
    }

    @Override
    public void forgetState() {
        inLock(lock, () -> asUnchecked(() -> removeAllStmt.execute()));
    }

    @Override
    public void saveItemState(Path path, ItemState itemState) {
        inLock(lock, () -> asUnchecked(() -> {
            addRowUpdateBatch(updateOneStateStmt, path.toAbsolutePath().toString(), itemState);
            updateOneStateStmt.execute();
            connection.commit();
        }));
    }

    @Override
    public int itemCount() {
        return inLock(lock, () -> getAsUnchecked(() -> {
            try (var resultSet = queryCountStmt.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }));
    }

    @Override
    public void startWebServer() {
        logger.info("Starting DB Console");
        asUnchecked(() -> Server.startWebServer(connection));
        logger.info("DB Console Disconnected");
    }

    private static void addRowUpdateBatch(PreparedStatement statement, String absolutePath, ItemState itemState) throws SQLException {
        statement.clearParameters();
        statement.setString(1, absolutePath);
        if (itemState.uploadState().isPresent()) {
            var uploadMediaItemState = itemState.uploadState().get();
            statement.setString(2, uploadMediaItemState.token());
            statement.setObject(3, uploadMediaItemState.uploadInstant());
        } else {
            statement.setNull(2, Types.NULL);
            statement.setNull(3, Types.NULL);
        }
        if (itemState.mediaId().isPresent()) {
            statement.setString(4, itemState.mediaId().get());
        } else {
            statement.setNull(4, Types.NULL);
        }
        statement.addBatch();
    }

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface H2DbPath {
    }
}
