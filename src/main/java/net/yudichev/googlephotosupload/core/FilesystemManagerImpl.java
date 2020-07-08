package net.yudichev.googlephotosupload.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

final class FilesystemManagerImpl implements FilesystemManager {
    private static final Logger logger = LoggerFactory.getLogger(FilesystemManagerImpl.class);

    private final PreferencesManager preferencesManager;

    @Inject
    FilesystemManagerImpl(PreferencesManager preferencesManager) {
        this.preferencesManager = checkNotNull(preferencesManager);
    }

    @Override
    public void walkDirectories(Path rootDir, Consumer<Path> directoryHandler) {
        var preferences = preferencesManager.get();
        asUnchecked(() -> Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
            private final Deque<Integer> relevantItemCountStack = new ArrayDeque<>();

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new RuntimeException("Interrupted");
                }
                if (preferences.noneMatch(file)) {
                    relevantItemCountStack.push(relevantItemCountStack.pop() + 1);
                }
                return CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new RuntimeException("Interrupted");
                }
                if (preferences.anyMatch(dir)) {
                    logger.debug("Skipping dir as it matches an ignore pattern: {}", dir);
                    return SKIP_SUBTREE;
                }
                relevantItemCountStack.push(0);
                return CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                if (relevantItemCountStack.pop() > 0) {
                    if (!relevantItemCountStack.isEmpty()) {
                        // this dir is relevant, so it contributes to the parent dir's relevant count
                        relevantItemCountStack.push(relevantItemCountStack.pop() + 1);
                    }
                    directoryHandler.accept(dir);
                } else {
                    logger.debug("Skipping dir as it does not have any non-ignorable files: {}", dir);
                }
                return CONTINUE;
            }
        }));
    }

    @Override
    public List<Path> listFiles(Path directory) {
        var preferences = preferencesManager.get();
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
