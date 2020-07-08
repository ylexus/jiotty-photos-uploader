package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.google.common.collect.Lists.partition;
import static net.yudichev.googlephotosupload.core.GooglePhotosUploaderImpl.GOOGLE_PHOTOS_API_BATCH_SIZE;
import static net.yudichev.jiotty.common.lang.CompletableFutures.toFutureOfList;

final class AddToAlbumWhileCreatingStrategy implements AddToAlbumStrategy {
    @Override
    public CompletableFuture<Void> addToAlbum(CompletableFuture<List<PathState>> createMediaDataResultsFuture,
                                              Optional<GooglePhotosAlbum> googlePhotosAlbum,
                                              ProgressStatus fileProgressStatus,
                                              BiFunction<Optional<String>, List<PathState>, CompletableFuture<List<PathMediaItemOrError>>> createMediaItems,
                                              Function<Path, ItemState> itemStateRetriever) {
        return createMediaDataResultsFuture
                .thenCompose(createMediaDataResults -> partition(createMediaDataResults, GOOGLE_PHOTOS_API_BATCH_SIZE).stream()
                        .map(pathStates -> createMediaItems.apply(googlePhotosAlbum.map(GooglePhotosAlbum::getId), pathStates))
                        .collect(toFutureOfList())
                        .thenApply(voids -> null));
    }
}