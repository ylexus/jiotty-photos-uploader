package net.yudichev.googlephotosupload.app;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import net.yudichev.jiotty.common.app.Application;
import net.yudichev.jiotty.common.async.ExecutorModule;
import net.yudichev.jiotty.common.lang.Json;
import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import net.yudichev.jiotty.common.varstore.VarStore;
import net.yudichev.jiotty.common.varstore.VarStoreModule;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.spotify.hamcrest.optional.OptionalMatchers.optionalWithValue;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.FileVisitResult.CONTINUE;
import static net.yudichev.googlephotosupload.app.RecordingGooglePhotosClient.CreatedGooglePhotosAlbum;
import static net.yudichev.googlephotosupload.app.RecordingGooglePhotosClient.UploadedGoogleMediaItem;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;
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

    @AfterEach
    void tearDown() {
        removeDir(varStoreDir);
        removeDir(root);
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
                .putUploadedMediaItemIdByAbsolutePath(outerAlbumPhotoAbsolutePath,
                        ItemState.of(Optional.of("some-unknown-media-item-id"), Optional.empty()))
                .build());

        doUploadTest();

        Optional<UploadState> newUploadState = varStore.readValue(UploadState.class, photosUploaderKey);
        assertThat(newUploadState, is(optionalWithValue()));
        //noinspection OptionalGetWithoutIsPresent
        assertThat(newUploadState.get().uploadedMediaItemIdByAbsolutePath().get(outerAlbumPhotoAbsolutePath),
                is(ItemState.of(Optional.of(outerAlbumPhotoAbsolutePath), Optional.of("outer-album"))));
    }

    @Test
    void reUploadsIfSavedStateShowsNoAlbum() throws IOException, InterruptedException {
        VarStore varStore = Guice.createInjector(new VarStoreModule(varStoreAppName)).getInstance(VarStore.class);
        String photosUploaderKey = "photosUploader";
        String outerAlbumPhotoAbsolutePath = outerAlbumPhoto.toAbsolutePath().toString();
        varStore.saveValue(photosUploaderKey, UploadState.builder()
                .putUploadedMediaItemIdByAbsolutePath(outerAlbumPhotoAbsolutePath,
                        ItemState.of(Optional.of(outerAlbumPhotoAbsolutePath), Optional.empty()))
                .build());

        doUploadTest();

        VarStoreData varStoreData = readVarStoreDirectly();
        assertThat(varStoreData.albumManager().uploadedAlbumIdByTitle(), allOf(
                hasEntry("outer-album", AlbumState.builder().addAlbumIds("outer-album").setPrimaryAlbumId("outer-album").build()),
                hasEntry("outer-album: inner-album",
                        AlbumState.builder().addAlbumIds("outer-album: inner-album").setPrimaryAlbumId("outer-album: inner-album").build())));
        assertThat(varStoreData.photosUploader().uploadedMediaItemIdByAbsolutePath().get(outerAlbumPhotoAbsolutePath),
                is(ItemState.of(Optional.of(outerAlbumPhotoAbsolutePath), Optional.of("outer-album"))));
    }

    @Test
    void ignoresIfSavedStateShowsInvalidMediaItem() throws InterruptedException, IOException {
        VarStore varStore = Guice.createInjector(new VarStoreModule(varStoreAppName)).getInstance(VarStore.class);
        String photosUploaderKey = "photosUploader";
        String outerAlbumPhotoAbsolutePath = outerAlbumPhoto.toAbsolutePath().toString();
        varStore.saveValue(photosUploaderKey, UploadState.builder()
                .putUploadedMediaItemIdByAbsolutePath(outerAlbumPhotoAbsolutePath,
                        ItemState.of(Optional.empty(), Optional.empty()))
                .build());

        doExecuteUpload();

        Collection<UploadedGoogleMediaItem> allItems = googlePhotosClient.getAllItems();
        assertThat(allItems, containsInAnyOrder(
                allOf(itemForFile(equalTo(rootPhoto)), itemWithNoAlbum()),
                // outer album item skipped
                allOf(itemForFile(equalTo(innerAlbumPhoto)), itemInAlbumWithId(equalTo("outer-album: inner-album")))));

        VarStoreData varStoreData = readVarStoreDirectly();
        doVerifyAlbumsInVarStore(varStoreData);
        assertThat(varStoreData.photosUploader().uploadedMediaItemIdByAbsolutePath().get(outerAlbumPhotoAbsolutePath),
                is(ItemState.of(Optional.empty(), Optional.empty())));
    }

    @Test
    void ignoresExcludedFile() throws IOException, InterruptedException {
        Path invalidPhoto = root.resolve("excluded-file.txt");
        Files.write(invalidPhoto, new byte[]{0});

        doUploadTest();
    }

    @Test
    void handlesFailedUploadOperationAndSavesErrorStateToVarStore() throws IOException, InterruptedException {
        Path invalidMediaItemPath = root.resolve("not-a-media-file.bin").toAbsolutePath();
        Files.write(invalidMediaItemPath, new byte[]{0});

        doExecuteUpload();

        doVerifyGoogleClientState();

        VarStoreData varStoreData = readVarStoreDirectly();
        ImmutableMap<String, ItemState> uploadedMediaItemIdByAbsolutePath = varStoreData.photosUploader().uploadedMediaItemIdByAbsolutePath();
        assertThat(uploadedMediaItemIdByAbsolutePath.values(), hasSize(4));
        doVerifyJpegFilesInVarStore(varStoreData);
        assertThat(uploadedMediaItemIdByAbsolutePath.get(invalidMediaItemPath.toString()),
                is(ItemState.of(Optional.empty(), Optional.empty())));
    }

    @Test
    void reusesPreExistingAlbum() throws InterruptedException, TimeoutException, ExecutionException {
        googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        doExecuteUpload();
        doVerifyGoogleClientState();
    }

    @Test
    void reusesPreExistingAlbumWithTwoAlbumsHavingSameName() throws InterruptedException, TimeoutException, ExecutionException {
        googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        doExecuteUpload();

        doVerifyGooglClientItemState();
        assertThat(googlePhotosClient.getAllAlbums(), containsInAnyOrder(
                albumWithId(equalTo("outer-album")),
                albumWithId(equalTo("outer-album1")),
                albumWithId(equalTo("outer-album: inner-album"))));
    }

    private VarStoreData readVarStoreDirectly() throws IOException {
        return Json.parse(new String(Files.readAllBytes(varStoreDir.resolve("data.json")), UTF_8), VarStoreData.class);
    }

    private void doVerifyJpegFilesInVarStore(VarStoreData varStoreData) {
        Map<String, ItemState> uploadedMediaItemIdByAbsolutePath = varStoreData.photosUploader().uploadedMediaItemIdByAbsolutePath();
        String innerPhotoPath = innerAlbumPhoto.toAbsolutePath().toString();
        String outerPhotoPath = outerAlbumPhoto.toAbsolutePath().toString();
        String rootPhotoPath = rootPhoto.toAbsolutePath().toString();
        assertThat(uploadedMediaItemIdByAbsolutePath.get(rootPhotoPath), is(ItemState.of(Optional.of(rootPhotoPath), Optional.empty())));
        assertThat(uploadedMediaItemIdByAbsolutePath.get(innerPhotoPath),
                is(ItemState.of(Optional.of(innerPhotoPath), Optional.of("outer-album: inner-album"))));
        assertThat(uploadedMediaItemIdByAbsolutePath.get(outerPhotoPath), is(ItemState.of(Optional.of(outerPhotoPath), Optional.of("outer-album"))));
    }

    private void doUploadTest() throws InterruptedException, IOException {
        doExecuteUpload();

        doVerifyGoogleClientState();

        VarStoreData varStoreData = readVarStoreDirectly();
        assertThat(varStoreData.photosUploader().uploadedMediaItemIdByAbsolutePath().values(), hasSize(3));
        doVerifyJpegFilesInVarStore(varStoreData);
        doVerifyAlbumsInVarStore(varStoreData);
    }

    private void doVerifyAlbumsInVarStore(VarStoreData varStoreData) {
        assertThat(varStoreData.albumManager().uploadedAlbumIdByTitle(), allOf(
                hasEntry("outer-album", AlbumState.builder().addAlbumIds("outer-album").setPrimaryAlbumId("outer-album").build()),
                hasEntry("outer-album: inner-album",
                        AlbumState.builder().addAlbumIds("outer-album: inner-album").setPrimaryAlbumId("outer-album: inner-album").build())));
    }

    private void doVerifyGoogleClientState() {
        doVerifyGooglClientItemState();
        doVerifyGoogleCloudAlbumState();
    }

    private void doVerifyGoogleCloudAlbumState() {
        assertThat(googlePhotosClient.getAllAlbums(), containsInAnyOrder(
                albumWithId(equalTo("outer-album")),
                albumWithId(equalTo("outer-album: inner-album"))));
    }

    private void doVerifyGooglClientItemState() {
        assertThat(googlePhotosClient.getAllItems(), containsInAnyOrder(
                allOf(itemForFile(equalTo(rootPhoto)), itemWithNoAlbum()),
                allOf(itemForFile(equalTo(outerAlbumPhoto)), itemInAlbumWithId(equalTo("outer-album"))),
                allOf(itemForFile(equalTo(innerAlbumPhoto)), itemInAlbumWithId(equalTo("outer-album: inner-album")))));
    }

    private void doExecuteUpload() throws InterruptedException {
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

    private static Matcher<CreatedGooglePhotosAlbum> albumWithId(Matcher<String> albumNameMatcher) {
        return new FeatureMatcher<CreatedGooglePhotosAlbum, String>(albumNameMatcher, "album with name", "album with name") {
            @Override
            protected String featureValueOf(CreatedGooglePhotosAlbum actual) {
                return actual.getId();
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
        AlbumsState albumManager();

        UploadState photosUploader();
    }
}
