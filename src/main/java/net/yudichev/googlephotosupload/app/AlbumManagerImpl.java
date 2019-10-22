package net.yudichev.googlephotosupload.app;

import com.google.common.collect.Streams;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.common.varstore.VarStore;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static net.yudichev.googlephotosupload.app.Bindings.Backpressured;
import static net.yudichev.googlephotosupload.app.Bindings.RootDir;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

final class AlbumManagerImpl extends BaseLifecycleComponent implements AlbumManager {
    private static final Logger logger = LoggerFactory.getLogger(AlbumManagerImpl.class);
    private static final String VAR_STORE_KEY = "albumManager";

    private final VarStore varStore;
    private final GooglePhotosClient googlePhotosClient;
    private final int rootNameCount;
    private final ExecutorService executorService;
    private final Map<Path, Optional<CompletableFuture<GooglePhotosAlbum>>> albumByPath = new ConcurrentHashMap<>();
    private final StateSaver stateSaver;
    private final Map<String, GooglePhotoAlbumsOfTitle> albumsByTitle = new ConcurrentHashMap<>();
    private AlbumsState albumState;

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
                    GooglePhotoAlbumsOfTitle googlePhotoAlbumsOfTitle = albumsByTitle.compute(albumName,
                            (theName, albums) -> {
                                if (albums == null || albums.primaryAlbum().isCompletedExceptionally()) {
                                    GooglePhotoAlbumsOfTitle.Builder builder = GooglePhotoAlbumsOfTitle.builder();
                                    if (albums != null) {
                                        builder.from(albums);
                                    }
                                    albums = builder.setPrimaryAlbum(googlePhotosClient.createAlbum(theName, executorService)
                                            .thenApply(googlePhotosAlbum -> {
                                                saveState();
                                                logger.info("Created album '{}', id {}", theName, googlePhotosAlbum.getId());
                                                return googlePhotosAlbum;
                                            }))
                                            .build();
                                }
                                return albums;
                            });
                    logger.debug("Directory {}: albums of title: {}", dir, googlePhotoAlbumsOfTitle);
                    return Optional.of(googlePhotoAlbumsOfTitle.primaryAlbum()
                            .thenApply(googlePhotosAlbum -> {
                                registerNewlyCreatedAlbum(albumName, googlePhotosAlbum);
                                return googlePhotosAlbum;
                            }));
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
        logger.info("Reconciling album(s) with Google Photos");
        albumState = varStore.readValue(AlbumsState.class, VAR_STORE_KEY).orElseGet(() -> AlbumsState.builder().build());

        Map<String, AlbumState> albumsInVarStore = albumState.uploadedAlbumIdByTitle();
        logger.info("{} known album(s)", albumsInVarStore.size());

        List<GooglePhotosAlbum> albumsInCloud = getAsUnchecked(() -> googlePhotosClient.listAllAlbums().get(1, TimeUnit.MINUTES));
        logger.info("{} album(s) in cloud", albumsInCloud.size());

        Map<String, GooglePhotosAlbum> cloudAlbumsById = albumsInCloud.stream()
                .collect(toImmutableMap(
                        GooglePhotosAlbum::getId,
                        Function.identity()));

        // TODO user option "in case of album name match reuse existing albums, do not create new ones"
        albumsInVarStore.forEach((title, albumState) -> {
            Map<String, GooglePhotosAlbum> cloudAlbumsForThisTitleByAlbumId = albumState.albumIds().stream()
                    .map(cloudAlbumsById::get)
                    .filter(Objects::nonNull)
                    .collect(toImmutableMap(
                            GooglePhotosAlbum::getId,
                            Function.identity()));

            if (!cloudAlbumsForThisTitleByAlbumId.isEmpty()) {
                albumsByTitle.put(title, GooglePhotoAlbumsOfTitle.builder()
                        .addAllAlbums(cloudAlbumsForThisTitleByAlbumId.values())
                        .setPrimaryAlbum(completedFuture(albumState.primaryAlbumId()
                                .map(cloudAlbumsForThisTitleByAlbumId::get)
                                .orElseGet(() -> cloudAlbumsForThisTitleByAlbumId.values().iterator().next())))
                        .build());
            }
        });

        albumsInCloud.forEach(cloudAlbum -> albumsByTitle.compute(cloudAlbum.getTitle(), (title, albums) -> albums == null ?
                GooglePhotoAlbumsOfTitle.builder()
                        .addAlbums(cloudAlbum)
                        .setPrimaryAlbum(completedFuture(cloudAlbum))
                        .build() :
                GooglePhotoAlbumsOfTitle.builder()
                        .from(albums)
                        .addAlbums(cloudAlbum)
                        .build()));
        logger.info("Completed reconciliation; {} unique album title(s)", albumsByTitle.size());
    }

    @Override
    protected void doStop() {
        stateSaver.close();
    }

    private void registerNewlyCreatedAlbum(String albumName, GooglePhotosAlbum googlePhotosAlbum) {
        albumsByTitle.computeIfPresent(albumName, (ignored, albumsByTitle) -> GooglePhotoAlbumsOfTitle.builder()
                .from(albumsByTitle)
                .addAlbums(googlePhotosAlbum)
                .build());
        saveState();
    }

    private void saveState() {
        AlbumsState.Builder albumsStatBuilder = AlbumsState.builder();

        albumsByTitle.forEach((title, googlePhotoAlbumsOfTitle) -> {
            AlbumState.Builder albumStateBuilder = AlbumState.builder();
            googlePhotoAlbumsOfTitle.albums().forEach(googlePhotosAlbum -> albumStateBuilder.addAlbumIds(googlePhotosAlbum.getId()));
            if (googlePhotoAlbumsOfTitle.primaryAlbum().isDone() && !googlePhotoAlbumsOfTitle.primaryAlbum().isCompletedExceptionally()) {
                albumStateBuilder.setPrimaryAlbumId(googlePhotoAlbumsOfTitle.primaryAlbum().getNow(null).getId());
            }
            albumsStatBuilder.putUploadedAlbumIdByTitle(title, albumStateBuilder.build());
        });

        AlbumsState newAlbumState = albumsStatBuilder.build();
        if (!newAlbumState.equals(albumState)) {
            albumState = newAlbumState;
            varStore.saveValue(VAR_STORE_KEY, newAlbumState);
        }
    }
}
