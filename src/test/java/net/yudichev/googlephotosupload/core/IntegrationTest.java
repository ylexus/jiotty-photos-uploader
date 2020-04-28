package net.yudichev.googlephotosupload.core;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import net.yudichev.googlephotosupload.core.RecordingGooglePhotosClient.CreatedGooglePhotosAlbum;
import net.yudichev.jiotty.common.app.Application;
import net.yudichev.jiotty.common.async.ExecutorModule;
import net.yudichev.jiotty.common.lang.Json;
import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import net.yudichev.jiotty.common.varstore.VarStore;
import net.yudichev.jiotty.common.varstore.VarStoreModule;
import net.yudichev.jiotty.connector.google.photos.GoogleMediaItem;
import net.yudichev.jiotty.connector.google.photos.GooglePhotosAlbum;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.of;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.time.Instant.EPOCH;
import static java.time.Instant.now;
import static java.util.stream.Collectors.toList;
import static net.yudichev.googlephotosupload.cli.CliOptions.OPTIONS;
import static net.yudichev.googlephotosupload.core.IntegrationTestUploadStarter.getLastFailure;
import static net.yudichev.googlephotosupload.core.OptionalMatchers.emptyOptional;
import static net.yudichev.googlephotosupload.core.OptionalMatchers.optionalWithValue;
import static net.yudichev.googlephotosupload.core.RecordingGooglePhotosClient.UploadedGoogleMediaItem;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@SuppressWarnings("ClassWithTooManyMethods")
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

        var outerAlbumDir = root.resolve("outer-album");
        Files.createDirectories(outerAlbumDir);
        outerAlbumPhoto = outerAlbumDir.resolve("outer-album-photo.jpg");
        Files.write(outerAlbumPhoto, new byte[]{1});
        Files.write(outerAlbumDir.resolve("picasa.ini"), new byte[]{1});

        Files.createDirectories(root.resolve("DS_Store"));

        var innerAlbumDir = outerAlbumDir.resolve("inner-album");
        Files.createDirectories(innerAlbumDir);
        innerAlbumPhoto = innerAlbumDir.resolve("inner-album-photo.jpg");
        Files.write(innerAlbumPhoto, new byte[]{2});

        // TODO make test var store write to same temp dir not to pollute home directories
        varStoreAppName = IntegrationTest.class.getSimpleName() + RANDOM.nextInt();
        varStoreDir = Paths.get(System.getProperty("user.home"), "." + varStoreAppName);
        googlePhotosClient = new RecordingGooglePhotosClient();

        TestTimeModule.resetTime();
    }

    @AfterEach
    void tearDown() {
        removeDir(varStoreDir);
        removeDir(root);
    }

    @Test
    void testUploadsCorrectly() throws Exception {
        doUploadTest();

        assertThat(getLastFailure(), emptyOptional());
    }

    @Test
    void skipsUploadIfSavedStateShowsAlreadyUploaded() throws InterruptedException {
        var varStore = Guice.createInjector(new VarStoreModule(varStoreAppName)).getInstance(VarStore.class);
        var photosUploaderKey = "photosUploader";
        var outerAlbumPhotoAbsolutePath = outerAlbumPhoto.toAbsolutePath().toString();
        varStore.saveValue(photosUploaderKey, UploadState.builder()
                .putUploadedMediaItemIdByAbsolutePath(outerAlbumPhotoAbsolutePath,
                        ItemState.builder()
                                .setMediaId(outerAlbumPhotoAbsolutePath)
                                .setUploadState(UploadMediaItemState.of(outerAlbumPhotoAbsolutePath, now()))
                                .build())
                .build());

        doExecuteUpload();

        assertThat(getLastFailure(), emptyOptional());
        assertThat(googlePhotosClient.getAllItems(), not(hasItem(itemForFile(outerAlbumPhoto))));
    }

    @Test
    void testHandlesResourceExhaustedExceptionsCorrectly() throws Exception {
        googlePhotosClient.enableResourceExhaustedExceptions();

        doUploadTest();

        assertThat(getLastFailure(), emptyOptional());
    }

    @Test
    void ignoresExcludedFile() throws Exception {
        var invalidPhoto = root.resolve("excluded-file.txt");
        Files.write(invalidPhoto, new byte[]{0});

        doUploadTest();

        assertThat(getLastFailure(), emptyOptional());
    }

    @Test
    void handlesInvalidArgumentDuringCreationOfMediaItem() throws Exception {
        var invalidMediaItemPath = root.resolve("failOnMeWithInvalidArgumentDuringCreationOfMediaItem.jpg").toAbsolutePath();
        Files.write(invalidMediaItemPath, new byte[]{0});

        doExecuteUpload();

        doVerifyGoogleClientState();

        var varStoreData = readVarStoreDirectly();
        Map<String, ItemState> uploadedMediaItemIdByAbsolutePath = varStoreData.photosUploader().uploadedMediaItemIdByAbsolutePath();
        assertThat(uploadedMediaItemIdByAbsolutePath.values(), hasSize(4));

        var invalidItemPathString = invalidMediaItemPath.toAbsolutePath().toString();
        var invalidItemState = uploadedMediaItemIdByAbsolutePath.get(invalidItemPathString);
        assertThat(invalidItemState, itemStateHavingMediaId(emptyOptional()));
        assertThat(invalidItemState, itemStateHavingUploadState(optionalWithValue(allOf(
                uploadMediaItemStateHavingToken(startsWith(invalidItemPathString)),
                uploadMediaItemStateHavingInstant(equalTo(EPOCH))))));

        doVerifyJpegFilesInVarStore(varStoreData);
        assertThat(getLastFailure(), emptyOptional());
    }

    @Test
    void handlesInvalidArgumentDuringCreationOfMediaData() throws Exception {
        var invalidMediaItemPath = root.resolve("failOnMeWithInvalidArgumentDuringUploadIngMediaData.jpg").toAbsolutePath();
        Files.write(invalidMediaItemPath, new byte[]{0});

        doExecuteUpload();

        doVerifyGoogleClientState();

        var varStoreData = readVarStoreDirectly();
        Map<String, ItemState> uploadedMediaItemIdByAbsolutePath = varStoreData.photosUploader().uploadedMediaItemIdByAbsolutePath();
        assertThat(uploadedMediaItemIdByAbsolutePath.values(), hasSize(3));

        doVerifyJpegFilesInVarStore(varStoreData);
        assertThat(getLastFailure(), emptyOptional());
    }

    @Test
    void reusesPreExistingAlbum() throws Exception {
        googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        doExecuteUpload();
        doVerifyGoogleClientState();
        assertThat(getLastFailure(), emptyOptional());
    }

    @Test
    void mergesPreExistingEmptyAlbumsWithSameNameAndReusesResultingAlbum() throws Exception {
        googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        doExecuteUpload();

        doVerifyGoogleClientItemState();
        assertThat(googlePhotosClient.getAllAlbums(), containsInAnyOrder(
                albumWithId("fail-on-me-pre-existing-album"),
                albumWithId("outer-album"),
                albumWithId("outer-album1"),
                albumWithId("outer-album: inner-album")));
        assertThat(getLastFailure(), emptyOptional());
    }

    @Test
    void mergesPreExistingNonEmptyAlbumsWithSameNameAndReusesResultingAlbum() throws Exception {
        var preExistingAlbum1 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        var preExistingAlbum2 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        var preExistingAlbum3 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        var preExistingPhoto1 = uploadPhoto(preExistingAlbum1, "photo1.jpg");
        var preExistingPhoto2 = uploadPhoto(preExistingAlbum2, "photo2.jpg");

        doExecuteUpload();

        assertThat(googlePhotosClient.getAllItems(), containsInAnyOrder(
                allOf(itemForFile(rootPhoto), itemWithNoAlbum()),
                allOf(itemForFile(preExistingPhoto1), itemInAlbumWithId(equalTo("outer-album"))),
                allOf(itemForFile(preExistingPhoto2), itemInAlbumWithId(equalTo("outer-album"))),
                allOf(itemForFile(outerAlbumPhoto), itemInAlbumWithId(equalTo("outer-album"))),
                allOf(itemForFile(innerAlbumPhoto), itemInAlbumWithId(equalTo("outer-album: inner-album")))));
        assertThat(googlePhotosClient.getAllAlbums(), containsInAnyOrder(
                albumWithId("fail-on-me-pre-existing-album"),
                albumWithId("outer-album"),
                albumWithId("outer-album1"),
                albumWithId("outer-album2"),
                albumWithId("outer-album: inner-album")));
        assertThat(preExistingAlbum2, is(emptyAlbum()));
        assertThat(preExistingAlbum3, is(emptyAlbum()));
        assertThat(getLastFailure(), emptyOptional());
    }

    @Test
    void mergesPreExistingNonEmptyAlbumsWithSamePhotoInThem() throws Exception {
        var preExistingAlbum1 = googlePhotosClient.createAlbum("outer-album").get(3, TimeUnit.SECONDS);
        var preExistingAlbum2 = googlePhotosClient.createAlbum("outer-album").get(3, TimeUnit.SECONDS);

        var preExistingPhoto1 = uploadPhoto(preExistingAlbum1, "photo1.jpg");
        preExistingAlbum2.addMediaItemsByIds(of(preExistingPhoto1.toAbsolutePath().toString())).get(3, TimeUnit.SECONDS);

        Files.delete(outerAlbumPhoto);

        doExecuteUpload();

        assertThat(preExistingAlbum1, is(albumWithItems(contains(itemForFile(preExistingPhoto1)))));
        assertThat(preExistingAlbum2, is(emptyAlbum()));
        assertThat(getLastFailure(), emptyOptional());
    }

    @Test
    void mergesAlbumsWithMoreThanMaxItemsAllowedPerRequest() throws Exception {
        var preExistingAlbum1 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        var preExistingAlbum2 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        uploadPhoto(preExistingAlbum1, "photo-in-album1.jpg");
        var filePaths = IntStream.range(0, 51)
                .mapToObj(i -> getAsUnchecked(() -> uploadPhoto(preExistingAlbum2, "photo" + i + ".jpg")))
                .collect(toList());

        doExecuteUpload();

        var outerAlbumItems = preExistingAlbum1.getMediaItems().get(3, TimeUnit.SECONDS);
        assertThat(outerAlbumItems, hasSize(53));
        filePaths.forEach(path -> assertThat(outerAlbumItems, hasItem(itemForFile(path))));
        assertThat(outerAlbumItems, hasItem(itemForFile(outerAlbumPhoto)));

        assertThat(preExistingAlbum2, is(emptyAlbum()));
        assertThat(getLastFailure(), emptyOptional());
    }

    @Test
    void mergesAlbumsWithExactlyMaxItemsAllowedPerRequest() throws Exception {
        var preExistingAlbum1 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        var preExistingAlbum2 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        uploadPhoto(preExistingAlbum1, "photo-in-album1.jpg");
        var filePaths = IntStream.range(0, 49)
                .mapToObj(i -> getAsUnchecked(() -> uploadPhoto(preExistingAlbum2, "photo" + i + ".jpg")))
                .collect(toList());

        doExecuteUpload();

        var outerAlbumItems = preExistingAlbum1.getMediaItems().get(3, TimeUnit.SECONDS);
        assertThat(outerAlbumItems, hasSize(51));
        filePaths.forEach(path -> assertThat(outerAlbumItems, hasItem(itemForFile(path))));
        assertThat(outerAlbumItems, hasItem(itemForFile(outerAlbumPhoto)));

        assertThat(preExistingAlbum2, is(emptyAlbum()));
        assertThat(getLastFailure(), emptyOptional());
    }

    @Test
    void mergesPreExistingAlbumsWithSameNameSecondOneNonEmptyAndReusesResultingAlbum() throws Exception {
        googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        var preExistingAlbum2 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        var preExistingPhoto2 = uploadPhoto(preExistingAlbum2, "photo2.jpg");

        doExecuteUpload();

        assertThat(googlePhotosClient.getAllItems(), containsInAnyOrder(
                allOf(itemForFile(rootPhoto), itemWithNoAlbum()),
                allOf(itemForFile(preExistingPhoto2), itemInAlbumWithId(equalTo("outer-album1"))),
                allOf(itemForFile(outerAlbumPhoto), itemInAlbumWithId(equalTo("outer-album1"))),
                allOf(itemForFile(innerAlbumPhoto), itemInAlbumWithId(equalTo("outer-album: inner-album")))));
        assertThat(googlePhotosClient.getAllAlbums(), containsInAnyOrder(
                albumWithId("fail-on-me-pre-existing-album"),
                allOf(albumWithId("outer-album1"),
                        albumWithItems(containsInAnyOrder(itemForFile(outerAlbumPhoto), itemForFile(preExistingPhoto2)))),
                allOf(albumWithId("outer-album"), emptyAlbum()),
                allOf(albumWithId("outer-album: inner-album"), albumWithItems(contains(itemForFile(innerAlbumPhoto))))));
        assertThat(getLastFailure(), emptyOptional());
    }

    @Test
    void mergesPreExistingAlbumsSameNameWithPreexistingProtectedItemIgnoresError() throws Exception {
        var preExistingAlbum1 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        var preExistingAlbum2 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        var preExistingPhoto1 = uploadPhoto(preExistingAlbum1, "protected1.jpg");
        var preExistingPhoto2 = uploadPhoto(preExistingAlbum2, "protected2.jpg");

        doExecuteUpload();
        assertThat(googlePhotosClient.getAllItems(), containsInAnyOrder(
                allOf(itemForFile(rootPhoto), itemWithNoAlbum()),
                allOf(itemForFile(outerAlbumPhoto), itemInAlbumWithId(equalTo("outer-album"))),
                allOf(itemForFile(innerAlbumPhoto), itemInAlbumWithId(equalTo("outer-album: inner-album"))),
                allOf(itemForFile(preExistingPhoto1), itemInAlbumWithId(equalTo(preExistingAlbum1.getId()))),
                allOf(itemForFile(preExistingPhoto2), itemInAlbumWithId(equalTo(preExistingAlbum2.getId())))));
        assertThat(googlePhotosClient.getAllAlbums(), containsInAnyOrder(
                albumWithId("fail-on-me-pre-existing-album"),
                allOf(equalTo(preExistingAlbum1), albumWithItems(containsInAnyOrder(itemForFile(outerAlbumPhoto), itemForFile(preExistingPhoto1)))),
                allOf(equalTo(preExistingAlbum2), albumWithItems(contains(itemForFile(preExistingPhoto2)))),
                allOf(albumWithId("outer-album: inner-album"), albumWithItems(contains(itemForFile(innerAlbumPhoto))))));
        assertThat(getLastFailure(), emptyOptional());
    }

    @Test
    void testPermanentUploadFailureResultsInGlobalError() throws Exception {
        var failedPhoto = root.resolve("failOnMe.jpg");
        Files.write(failedPhoto, new byte[]{0});

        doExecuteUpload();

        assertThat(getLastFailure(), optionalWithValue());
    }

    @Test
    void testPermanentAlbumCreationFailureStopsUpload() throws Exception {
        var failOnMeAlbumDir = root.resolve("failOnMe");
        Files.createDirectories(failOnMeAlbumDir);
        var photo = failOnMeAlbumDir.resolve("photo-new.jpg");
        Files.write(photo, new byte[]{0});

        doExecuteUpload();

        assertThat(googlePhotosClient.getAllItems(), is(empty()));
        assertThat(googlePhotosClient.getAllItems(), is(empty()));
        assertThat(getLastFailure(), optionalWithValue());
    }

    @Test
    void noResumeReUploadsExistingFile() throws Exception {
        doUploadTest();

        doUploadTest("-no-resume");
        googlePhotosClient.getAllItems().forEach(uploadedGoogleMediaItem -> assertThat(uploadedGoogleMediaItem.getUploadCount(), is(2)));
        assertThat(getLastFailure(), emptyOptional());
    }

    @Test
    void doesNotReUploadDataIfPreviouslyUploadedButMediaCreationFailed() throws Exception {
        var invalidMediaItemPath = root.resolve("failOnMeWithInvalidArgumentDuringCreationOfMediaItem.jpg").toAbsolutePath();
        Files.write(invalidMediaItemPath, new byte[]{0});

        doExecuteUpload();

        googlePhotosClient.disableFileNameBaseFailures();

        doExecuteUpload();

        assertThat(googlePhotosClient.getAllItems(), hasItem(allOf(itemForFile(invalidMediaItemPath), itemWithNoAlbum())));

        var varStoreData = readVarStoreDirectly();
        Map<String, ItemState> uploadedMediaItemIdByAbsolutePath = varStoreData.photosUploader().uploadedMediaItemIdByAbsolutePath();

        var invalidItemPathString = invalidMediaItemPath.toAbsolutePath().toString();
        var invalidItemState = uploadedMediaItemIdByAbsolutePath.get(invalidItemPathString);
        assertThat(invalidItemState, itemStateHavingMediaId(optionalWithValue(equalTo(invalidItemPathString))));
        assertThat(getLastFailure(), emptyOptional());
    }

    @Test
    void uploadingEmptyDirectoryDoesNotFail() throws Exception {
        Files.createDirectory(root.resolve("empty-dir"));

        doExecuteUpload();

        assertThat(getLastFailure(), emptyOptional());
    }

    @Test
    void expiredUploadTokenCausesReUploadOnlyForFilesThatWereNotSuccessfullyUploaded() throws Exception {
        var invalidMediaItemPath = root.resolve("failOnMeWithInvalidArgumentDuringCreationOfMediaItem.jpg").toAbsolutePath();
        Files.write(invalidMediaItemPath, new byte[]{0});

        doExecuteUpload();

        googlePhotosClient.disableFileNameBaseFailures();
        TestTimeModule.advanceTimeBy(Duration.ofDays(2));

        doExecuteUpload();
        var mediaItem = googlePhotosClient.getAllItems().stream()
                .filter(item -> item.getBinary().getFile().equals(invalidMediaItemPath))
                .findFirst()
                .get();

        assertThat(mediaItem, allOf(
                itemForFile(invalidMediaItemPath),
                itemWithNoAlbum(),
                itemWithDescription(optionalWithValue(equalTo(invalidMediaItemPath.getFileName().toString())))));
        googlePhotosClient.getAllItems().forEach(item -> assertThat(item.getUploadCount(), is(1)));
        assertThat(getLastFailure(), emptyOptional());
    }

    @Test
    void albumPermissionErrorOnMediaItemCreationSkipsAlbumAndIsIgnored() throws Exception {
        var preExistingAlbumPath = root.resolve("fail-on-me-pre-existing-album");
        Files.createDirectory(preExistingAlbumPath);
        var photoInPreExistingAlbumPath = preExistingAlbumPath.resolve("photoInPreExistingAlbum.jpg");
        Files.write(photoInPreExistingAlbumPath, new byte[]{0});

        doExecuteUpload();

        assertThat(googlePhotosClient.getAllItems(), not(hasItem(itemForFile(photoInPreExistingAlbumPath))));
        assertThat(getLastFailure(), emptyOptional());
    }

    private VarStoreData readVarStoreDirectly() throws IOException {
        return Json.parse(Files.readString(varStoreDir.resolve("data.json")), VarStoreData.class);
    }

    private void doVerifyJpegFilesInVarStore(VarStoreData varStoreData) {
        Map<String, ItemState> uploadedMediaItemIdByAbsolutePath = varStoreData.photosUploader().uploadedMediaItemIdByAbsolutePath();
        var innerPhotoPath = innerAlbumPhoto.toAbsolutePath().toString();
        var outerPhotoPath = outerAlbumPhoto.toAbsolutePath().toString();
        var rootPhotoPath = rootPhoto.toAbsolutePath().toString();

        var rootItemState = uploadedMediaItemIdByAbsolutePath.get(rootPhotoPath);
        assertThat(rootItemState, itemStateHavingMediaId(optionalWithValue(equalTo(rootPhotoPath))));
        assertThat(rootItemState, itemStateHavingUploadState(optionalWithValue(allOf(
                uploadMediaItemStateHavingToken(startsWith(rootPhotoPath)),
                uploadMediaItemStateHavingInstant(equalTo(EPOCH))))));

        var innerItemState = uploadedMediaItemIdByAbsolutePath.get(innerPhotoPath);
        assertThat(innerItemState, itemStateHavingMediaId(optionalWithValue(equalTo(innerPhotoPath))));
        assertThat(innerItemState, itemStateHavingUploadState(optionalWithValue(allOf(
                uploadMediaItemStateHavingToken(startsWith(innerPhotoPath)),
                uploadMediaItemStateHavingInstant(equalTo(EPOCH))))));

        var outerItemState = uploadedMediaItemIdByAbsolutePath.get(outerPhotoPath);
        assertThat(outerItemState, itemStateHavingMediaId(optionalWithValue(equalTo(outerPhotoPath))));
        assertThat(outerItemState, itemStateHavingUploadState(optionalWithValue(allOf(
                uploadMediaItemStateHavingToken(startsWith(outerPhotoPath)),
                uploadMediaItemStateHavingInstant(equalTo(EPOCH))))));
    }

    private void doUploadTest(String... additionalCommandLineOptions) throws InterruptedException, IOException {
        doExecuteUpload(additionalCommandLineOptions);

        doVerifyGoogleClientState();

        var varStoreData = readVarStoreDirectly();
        assertThat(varStoreData.photosUploader().uploadedMediaItemIdByAbsolutePath().values(), hasSize(3));
        doVerifyJpegFilesInVarStore(varStoreData);
    }

    private void doVerifyGoogleClientState() {
        doVerifyGoogleClientItemState();
        doVerifyGoogleClientAlbumState();
    }

    private void doVerifyGoogleClientAlbumState() {
        assertThat(googlePhotosClient.getAllAlbums(), containsInAnyOrder(
                albumWithId("fail-on-me-pre-existing-album"),
                albumWithId("outer-album"),
                albumWithId("outer-album: inner-album")));
    }

    private void doVerifyGoogleClientItemState() {
        assertThat(googlePhotosClient.getAllItems(), containsInAnyOrder(
                allOf(
                        itemForFile(rootPhoto),
                        itemWithNoAlbum(),
                        itemWithDescription(optionalWithValue(equalTo(rootPhoto.getFileName().toString())))),
                allOf(
                        itemForFile(outerAlbumPhoto),
                        itemInAlbumWithId(equalTo("outer-album")),
                        itemWithDescription(optionalWithValue(equalTo(outerAlbumPhoto.getFileName().toString())))),
                allOf(
                        itemForFile(innerAlbumPhoto),
                        itemInAlbumWithId(equalTo("outer-album: inner-album")),
                        itemWithDescription(optionalWithValue(equalTo(innerAlbumPhoto.getFileName().toString()))))));
    }

    private void doExecuteUpload(String... additionalCommandLineOptions) throws InterruptedException {
        CommandLineParser parser = new DefaultParser();
        var commandLine = getAsUnchecked(() -> parser.parse(OPTIONS, ImmutableList.<String>builder()
                .add("-r", root.toString())
                .add(additionalCommandLineOptions)
                .build()
                .toArray(String[]::new)));
        var applicationExitedLatch = new CountDownLatch(1);
        new Thread(() -> {
            Application.builder()
                    .addModule(TestTimeModule::new)
                    .addModule(ExecutorModule::new)
                    .addModule(() -> new VarStoreModule(varStoreAppName))
                    .addModule(() -> new MockGooglePhotosModule(googlePhotosClient))
                    .addModule(ResourceBundleModule::new)
                    .addModule(() -> new UploadPhotosModule(1))
                    .addModule(() -> new IntegrationTestUploadStarterModule(commandLine))
                    .build()
                    .run();
            applicationExitedLatch.countDown();
        }, "application main").start();
        applicationExitedLatch.await(5, TimeUnit.SECONDS);
    }

    private Path uploadPhoto(GooglePhotosAlbum album, String fileName) throws Exception {
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

    private static void removeDir(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        asUnchecked(() -> Files.walkFileTree(dir, new SimpleFileVisitor<>() {
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
        return new FeatureMatcher<>(albumIdMatcher, "item in album", "item in album") {
            @Override
            protected String featureValueOf(UploadedGoogleMediaItem actual) {
                return getOnlyElement(actual.getAlbumIds());
            }
        };
    }

    @SuppressWarnings("TypeParameterExtendsFinalClass")
    private static Matcher<UploadedGoogleMediaItem> itemWithDescription(Matcher<Optional<? extends String>> descriptionMatcher) {
        return new FeatureMatcher<>(descriptionMatcher, "item description", "item description") {
            @Override
            protected Optional<String> featureValueOf(UploadedGoogleMediaItem actual) {
                return actual.getDescription();
            }
        };
    }

    private static Matcher<CreatedGooglePhotosAlbum> albumWithId(String albumId) {
        return albumWithId(equalTo(albumId));
    }

    private static Matcher<CreatedGooglePhotosAlbum> albumWithId(Matcher<String> albumIdMatcher) {
        return new FeatureMatcher<>(albumIdMatcher, "album with name", "album with name") {
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
        return new CustomTypeSafeMatcher<>("empty album") {
            @Override
            protected boolean matchesSafely(GooglePhotosAlbum item) {
                return item.getMediaItemCount() == 0;
            }
        };
    }

    private static Matcher<UploadedGoogleMediaItem> itemWithNoAlbum() {
        return new CustomTypeSafeMatcher<>("item with no album") {
            @Override
            protected boolean matchesSafely(UploadedGoogleMediaItem item) {
                return item.getAlbumIds().isEmpty();
            }
        };
    }

    private static Matcher<? super GoogleMediaItem> itemForFile(Matcher<Path> fileMatcher) {
        FeatureMatcher<String, Path> absolutePathMatcher = new FeatureMatcher<>(fileMatcher, "absolute path", "absolute path") {
            @Override
            protected Path featureValueOf(String actual) {
                return Paths.get(actual);
            }
        };
        return new FeatureMatcher<>(absolutePathMatcher, "item for file path", "item for file path") {
            @Override
            protected String featureValueOf(GoogleMediaItem actual) {
                return actual.getId();
            }
        };
    }

    private static Matcher<? super GoogleMediaItem> itemForFile(Path filePath) {
        return itemForFile(equalTo(filePath));
    }

    @SuppressWarnings("TypeParameterExtendsFinalClass")
    private static Matcher<ItemState> itemStateHavingMediaId(Matcher<Optional<? extends String>> mediaIdMatcher) {
        return new FeatureMatcher<>(mediaIdMatcher, "media item id", "media item id") {
            @Override
            protected Optional<String> featureValueOf(ItemState actual) {
                return actual.mediaId();
            }
        };
    }

    @SuppressWarnings("TypeParameterExtendsFinalClass")
    private static Matcher<ItemState> itemStateHavingUploadState(Matcher<Optional<? extends UploadMediaItemState>> mediaItemStateMatcher) {
        return new FeatureMatcher<>(mediaItemStateMatcher, "upload media item state", "upload media item state") {
            @Override
            protected Optional<UploadMediaItemState> featureValueOf(ItemState actual) {
                return actual.uploadState();
            }
        };
    }

    private static Matcher<UploadMediaItemState> uploadMediaItemStateHavingToken(Matcher<String> tokenMatcher) {
        return new FeatureMatcher<>(tokenMatcher, "upload media item state token", "upload media item state token") {
            @Override
            protected String featureValueOf(UploadMediaItemState actual) {
                return actual.token();
            }
        };
    }

    private static Matcher<UploadMediaItemState> uploadMediaItemStateHavingInstant(Matcher<Instant> instantMatcher) {
        return new FeatureMatcher<>(instantMatcher, "upload media item state instant", "upload media item state instant") {
            @Override
            protected Instant featureValueOf(UploadMediaItemState actual) {
                return actual.uploadInstant();
            }
        };
    }

    @Immutable
    @PackagePrivateImmutablesStyle
    @JsonDeserialize
    interface BaseVarStoreData {
        UploadState photosUploader();
    }
}
