package net.yudichev.googlephotosupload.app;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.inject.Guice;
import net.jiotty.common.app.Application;
import net.jiotty.common.async.ExecutorModule;
import net.jiotty.common.lang.Json;
import net.jiotty.common.lang.PackagePrivateImmutablesStyle;
import net.jiotty.common.varstore.VarStore;
import net.jiotty.common.varstore.VarStoreModule;
import org.apache.commons.cli.*;
import org.hamcrest.CustomTypeSafeMatcher;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.immutables.value.Value.Immutable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.spotify.hamcrest.optional.OptionalMatchers.optionalWithValue;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.FileVisitResult.CONTINUE;
import static net.jiotty.common.lang.MoreThrowables.asUnchecked;
import static net.jiotty.common.lang.MoreThrowables.getAsUnchecked;
import static net.yudichev.googlephotosupload.app.RecordingGooglePhotosClient.UploadedGoogleMediaItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ExtendWith(MockitoExtension.class)
final class IntegrationTest {
    private static final SecureRandom RANDOM = new SecureRandom();
    private String varStoreAppName;
    private Path root;
    private Path rootPhoto;
    private Path outerAlbumPhoto;
    private Path innerAlbumPhoto;
    private Path varStoreDir;
    private RecordingGooglePhotosClient googlePhotosClient;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createTempDirectory(getClass().getSimpleName());
        rootPhoto = root.resolve("root-photo.jpg");
        Files.write(rootPhoto, new byte[]{0});
        Path outerAlbumDir = root.resolve("outer-album");
        Files.createDirectories(outerAlbumDir);
        outerAlbumPhoto = outerAlbumDir.resolve("outer-album-photo.jpg");
        Files.write(outerAlbumPhoto, new byte[]{1});
        Path innerAlbumDir = outerAlbumDir.resolve("inner-album");
        Files.createDirectories(innerAlbumDir);
        innerAlbumPhoto = innerAlbumDir.resolve("inner-album-photo.jpg");
        Files.write(innerAlbumPhoto, new byte[]{2});

        // TODO make test var store write to same temp dir not to pollute home directories
        varStoreAppName = IntegrationTest.class.getSimpleName() + RANDOM.nextInt();
        varStoreDir = Paths.get(System.getProperty("user.home"), "." + varStoreAppName);
        googlePhotosClient = new RecordingGooglePhotosClient();
    }

    @Test
    void testUploadsCorrectly() throws InterruptedException, IOException {
        doUploadTest();
    }

    @Test
    void testHandlesResourceExhaustedExceptionsCorrectly() throws IOException, InterruptedException {
        googlePhotosClient.enableResourceExhaustedExceptions();
        doUploadTest();
    }

    @Test
    void testHandlesInvalidMediaIdExceptionsCorrectly() throws IOException, InterruptedException {
        VarStore varStore = Guice.createInjector(new VarStoreModule(varStoreAppName)).getInstance(VarStore.class);
        String photosUploaderKey = "photosUploader";
        String outerAlbumPhotoAbsolutePath = outerAlbumPhoto.toAbsolutePath().toString();
        varStore.saveValue(photosUploaderKey, UploadState.builder()
                .putUploadedMediaItemIdByAbsolutePath(outerAlbumPhotoAbsolutePath, ItemState.of("some-unknown-media-item-id", Optional.empty()))
                .build());

        doUploadTest();

        Optional<UploadState> newUploadState = varStore.readValue(UploadState.class, photosUploaderKey);
        assertThat(newUploadState, is(optionalWithValue()));
        //noinspection OptionalGetWithoutIsPresent
        assertThat(newUploadState.get().uploadedMediaItemIdByAbsolutePath().get(outerAlbumPhotoAbsolutePath),
                is(ItemState.of(outerAlbumPhotoAbsolutePath, Optional.of("outer-album"))));
    }

    @Test
    void reUploadsIfSavedStateShowsNoAlbum() throws IOException, InterruptedException {
        VarStore varStore = Guice.createInjector(new VarStoreModule(varStoreAppName)).getInstance(VarStore.class);
        String photosUploaderKey = "photosUploader";
        String outerAlbumPhotoAbsolutePath = outerAlbumPhoto.toAbsolutePath().toString();
        varStore.saveValue(photosUploaderKey, UploadState.builder()
                .putUploadedMediaItemIdByAbsolutePath(outerAlbumPhotoAbsolutePath, ItemState.of(outerAlbumPhotoAbsolutePath, Optional.empty()))
                .build());

        doUploadTest();

        Optional<UploadState> newUploadState = varStore.readValue(UploadState.class, photosUploaderKey);
        assertThat(newUploadState, is(optionalWithValue()));
        //noinspection OptionalGetWithoutIsPresent
        assertThat(newUploadState.get().uploadedMediaItemIdByAbsolutePath().get(outerAlbumPhotoAbsolutePath),
                is(ItemState.of(outerAlbumPhotoAbsolutePath, Optional.of("outer-album"))));
    }

    @Test
    void ignoresExcludedFile() throws IOException, InterruptedException {
        Path invalidPhoto = root.resolve("excluded-file.txt");
        Files.write(invalidPhoto, new byte[]{0});

        doUploadTest();
    }

    @Test
    void handlesFailedUploadOperation() throws IOException, InterruptedException {
        Path invalidPhoto = root.resolve("not-a-media-file.bin");
        Files.write(invalidPhoto, new byte[]{0});

        doUploadTest();
    }

    @AfterEach
    void tearDown() {
        removeDir(varStoreDir);
        removeDir(root);
    }

    private void doUploadTest() throws InterruptedException, IOException {
        Options options = new Options()
                .addOption(Option.builder("r")
                        .hasArg()
                        .required()
                        .build());
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = getAsUnchecked(() -> parser.parse(options, new String[]{"-r", root.toString()}));
        CountDownLatch applicationExitedLatch = new CountDownLatch(1);
        new Thread(() -> {
            Application.builder()
                    .addModule(() -> new CommandLineModule(commandLine))
                    .addModule(ExecutorModule::new)
                    .addModule(() -> new VarStoreModule(varStoreAppName))
                    .addModule(() -> new MockGooglePhotosModule(googlePhotosClient))
                    .addModule(() -> new UploadPhotosModule(1))
                    .build()
                    .run();
            applicationExitedLatch.countDown();
        }, "application main").start();
        applicationExitedLatch.await(5, TimeUnit.SECONDS);

        Collection<UploadedGoogleMediaItem> allItems = googlePhotosClient.getAllItems();
        assertThat(allItems, containsInAnyOrder(
                allOf(itemForFile(equalTo(rootPhoto)), itemWithNoAlbum()),
                allOf(itemForFile(equalTo(outerAlbumPhoto)), itemInAlbumWithId(equalTo("outer-album"))),
                allOf(itemForFile(equalTo(innerAlbumPhoto)), itemInAlbumWithId(equalTo("outer-album: inner-album")))));

        VarStoreData varStoreData = Json.parse(new String(Files.readAllBytes(varStoreDir.resolve("data.json")), UTF_8), VarStoreData.class);
        assertThat(varStoreData.albumManager().uploadedAlbumIdByTitle().keySet(), containsInAnyOrder("outer-album", "outer-album: inner-album"));
        Map<String, ItemState> uploadedMediaItemIdByAbsolutePath = varStoreData.photosUploader().uploadedMediaItemIdByAbsolutePath();
        assertThat(uploadedMediaItemIdByAbsolutePath.values(), hasSize(3));
        String innerPhotoPath = innerAlbumPhoto.toAbsolutePath().toString();
        String outerPhotoPath = outerAlbumPhoto.toAbsolutePath().toString();
        String rootPhotoPath = rootPhoto.toAbsolutePath().toString();
        assertThat(uploadedMediaItemIdByAbsolutePath.get(rootPhotoPath), is(ItemState.of(rootPhotoPath, Optional.empty())));
        assertThat(uploadedMediaItemIdByAbsolutePath.get(innerPhotoPath), is(ItemState.of(innerPhotoPath, Optional.of("outer-album: inner-album"))));
        assertThat(uploadedMediaItemIdByAbsolutePath.get(outerPhotoPath), is(ItemState.of(outerPhotoPath, Optional.of("outer-album"))));
    }

    private void removeDir(Path dir) {
        asUnchecked(() -> Files.walkFileTree(dir, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                return delete(file);
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return delete(dir);
            }

            private FileVisitResult delete(Path path) throws IOException {
                Files.delete(path);
                return CONTINUE;
            }
        }));
    }

    private static Matcher<UploadedGoogleMediaItem> itemInAlbumWithId(Matcher<String> albumIdMatcher) {
        return new FeatureMatcher<UploadedGoogleMediaItem, String>(albumIdMatcher, "item in album", "item in album") {
            @Override
            protected String featureValueOf(UploadedGoogleMediaItem actual) {
                return actual.getAlbumId().orElse("no album");
            }
        };
    }

    private static Matcher<UploadedGoogleMediaItem> itemWithNoAlbum() {
        return new CustomTypeSafeMatcher<UploadedGoogleMediaItem>("item with no album") {
            @Override
            protected boolean matchesSafely(UploadedGoogleMediaItem item) {
                return !item.getAlbumId().isPresent();
            }
        };
    }

    private static Matcher<UploadedGoogleMediaItem> itemForFile(Matcher<Path> fileMatcher) {
        return new FeatureMatcher<UploadedGoogleMediaItem, Path>(fileMatcher, "item for file", "item for file") {
            @Override
            protected Path featureValueOf(UploadedGoogleMediaItem actual) {
                return actual.getFile();
            }
        };
    }

    @Immutable
    @PackagePrivateImmutablesStyle
    @JsonDeserialize
    interface BaseVarStoreData {
        AlbumState albumManager();

        UploadState photosUploader();
    }
}
