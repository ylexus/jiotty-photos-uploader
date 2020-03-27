package net.yudichev.googlephotosupload.core;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import org.immutables.value.Value;
import org.immutables.value.Value.Immutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

final class DirectoryStructureSupplierImpl implements DirectoryStructureSupplier {
    private static final Logger logger = LoggerFactory.getLogger(DirectoryStructureSupplierImpl.class);

    private final FilesystemManager filesystemManager;
    private final ProgressStatusFactory progressStatusFactory;

    @Inject
    DirectoryStructureSupplierImpl(FilesystemManager filesystemManager,
                                   ProgressStatusFactory progressStatusFactory) {
        this.filesystemManager = checkNotNull(filesystemManager);
        this.progressStatusFactory = progressStatusFactory;
    }

    @Override
    public CompletableFuture<List<AlbumDirectory>> listAlbumDirectories(Path rootDir) {
        checkArgument(Files.isDirectory(rootDir), "Path is not a directory: %s", rootDir);
        ProgressStatus progressStatus = progressStatusFactory.create("Scanning directory structure", Optional.empty());
        int rootNameCount = rootDir.getNameCount();
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Building album list from the file system...");
            ImmutableList.Builder<AlbumDirectory> listBuilder = ImmutableList.builder();
            filesystemManager.walkDirectories(rootDir, path -> {
                listBuilder.add(AlbumDirectory.of(path, toAlbumTitle(path, rootNameCount)));
                progressStatus.incrementSuccess();
            });
            List<AlbumDirectory> directoriesByAlbumTitle = listBuilder.build();
            logger.info("... done, {} directories found that will be used as albums", directoriesByAlbumTitle.size());
            progressStatus.closeSuccessfully();
            return directoriesByAlbumTitle;
        });
    }

    private Optional<String> toAlbumTitle(Path path, int rootNameCount) {
        int nameCount = path.getNameCount();
        if (nameCount > rootNameCount) {
            Path albumNamePath = path.subpath(rootNameCount, nameCount);
            return Optional.of(String.join(": ", Streams.stream(albumNamePath.iterator())
                    .map(Path::toString)
                    .collect(toImmutableList())));
        } else {
            return Optional.empty();
        }
    }

    @Immutable
    @PackagePrivateImmutablesStyle
    interface BaseAlbumDirectory {
        @Value.Parameter
        Path path();

        @Value.Parameter
        Optional<String> albumTitle();
    }
}