package net.yudichev.googlephotosupload.core;

import javax.inject.Inject;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

final class FilesystemManagerImpl implements FilesystemManager {
    private final PreferencesSupplier preferencesSupplier;

    @Inject
    FilesystemManagerImpl(PreferencesSupplier preferencesSupplier) {
        this.preferencesSupplier = checkNotNull(preferencesSupplier);
    }

    @Override
    public void walkDirectories(Path rootDir, Consumer<Path> directoryHandler) {
        var preferences = preferencesSupplier.get();
        asUnchecked(() -> Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new RuntimeException("Interrupted");
                }
                if (preferences.anyMatch(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                directoryHandler.accept(dir);
                return FileVisitResult.CONTINUE;
            }
        }));
    }

    @Override
    public List<Path> listFiles(Path directory) {
        var preferences = preferencesSupplier.get();
        return getAsUnchecked(() -> {
            try (var pathStream = Files.list(directory)) {
                return pathStream
                        .filter(Files::isRegularFile)
                        .filter(preferences::noneMatch)
                        .collect(toImmutableList());
            }
        });
    }
}
