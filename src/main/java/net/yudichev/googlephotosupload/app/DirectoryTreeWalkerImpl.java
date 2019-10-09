package net.yudichev.googlephotosupload.app;

import com.google.common.collect.ImmutableSet;
import net.yudichev.jiotty.common.lang.MoreThrowables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.app.Bindings.RootDir;

final class DirectoryTreeWalkerImpl implements DirectoryTreeWalker {
    private static final Logger logger = LoggerFactory.getLogger(DirectoryTreeWalkerImpl.class);
    // TODO this must be configurable
    private static final Collection<Pattern> EXCLUSION_PATTERNS = ImmutableSet.of(
            Pattern.compile("\\..*"),
            Pattern.compile(".*picasaoriginals"),
            Pattern.compile(".*picasa.ini"),
            Pattern.compile("DS_Store"),
            Pattern.compile("Thumbs.db"),
            Pattern.compile(".*\\.(txt|exe|htm)")
    );
    private final Path rootDir;

    @Inject
    DirectoryTreeWalkerImpl(@RootDir Path rootDir) {
        this.rootDir = checkNotNull(rootDir);
    }

    @Override
    public void walk(Consumer<Path> fileHandler) {
        MoreThrowables.asUnchecked(() -> Files.walkFileTree(rootDir, new FileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (EXCLUSION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(dir.getFileName().toString()).matches())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (EXCLUSION_PATTERNS.stream().noneMatch(pattern -> pattern.matcher(file.getFileName().toString()).matches())) {
                    fileHandler.accept(file);
                } else {
                    logger.info("Excluded: {}", file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                logger.warn("Unable to access path {}", file, exc);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                if (exc != null) {
                    logger.warn("Problem reading directory {}", dir, exc);
                }
                return FileVisitResult.CONTINUE;
            }
        }));
    }
}
