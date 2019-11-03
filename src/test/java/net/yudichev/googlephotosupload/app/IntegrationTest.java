package net.yudichev.googlephotosupload.app;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import net.yudichev.googlephotosupload.app.RecordingGooglePhotosClient.CreatedGooglePhotosAlbum;
import net.yudichev.jiotty.common.app.Application;
import net.yudichev.jiotty.common.async.ExecutorModule;
import net.yudichev.jiotty.common.lang.Json;
import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import net.yudichev.jiotty.common.varstore.VarStore;
import net.yudichev.jiotty.common.varstore.VarStoreModule;
import net.yudichev.jiotty.connector.google.photos.GoogleMediaItem;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.spotify.hamcrest.optional.OptionalMatchers.optionalWithValue;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.util.stream.Collectors.toList;
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
        Files.write(outerAlbumDir.resolve("picasa.ini"), new byte[]{1});

        Files.createDirectories(root.resolve("DS_Store"));

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
        assertThat(varStoreData.photosUploader().uploadedMediaItemIdByAbsolutePath().get(outerAlbumPhotoAbsolutePath),
                is(ItemState.of(Optional.of(outerAlbumPhotoAbsolutePath), Optional.of("outer-album"))));
    }

    @Test
    void skipsUploadIfSavedStateShowsAlreadyUploaded() throws InterruptedException {
        VarStore varStore = Guice.createInjector(new VarStoreModule(varStoreAppName)).getInstance(VarStore.class);
        String photosUploaderKey = "photosUploader";
        String outerAlbumPhotoAbsolutePath = outerAlbumPhoto.toAbsolutePath().toString();
        varStore.saveValue(photosUploaderKey, UploadState.builder()
                .putUploadedMediaItemIdByAbsolutePath(outerAlbumPhotoAbsolutePath,
                        ItemState.of(Optional.of(outerAlbumPhotoAbsolutePath), Optional.of("outer-album")))
                .build());

        doExecuteUpload();

        assertThat(googlePhotosClient.getAllItems(), not(hasItem(itemForFile(outerAlbumPhoto))));
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
                allOf(itemForFile(rootPhoto), itemWithNoAlbum()),
                // outer album item skipped
                allOf(itemForFile(innerAlbumPhoto), itemInAlbumWithId(equalTo("outer-album: inner-album")))));

        VarStoreData varStoreData = readVarStoreDirectly();
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
    void mergesPreExistingEmptyAlbumsWithSameNameAndReusesResultingAlbum() throws InterruptedException, TimeoutException, ExecutionException {
        googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        doExecuteUpload();

        doVerifyGoogleClientItemState();
        assertThat(googlePhotosClient.getAllAlbums(), containsInAnyOrder(
                albumWithId("outer-album"),
                albumWithId("outer-album1"),
                albumWithId("outer-album: inner-album")));
    }

    @Test
    void mergesPreExistingNonEmptyAlbumsWithSameNameAndReusesResultingAlbum() throws InterruptedException, TimeoutException, ExecutionException, IOException {
        GooglePhotosAlbum preExistingAlbum1 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        GooglePhotosAlbum preExistingAlbum2 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        GooglePhotosAlbum preExistingAlbum3 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        Path preExistingPhoto1 = uploadPhoto(preExistingAlbum1, "photo1.jpg");
        Path preExistingPhoto2 = uploadPhoto(preExistingAlbum2, "photo2.jpg");

        doExecuteUpload();

        assertThat(googlePhotosClient.getAllItems(), containsInAnyOrder(
                allOf(itemForFile(rootPhoto), itemWithNoAlbum()),
                allOf(itemForFile(preExistingPhoto1), itemInAlbumWithId(equalTo("outer-album"))),
                allOf(itemForFile(preExistingPhoto2), itemInAlbumWithId(equalTo("outer-album"))),
                allOf(itemForFile(outerAlbumPhoto), itemInAlbumWithId(equalTo("outer-album"))),
                allOf(itemForFile(innerAlbumPhoto), itemInAlbumWithId(equalTo("outer-album: inner-album")))));
        assertThat(googlePhotosClient.getAllAlbums(), containsInAnyOrder(
                albumWithId("outer-album"),
                albumWithId("outer-album1"),
                albumWithId("outer-album2"),
                albumWithId("outer-album: inner-album")));
        assertThat(preExistingAlbum2, is(emptyAlbum()));
        assertThat(preExistingAlbum3, is(emptyAlbum()));
    }

    @Test
    void mergesPreExistingNonEmptyAlbumsWithSamePhotoInThem() throws InterruptedException, TimeoutException, ExecutionException, IOException {
        GooglePhotosAlbum preExistingAlbum1 = googlePhotosClient.createAlbum("outer-album").get(3, TimeUnit.SECONDS);
        GooglePhotosAlbum preExistingAlbum2 = googlePhotosClient.createAlbum("outer-album").get(3, TimeUnit.SECONDS);

        Path preExistingPhoto1 = uploadPhoto(preExistingAlbum1, "photo1.jpg");
        preExistingAlbum2.addMediaItemsByIds(ImmutableList.of(preExistingPhoto1.toAbsolutePath().toString())).get(3, TimeUnit.SECONDS);

        Files.delete(outerAlbumPhoto);

        doExecuteUpload();

        assertThat(preExistingAlbum1, is(albumWithItems(contains(itemForFile(preExistingPhoto1)))));
        assertThat(preExistingAlbum2, is(emptyAlbum()));
    }

    @Test
    void mergesAlbumsWithMoreThanMaxItemsAllowedPerRequest() throws InterruptedException, TimeoutException, ExecutionException, IOException {
        GooglePhotosAlbum preExistingAlbum1 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        GooglePhotosAlbum preExistingAlbum2 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        uploadPhoto(preExistingAlbum1, "photo-in-album1.jpg");
        List<Path> filePaths = IntStream.range(0, 51)
                .mapToObj(i -> getAsUnchecked(() -> uploadPhoto(preExistingAlbum2, "photo" + i + ".jpg")))
                .collect(toList());

        doExecuteUpload();

        List<GoogleMediaItem> outerAlbumItems = preExistingAlbum1.getMediaItems().get(3, TimeUnit.SECONDS);
        assertThat(outerAlbumItems, hasSize(53));
        filePaths.forEach(path -> assertThat(outerAlbumItems, hasItem(itemForFile(path))));
        assertThat(outerAlbumItems, hasItem(itemForFile(outerAlbumPhoto)));

        assertThat(preExistingAlbum2, is(emptyAlbum()));
    }

    @Test
    void mergesAlbumsWithExactlyMaxItemsAllowedPerRequest() throws InterruptedException, TimeoutException, ExecutionException, IOException {
        GooglePhotosAlbum preExistingAlbum1 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        GooglePhotosAlbum preExistingAlbum2 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        uploadPhoto(preExistingAlbum1, "photo-in-album1.jpg");
        List<Path> filePaths = IntStream.range(0, 49)
                .mapToObj(i -> getAsUnchecked(() -> uploadPhoto(preExistingAlbum2, "photo" + i + ".jpg")))
                .collect(toList());

        doExecuteUpload();

        List<GoogleMediaItem> outerAlbumItems = preExistingAlbum1.getMediaItems().get(3, TimeUnit.SECONDS);
        assertThat(outerAlbumItems, hasSize(51));
        filePaths.forEach(path -> assertThat(outerAlbumItems, hasItem(itemForFile(path))));
        assertThat(outerAlbumItems, hasItem(itemForFile(outerAlbumPhoto)));

        assertThat(preExistingAlbum2, is(emptyAlbum()));
    }

    @Test
    void mergesPreExistingAlbumsWithSameNameSecondOneNonEmptyAndReusesResultingAlbum() throws InterruptedException, TimeoutException, ExecutionException, IOException {
        CompletableFuture<GooglePhotosAlbum> preExistingEmptyAlbum = googlePhotosClient.createAlbum("outer-album");
        preExistingEmptyAlbum.get(1, TimeUnit.SECONDS);
        GooglePhotosAlbum preExistingAlbum2 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        Path preExistingPhoto2 = uploadPhoto(preExistingAlbum2, "photo2.jpg");

        doExecuteUpload();

        assertThat(googlePhotosClient.getAllItems(), containsInAnyOrder(
                allOf(itemForFile(rootPhoto), itemWithNoAlbum()),
                allOf(itemForFile(preExistingPhoto2), itemInAlbumWithId(equalTo("outer-album1"))),
                allOf(itemForFile(outerAlbumPhoto), itemInAlbumWithId(equalTo("outer-album1"))),
                allOf(itemForFile(innerAlbumPhoto), itemInAlbumWithId(equalTo("outer-album: inner-album")))));
        assertThat(googlePhotosClient.getAllAlbums(), containsInAnyOrder(
                allOf(albumWithId("outer-album1"),
                        albumWithItems(containsInAnyOrder(itemForFile(outerAlbumPhoto), itemForFile(preExistingPhoto2)))),
                allOf(albumWithId("outer-album"), emptyAlbum()),
                allOf(albumWithId("outer-album: inner-album"), albumWithItems(contains(itemForFile(innerAlbumPhoto))))));
    }

    @Test
    void testPermanentUploadFailureIsIgnored() throws IOException, InterruptedException {
        Path failedPhoto = root.resolve("failOnMe.jpg");
        Files.write(failedPhoto, new byte[]{0});

        doUploadTest();
    }

    @Test
    void testPermanentAlbumCreationFailureStopsUpload() throws IOException, InterruptedException {
        Path failOnMeAlbumDir = root.resolve("failOnMe");
        Files.createDirectories(failOnMeAlbumDir);
        Path photo = failOnMeAlbumDir.resolve("photo-new.jpg");
        Files.write(photo, new byte[]{0});

        doExecuteUpload();

        assertThat(googlePhotosClient.getAllItems(), is(empty()));
        assertThat(googlePhotosClient.getAllItems(), is(empty()));
    }

    private Path uploadPhoto(GooglePhotosAlbum album, String fileName) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Path path = null;
        try {
            path = root.resolve(fileName);
            Files.write(path, new byte[]{0});

            googlePhotosClient.uploadMediaItem(Optional.of(album.getId()), path).get(1, TimeUnit.SECONDS);
        } finally {
            if (path != null) {
                Files.delete(path);
            }
        }
        return path;
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
    }

    private void doVerifyGoogleClientState() {
        doVerifyGoogleClientItemState();
        doVerifyGoogleCloudAlbumState();
    }

    private void doVerifyGoogleCloudAlbumState() {
        assertThat(googlePhotosClient.getAllAlbums(), containsInAnyOrder(
                albumWithId("outer-album"),
                albumWithId("outer-album: inner-album")));
    }

    private void doVerifyGoogleClientItemState() {
        assertThat(googlePhotosClient.getAllItems(), containsInAnyOrder(
                allOf(itemForFile(rootPhoto), itemWithNoAlbum()),
                allOf(itemForFile(outerAlbumPhoto), itemInAlbumWithId(equalTo("outer-album"))),
                allOf(itemForFile(innerAlbumPhoto), itemInAlbumWithId(equalTo("outer-album: inner-album")))));
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
        if (!Files.exists(dir)) {
            return;
        }
        asUnchecked(() -> Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                return delete(file);
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return delete(dir);
            }

            @SuppressWarnings("SameReturnValue")
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
                return getOnlyElement(actual.getAlbumIds());
            }
        };
    }

    private static Matcher<CreatedGooglePhotosAlbum> albumWithId(String albumId) {
        return albumWithId(equalTo(albumId));
    }

    private static Matcher<CreatedGooglePhotosAlbum> albumWithId(Matcher<String> albumIdMatcher) {
        return new FeatureMatcher<CreatedGooglePhotosAlbum, String>(albumIdMatcher, "album with name", "album with name") {
            @Override
            protected String featureValueOf(CreatedGooglePhotosAlbum actual) {
                return actual.getId();
            }
        };
    }

    private static Matcher<? super GooglePhotosAlbum> albumWithItems(Matcher<Iterable<? extends GoogleMediaItem>> itemsMatcher) {
        return new FeatureMatcher<GooglePhotosAlbum, Iterable<GoogleMediaItem>>(
                itemsMatcher, "album with items", "album with items") {
            @Override
            protected Iterable<GoogleMediaItem> featureValueOf(GooglePhotosAlbum actual) {
                return getAsUnchecked(() -> actual.getMediaItems(directExecutor()).get(1, TimeUnit.SECONDS));
            }
        };
    }

    private static Matcher<? super GooglePhotosAlbum> emptyAlbum() {
        return new CustomTypeSafeMatcher<GooglePhotosAlbum>("empty album") {
            @Override
            protected boolean matchesSafely(GooglePhotosAlbum item) {
                return item.getMediaItemCount() == 0;
            }
        };
    }

    private static Matcher<UploadedGoogleMediaItem> itemWithNoAlbum() {
        return new CustomTypeSafeMatcher<UploadedGoogleMediaItem>("item with no album") {
            @Override
            protected boolean matchesSafely(UploadedGoogleMediaItem item) {
                return item.getAlbumIds().isEmpty();
            }
        };
    }

    private static Matcher<? super GoogleMediaItem> itemForFile(Matcher<Path> fileMatcher) {
        FeatureMatcher<String, Path> absolutePathMatcher = new FeatureMatcher<String, Path>(fileMatcher, "absolute path", "absolute path") {
            @Override
            protected Path featureValueOf(String actual) {
                return Paths.get(actual);
            }
        };
        return new FeatureMatcher<GoogleMediaItem, String>(absolutePathMatcher, "item for file path", "item for file path") {
            @Override
            protected String featureValueOf(GoogleMediaItem actual) {
                return actual.getId();
            }
        };
    }

    private static Matcher<? super GoogleMediaItem> itemForFile(Path filePath) {
        return itemForFile(equalTo(filePath));
    }

    @Immutable
    @PackagePrivateImmutablesStyle
    @JsonDeserialize
    interface BaseVarStoreData {
        UploadState photosUploader();
    }
}
