package net.yudichev.googlephotosupload.core;

import com.google.common.collect.ImmutableSet;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

final class FilesystemManagerImpl implements FilesystemManager {
    // TODO this must be configurable
    private static final Collection<Pattern> EXCLUSION_PATTERNS = ImmutableSet.of(
            Pattern.compile("\\..*"),
            Pattern.compile(".*picasaoriginals"),
            Pattern.compile(".*picasa.ini"),
            Pattern.compile("DS_Store"),
            Pattern.compile("Thumbs.db"),
            Pattern.compile(".*\\.(txt|exe|htm)")
    );

    @Override
    public void walkDirectories(Path rootDir, Consumer<Path> directoryHandler) {
        asUnchecked(() -> Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new RuntimeException("Interrupted");
                }
                if (EXCLUSION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(dir.getFileName().toString()).matches())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                directoryHandler.accept(dir);
                return FileVisitResult.CONTINUE;
            }
        }));
    }

    @Override
    public List<Path> listFiles(Path directory) {
        return getAsUnchecked(() -> {
            try (Stream<Path> pathStream = Files.list(directory)) {
                return pathStream
                        .filter(Files::isRegularFile)
                        .filter(path -> EXCLUSION_PATTERNS.stream().noneMatch(pattern -> pattern.matcher(path.getFileName().toString()).matches()))
                        .collect(toImmutableList());
            }
        });
    }
}
