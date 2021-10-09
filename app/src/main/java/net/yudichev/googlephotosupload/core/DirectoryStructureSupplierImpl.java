package net.yudichev.googlephotosupload.core;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.file.FileVisitResult.CONTINUE;
import static net.yudichev.jiotty.common.lang.HumanReadableExceptionMessage.humanReadableMessage;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;

final class DirectoryStructureSupplierImpl implements DirectoryStructureSupplier {
    private static final Logger logger = LoggerFactory.getLogger(DirectoryStructureSupplierImpl.class);

    private final ProgressStatusFactory progressStatusFactory;
    private final PreferencesManager preferencesManager;
    private final ResourceBundle resourceBundle;

    @Inject
    DirectoryStructureSupplierImpl(ProgressStatusFactory progressStatusFactory,
                                   PreferencesManager preferencesManager,
                                   ResourceBundle resourceBundle) {
        this.progressStatusFactory = progressStatusFactory;
        this.preferencesManager = checkNotNull(preferencesManager);
        this.resourceBundle = checkNotNull(resourceBundle);
    }

    @Override
    public CompletableFuture<List<AlbumDirectory>> listAlbumDirectories(List<Path> rootDirs) {
        var progressStatus = progressStatusFactory.create(resourceBundle.getString("directoryStructureSupplierProgressTitle"), Optional.empty());
        var resultFuture = CompletableFuture.<List<AlbumDirectory>>supplyAsync(() -> {
            rootDirs.forEach(rootDir -> checkArgument(Files.isDirectory(rootDir), "Path is not a directory: %s", rootDir));
            logger.info("Scanning file system starting at roots {}...", rootDirs);
            var result = rootDirs.stream()
                    .flatMap(rootDir -> listAlbumDirectories(progressStatus, rootDir))
                    .collect(toImmutableList());
            logger.info("... done, {} directories found that will be used as albums", result.size());
            return result;
        });
        resultFuture.whenComplete((ignored, e) -> progressStatus.close(e == null));
        return resultFuture;
    }

    private Stream<AlbumDirectory> listAlbumDirectories(ProgressStatus progressStatus, Path rootDir) {
        var preferences = preferencesManager.get();
        var rootNameCount = rootDir.getNameCount();
        preferences.relevantDirDepthLimit().ifPresent(limit -> logger.info("Only using directories up to depth level {} as albums", limit));
        var relevantDepthLimit = preferences.relevantDirDepthLimit().orElse(Integer.MAX_VALUE);
        Map<Path, ImmutableList.Builder<Path>> fileListBuilderByParentDir = new HashMap<>();
        asUnchecked(() -> Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
            private int currentDepth;
            private Path currentRelevantDir;

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (++currentDepth <= relevantDepthLimit) {
                    setRelevantDir(dir);
                }
                return CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                if (--currentDepth <= relevantDepthLimit) {
                    setRelevantDir(dir.getParent());
                }

                if (exc != null) {
                    progressStatus.addFailure(KeyedError.of(dir, humanReadableMessage(exc)));
                }
                return CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (preferences.shouldIncludePath(file)) {
                    fileListBuilderByParentDir
                            .computeIfAbsent(currentRelevantDir, ignored -> ImmutableList.builder())
                            .add(file);
                    progressStatus.updateDescription(file.toAbsolutePath().toString());
                    logger.debug("Including file: {}", file);
                    progressStatus.incrementSuccess();
                } else {
                    logger.debug("Skipping file as it does not pass include/exclude pattern test: {}", file);
                }
                return CONTINUE;
            }

            private void setRelevantDir(Path dir) {
                currentRelevantDir = dir;
                logger.debug("Current relevant dir {}", currentRelevantDir);
            }
        }));

        return fileListBuilderByParentDir.entrySet().stream()
                .map(entry -> AlbumDirectory.builder()
                        .setPath(entry.getKey())
                        .setAlbumTitle(toAlbumTitle(entry.getKey(), preferences.albumDelimiter(), rootNameCount))
                        .setFiles(entry.getValue().build())
                        .build());
    }

    private static Optional<String> toAlbumTitle(Path path, String albumNameDelimiter, int rootNameCount) {
        var nameCount = path.getNameCount();
        if (nameCount > rootNameCount) {
            var albumNamePath = path.subpath(rootNameCount, nameCount);
            return Optional.of(String.join(albumNameDelimiter, Streams.stream(albumNamePath.iterator())
                    .map(Path::toString)
                    .collect(toImmutableList())));
        } else {
            return Optional.empty();
        }
    }

    @Value.Immutable
    @PackagePrivateImmutablesStyle
    interface BaseAlbumDirectory {
        Path path();

        Optional<String> albumTitle();

        List<Path> files();
    }
}