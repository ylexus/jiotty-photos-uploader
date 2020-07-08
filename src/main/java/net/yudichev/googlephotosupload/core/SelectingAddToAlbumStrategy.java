package net.yudichev.googlephotosupload.core;

import com.google.inject.BindingAnnotation;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;

import javax.inject.Inject;
import javax.inject.Provider;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static net.yudichev.googlephotosupload.core.AddToAlbumMethod.WHILE_CREATING_ITEMS;

final class SelectingAddToAlbumStrategy implements AddToAlbumStrategy {
    private final PreferencesManager preferencesManager;
    private final Provider<AddToAlbumStrategy> whileCreatingItemsStrategyProvider;
    private final Provider<AddToAlbumStrategy> afterCreatingItemsStrategyProvider;

    @Inject
    SelectingAddToAlbumStrategy(PreferencesManager preferencesManager,
                                @WhileCreatingItems Provider<AddToAlbumStrategy> whileCreatingItemsStrategyProvider,
                                @AfterCreatingItemsSorted Provider<AddToAlbumStrategy> afterCreatingItemsStrategyProvider) {
        this.preferencesManager = checkNotNull(preferencesManager);
        this.whileCreatingItemsStrategyProvider = checkNotNull(whileCreatingItemsStrategyProvider);
        this.afterCreatingItemsStrategyProvider = checkNotNull(afterCreatingItemsStrategyProvider);
    }

    @Override
    public CompletableFuture<Void> addToAlbum(CompletableFuture<List<PathState>> createMediaDataResultsFuture,
                                              Optional<GooglePhotosAlbum> googlePhotosAlbum,
                                              ProgressStatus fileProgressStatus,
                                              BiFunction<Optional<String>, List<PathState>, CompletableFuture<List<PathMediaItemOrError>>> createMediaItems,
                                              Function<Path, ItemState> itemStateRetriever) {
        return selectDelegate().addToAlbum(createMediaDataResultsFuture, googlePhotosAlbum, fileProgressStatus, createMediaItems, itemStateRetriever);
    }

    private AddToAlbumStrategy selectDelegate() {
        return preferencesManager.get().addToAlbumStrategy().orElseThrow(IllegalStateException::new) == WHILE_CREATING_ITEMS ?
                whileCreatingItemsStrategyProvider.get() : afterCreatingItemsStrategyProvider.get();
    }

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface WhileCreatingItems {
    }

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface AfterCreatingItemsSorted {
    }
}
