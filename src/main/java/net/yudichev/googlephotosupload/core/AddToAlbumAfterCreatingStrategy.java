package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.lang.CompletableFutures;
import net.yudichev.jiotty.connector.google.photos.GoogleMediaItem;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;

import javax.inject.Inject;
import javax.inject.Provider;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Lists.partition;
import static java.util.Comparator.comparing;
import static net.yudichev.googlephotosupload.core.Bindings.Backpressured;
import static net.yudichev.googlephotosupload.core.GooglePhotosUploaderImpl.GOOGLE_PHOTOS_API_BATCH_SIZE;
import static net.yudichev.jiotty.common.lang.CompletableFutures.toFutureOfListChaining;

final class AddToAlbumAfterCreatingStrategy implements AddToAlbumStrategy {
    private final Provider<ExecutorService> executorServiceProvider;
    private final FatalUserCorrectableRemoteApiExceptionHandler fatalUserCorrectableHandler;
    private final CloudOperationHelper cloudOperationHelper;

    @Inject
    AddToAlbumAfterCreatingStrategy(@Backpressured Provider<ExecutorService> executorServiceProvider,
                                    FatalUserCorrectableRemoteApiExceptionHandler fatalUserCorrectableHandler,
                                    CloudOperationHelper cloudOperationHelper) {
        this.executorServiceProvider = checkNotNull(executorServiceProvider);
        this.fatalUserCorrectableHandler = checkNotNull(fatalUserCorrectableHandler);
        this.cloudOperationHelper = checkNotNull(cloudOperationHelper);
    }

    @Override
    public CompletableFuture<Void> addToAlbum(CompletableFuture<List<PathState>> createMediaDataResultsFuture,
                                              Optional<GooglePhotosAlbum> googlePhotosAlbum,
                                              ProgressStatus fileProgressStatus,
                                              BiFunction<Optional<String>, List<PathState>, CompletableFuture<List<PathMediaItemOrError>>> createMediaItems,
                                              Function<Path, ItemState> itemStateRetriever) {
        return createMediaDataResultsFuture
                .thenCompose(createMediaDataResults -> partition(createMediaDataResults, GOOGLE_PHOTOS_API_BATCH_SIZE).stream()
                        .collect(toFutureOfListChaining(pathStates -> createMediaItems.apply(Optional.empty(), pathStates)))
                        .thenApply(lists -> lists.stream().flatMap(Collection::stream)))
                .thenCompose(pathMediaItemOrErrorStream -> addToAlbum(googlePhotosAlbum, pathMediaItemOrErrorStream, fileProgressStatus, itemStateRetriever));
    }

    private CompletionStage<Void> addToAlbum(Optional<GooglePhotosAlbum> googlePhotosAlbum,
                                             Stream<PathMediaItemOrError> pathMediaItemOrErrorStream,
                                             ProgressStatus fileProgressStatus,
                                             Function<Path, ItemState> itemStateRetriever) {
        return googlePhotosAlbum
                .map(album -> {
                    var pathMediaItemOrErrors = pathMediaItemOrErrorStream
                            .filter(pathMediaItemOrError -> itemStateRetriever.apply(pathMediaItemOrError.path()).albumId().isEmpty())
                            .collect(toImmutableList());
                    var mediaItemsToAddToAlbum = pathMediaItemOrErrors.stream()
                            .map(PathMediaItemOrError::mediaItem)
                            .sorted(comparing(GoogleMediaItem::getCreationTime))
                            // it's possible to have duplicates here, when a directory contains two copies of same media items under different file names
                            // (see https://github.com/ylexus/jiotty-photos-uploader/issues/34#issuecomment-639876779)
                            .distinct()
                            .collect(toImmutableList());
                    return cloudOperationHelper.withBackOffAndRetry("add items to album",
                            () -> partition(mediaItemsToAddToAlbum, GOOGLE_PHOTOS_API_BATCH_SIZE).stream()
                                    .collect(toFutureOfListChaining(mediaItems -> album.addMediaItems(mediaItems, executorServiceProvider.get())))
                                    .<Void>thenApply(ignored -> null),
                            fileProgressStatus::onBackoffDelay)
                            .exceptionallyCompose(exception -> fatalUserCorrectableHandler.handle(
                                    "adding items to album " + album.getTitle(), exception)
                                    .map(errorMessage -> {
                                        pathMediaItemOrErrors.stream()
                                                .map(PathMediaItemOrError::path)
                                                .forEach(path -> fileProgressStatus.addFailure(KeyedError.of(path, errorMessage)));
                                        return CompletableFutures.<Void>completedFuture();
                                    })
                                    .orElseThrow(() -> new RuntimeException(exception)));
                })
                .orElseGet(CompletableFutures::completedFuture);
    }
}