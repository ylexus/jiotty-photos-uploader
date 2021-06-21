package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

interface AddToAlbumStrategy {
    CompletableFuture<Void> addToAlbum(CompletableFuture<List<PathState>> createMediaDataResultsFuture,
                                       Optional<GooglePhotosAlbum> googlePhotosAlbum,
                                       ProgressStatus fileProgressStatus,
                                       ProgressStatus directoryProgressStatus,
                                       BiFunction<Optional<String>, List<PathState>, CompletableFuture<List<PathMediaItemOrError>>> createMediaItems,
                                       Function<Path, ItemState> itemStateRetriever);
}
