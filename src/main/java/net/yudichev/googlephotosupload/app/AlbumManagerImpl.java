package net.yudichev.googlephotosupload.app;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import net.jiotty.common.inject.BaseLifecycleComponent;
import net.jiotty.common.lang.PackagePrivateImmutablesStyle;
import net.jiotty.common.varstore.VarStore;
import net.jiotty.connector.google.photos.GooglePhotosAlbum;
import net.jiotty.connector.google.photos.GooglePhotosClient;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toConcurrentMap;
import static net.yudichev.googlephotosupload.app.Bindings.Backpressured;
import static net.yudichev.googlephotosupload.app.Bindings.RootDir;

final class AlbumManagerImpl extends BaseLifecycleComponent implements AlbumManager {
    private static final Logger logger = LoggerFactory.getLogger(AlbumManagerImpl.class);
    private static final String VAR_STORE_KEY = "albumManager";

    private final VarStore varStore;
    private final GooglePhotosClient googlePhotosClient;
    private final int rootNameCount;
    private final ExecutorService executorService;
    private final Map<Path, Optional<CompletableFuture<GooglePhotosAlbum>>> albumByPath = new ConcurrentHashMap<>();
    private final StateSaver stateSaver;
    private Map<String, CompletableFuture<GooglePhotosAlbum>> createdAlbumsByAlbumName;
    private AlbumState albumState;

    @Inject
    AlbumManagerImpl(VarStore varStore,
                     GooglePhotosClient googlePhotosClient,
                     @RootDir Path rootDir,
                     @Backpressured ExecutorService executorService,
                     StateSaverFactory stateSaverFactory) {
        this.varStore = checkNotNull(varStore);
        this.googlePhotosClient = checkNotNull(googlePhotosClient);
        rootNameCount = rootDir.getNameCount();
        this.executorService = checkNotNull(executorService);
        stateSaver = stateSaverFactory.create("created-albums", this::saveState);
    }

    @Override
    public Optional<CompletableFuture<GooglePhotosAlbum>> albumForDir(Path dir) {
        return albumByPath.compute(dir, (theDir, currentAlbumByPathFuture) -> {
            int nameCount = dir.getNameCount();
            if (nameCount > rootNameCount) {
                //noinspection OptionalAssignedToNull,OptionalGetWithoutIsPresent as designed
                if (currentAlbumByPathFuture == null || currentAlbumByPathFuture.get().isCompletedExceptionally()) {
                    Path albumNamePath = dir.subpath(rootNameCount, nameCount);
                    String albumName = String.join(": ", Streams.stream(albumNamePath.iterator())
                            .map(Path::toString)
                            .collect(toImmutableList()));
                    CompletableFuture<GooglePhotosAlbum> album = createdAlbumsByAlbumName.compute(albumName,
                            (theName, currentFuture) -> {
                                if (currentFuture == null || currentFuture.isCompletedExceptionally()) {
                                    currentFuture = googlePhotosClient.createAlbum(theName, executorService)
                                            .thenApply(googlePhotosAlbum -> {
                                                logger.info("Created album '{}', id {}", theName, googlePhotosAlbum.getId());
                                                return googlePhotosAlbum;
                                            });
                                }
                                return currentFuture;
                            })
                            .thenApply(theAlbum -> {
                                stateSaver.save();
                                return theAlbum;
                            });
                    logger.debug("Directory {}: album future {}", dir, album);
                    return Optional.of(album);
                } else {
                    return currentAlbumByPathFuture;
                }
            } else {
                logger.debug("Directory {}: no album", dir);
                return Optional.empty();
            }
        });
    }

    @Override
    protected void doStart() {
        albumState = varStore.readValue(AlbumState.class, VAR_STORE_KEY).orElseGet(() -> AlbumState.builder().build());
        createdAlbumsByAlbumName = albumState.uploadedAlbumIdByTitle().entrySet().stream()
                .collect(toConcurrentMap(
                        Map.Entry::getKey,
                        entry -> googlePhotosClient.getAlbum(entry.getValue(), executorService)));
    }

    @Override
    protected void doStop() {
        stateSaver.close();
    }

    private void saveState() {
        AlbumState newAlbumState = AlbumState.of(
                createdAlbumsByAlbumName.entrySet().stream()
                        .filter(entry -> entry.getValue().isDone() && !entry.getValue().isCompletedExceptionally())
                        .collect(ImmutableMap.toImmutableMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().getNow(null).getId())));
        if (!newAlbumState.equals(albumState)) {
            albumState = newAlbumState;
            varStore.saveValue(VAR_STORE_KEY, newAlbumState);
        }
    }

    @Value.Immutable
    @PackagePrivateImmutablesStyle
    @JsonSerialize
    @JsonDeserialize
    interface BaseAlbumState {
        @Value.Parameter
        Map<String, String> uploadedAlbumIdByTitle();
    }
}
