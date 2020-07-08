package net.yudichev.googlephotosupload.core;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import net.yudichev.googlephotosupload.core.RecordingGooglePhotosClient.Album;
import net.yudichev.googlephotosupload.core.RecordingGooglePhotosClient.MediaItem;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URL;
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.time.Instant.EPOCH;
import static java.time.Instant.now;
import static java.util.stream.Collectors.toList;
import static net.yudichev.googlephotosupload.cli.CliOptions.OPTIONS;
import static net.yudichev.googlephotosupload.core.AddToAlbumMethod.AFTER_CREATING_ITEMS_SORTED;
import static net.yudichev.googlephotosupload.core.GooglePhotosUploaderImpl.GOOGLE_PHOTOS_API_BATCH_SIZE;
import static net.yudichev.googlephotosupload.core.IntegrationTestUploadStarter.getLastFailure;
import static net.yudichev.googlephotosupload.core.OptionalMatchers.emptyOptional;
import static net.yudichev.googlephotosupload.core.OptionalMatchers.optionalWithValue;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@SuppressWarnings("ClassWithTooManyMethods")
@ExtendWith(MockitoExtension.class)
final class IntegrationTest {
    private static final SecureRandom RANDOM = new SecureRandom();
    @SuppressWarnings("StaticVariableMayNotBeInitialized")
    private static int dataGenerator;
    private String varStoreAppName;
    private Path root;
    private Path rootPhoto;
    private Path outerAlbumPhoto;
    private Path innerAlbumPhoto;
    private Path varStoreDir;
    private RecordingGooglePhotosClient googlePhotosClient;
    private RecordingProgressStatusFactory progressStatusFactory;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createTempDirectory(getClass().getSimpleName());

        // TODO make test var store write to same temp dir not to pollute home directories
        varStoreAppName = IntegrationTest.class.getSimpleName() + RANDOM.nextInt();
        varStoreDir = Paths.get(System.getProperty("user.home"), "." + varStoreAppName);
        googlePhotosClient = new RecordingGooglePhotosClient();
        progressStatusFactory = new RecordingProgressStatusFactory();

        TestTimeModule.resetTime();
    }

    @AfterEach
    void tearDown() {
        removeDir(varStoreDir);
        removeDir(root);
    }

    @Test
    void testUploadsCorrectly() throws Exception {
        createStandardTestFiles();

        doUploadTest();

        getLastFailure().ifPresent(Assertions::fail);
    }

    @Test
    void skipsUploadIfSavedStateShowsAlreadyUploaded() throws Exception {
        createStandardTestFiles();
        var varStore = Guice.createInjector(new VarStoreModule(varStoreAppName)).getInstance(VarStore.class);
        var outerAlbumPhotoAbsolutePath = outerAlbumPhoto.toAbsolutePath().toString();
        varStore.saveValue("photosUploader", UploadState.builder()
                .putUploadedMediaItemIdByAbsolutePath(outerAlbumPhotoAbsolutePath,
                        ItemState.builder()
                                .setMediaId(outerAlbumPhotoAbsolutePath)
                                .setUploadState(UploadMediaItemState.of(outerAlbumPhotoAbsolutePath, now()))
                                .build())
                .build());

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertNoRecordedProgressErrors();
        assertThat(googlePhotosClient.getAllItems(), not(hasItem(itemForFile(outerAlbumPhoto))));
    }

    @Test
    void testHandlesResourceExhaustedExceptionsCorrectly() throws Exception {
        createStandardTestFiles();
        googlePhotosClient.enableResourceExhaustedExceptions();

        doUploadTest();

        getLastFailure().ifPresent(Assertions::fail);
        assertNoRecordedProgressErrors();
    }

    @Test
    void ignoresExcludedFile() throws Exception {
        var invalidPhoto = root.resolve("excluded-file.txt");
        writeMediaFile(invalidPhoto);

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertNoRecordedProgressErrors();
    }

    @Test
    void handlesInvalidArgumentDuringCreationOfMediaItem() throws Exception {
        createStandardTestFiles();
        var invalidMediaItemPath = root.resolve("failOnMeWithInvalidArgumentDuringCreationOfMediaItem.jpg").toAbsolutePath();
        writeMediaFile(invalidMediaItemPath);

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertThat(progressStatusFactory.getRecordedErrorsByProgressName(), hasEntry(
                equalTo("Uploading media files"),
                contains(KeyedError.of(invalidMediaItemPath.toAbsolutePath(),
                        "INVALID_ARGUMENT: createMediaItems"))));
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
    }

    @Test
    void handlesInvalidArgumentDuringCreationOfMediaData() throws Exception {
        createStandardTestFiles();
        var invalidMediaItemPath = root.resolve("failOnMeWithInvalidArgumentDuringUploadIngMediaData.jpg").toAbsolutePath();
        writeMediaFile(invalidMediaItemPath);

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertThat(progressStatusFactory.getRecordedErrorsByProgressName(), hasEntry(
                equalTo("Uploading media files"),
                contains(KeyedError.of(invalidMediaItemPath.toAbsolutePath(),
                        "INVALID_ARGUMENT: uploadMediaData"))));
        doVerifyGoogleClientState();

        var varStoreData = readVarStoreDirectly();
        Map<String, ItemState> uploadedMediaItemIdByAbsolutePath = varStoreData.photosUploader().uploadedMediaItemIdByAbsolutePath();
        assertThat(uploadedMediaItemIdByAbsolutePath.values(), hasSize(3));

        doVerifyJpegFilesInVarStore(varStoreData);
    }

    @Test
    void reusesPreExistingAlbum() throws Exception {
        createStandardTestFiles();
        googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        doExecuteUpload();
        getLastFailure().ifPresent(Assertions::fail);
        assertNoRecordedProgressErrors();
        doVerifyGoogleClientState();
    }

    @Test
    void mergesPreExistingEmptyAlbumsWithSameNameAndReusesResultingAlbum() throws Exception {
        createStandardTestFiles();
        googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertThat(progressStatusFactory.getRecordedErrorsByProgressName(), hasEntry(
                equalTo("Reconciling 2 album(s) with Google Photos"),
                contains(KeyedError.of(new URL("http://photos.com/outer-album1"),
                        "Album 'outer-album' may now be empty and will require manual deletion (Google Photos API does not allow me to delete it for you)"))));
        doVerifyGoogleClientItemState();
        assertThat(googlePhotosClient.getAllAlbums(), containsInAnyOrder(
                albumWithId("outer-album"),
                albumWithId("outer-album1"),
                albumWithId("outer-album: inner-album")));
    }

    @Test
    void doesNotMergePreExistingNonWritableAlbum() throws Exception {
        createStandardTestFiles();
        var writableOuterAlbum = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        var nonWritableOuterAlbum = googlePhotosClient.createNonWritableAlbum("outer-album");

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertNoRecordedProgressErrors();
        doVerifyGoogleClientItemState();
        assertThat(googlePhotosClient.getAllAlbums(), containsInAnyOrder(
                albumWithId(writableOuterAlbum.getId()),
                albumWithId(nonWritableOuterAlbum.getId()),
                albumWithId("outer-album: inner-album")));
    }

    @Test
    void mergesPreExistingNonEmptyAlbumsWithSameNameAndReusesResultingAlbum() throws Exception {
        createStandardTestFiles();

        var preExistingAlbum1 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        var preExistingAlbum2 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        var preExistingAlbum3 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        var preExistingPhoto1Item = uploadPhoto(preExistingAlbum1, "photo1.jpg");
        var preExistingPhoto2Item = uploadPhoto(preExistingAlbum2, "photo2.jpg");

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertThat(progressStatusFactory.getRecordedErrorsByProgressName().keySet(), hasSize(1));
        assertThat(progressStatusFactory.getRecordedErrorsByProgressName(), hasEntry(
                equalTo("Reconciling 2 album(s) with Google Photos"),
                containsInAnyOrder(
                        KeyedError.of(new URL("http://photos.com/outer-album1"),
                                "Album 'outer-album' may now be empty and will require manual deletion " +
                                        "(Google Photos API does not allow me to delete it for you)"),
                        KeyedError.of(new URL("http://photos.com/outer-album2"),
                                "Album 'outer-album' may now be empty and will require manual deletion " +
                                        "(Google Photos API does not allow me to delete it for you)"))));

        assertThat(googlePhotosClient.getAllItems(), containsInAnyOrder(
                allOf(itemForFile(rootPhoto), itemWithNoAlbum()),
                equalTo(preExistingPhoto1Item),
                equalTo(preExistingPhoto2Item),
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
    void mergesPreExistingNonEmptyAlbumsWithSamePhotoInThem() throws Exception {
        createStandardTestFiles();
        var preExistingAlbum1 = googlePhotosClient.createAlbum("outer-album").get(3, TimeUnit.SECONDS);
        var preExistingAlbum2 = googlePhotosClient.createAlbum("outer-album").get(3, TimeUnit.SECONDS);

        var preExistingPhoto1ItemItem = uploadPhoto(preExistingAlbum1, "photo1.jpg");
        preExistingAlbum2.addMediaItemsByIds(ImmutableList.of(preExistingPhoto1ItemItem.getId())).get(3, TimeUnit.SECONDS);

        Files.delete(outerAlbumPhoto);

        doExecuteUpload();
        getLastFailure().ifPresent(Assertions::fail);
        assertThat(progressStatusFactory.getRecordedErrorsByProgressName().keySet(), hasSize(1));
        assertThat(progressStatusFactory.getRecordedErrorsByProgressName(), hasEntry(
                equalTo("Reconciling 2 album(s) with Google Photos"),
                contains(KeyedError.of(new URL("http://photos.com/outer-album1"),
                        "Album 'outer-album' may now be empty and will require manual deletion " +
                                "(Google Photos API does not allow me to delete it for you)"))));

        assertThat(preExistingAlbum1, is(albumWithItems(contains(preExistingPhoto1ItemItem))));
        assertThat(preExistingAlbum2, is(emptyAlbum()));
    }

    @Test
    void mergesAlbumsWithMoreThanMaxItemsAllowedPerRequest() throws Exception {
        createStandardTestFiles();
        var preExistingAlbum1 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        var preExistingAlbum2 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        uploadPhoto(preExistingAlbum1, "photo-in-album1.jpg");
        var items = IntStream.range(0, 51)
                .mapToObj(i -> getAsUnchecked(() -> uploadPhoto(preExistingAlbum2, "photo" + i + ".jpg")))
                .collect(toList());

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertThat(progressStatusFactory.getRecordedErrorsByProgressName(), hasEntry(
                equalTo("Reconciling 2 album(s) with Google Photos"),
                contains(KeyedError.of(new URL("http://photos.com/outer-album1"),
                        "Album 'outer-album' may now be empty and will require manual deletion (Google Photos API does not allow me to delete it for you)"))));

        var outerAlbumItems = preExistingAlbum1.getMediaItems().get(3, TimeUnit.SECONDS);
        assertThat(outerAlbumItems, hasSize(53));
        items.forEach(item -> assertThat(outerAlbumItems, hasItem(item)));
        assertThat(outerAlbumItems, hasItem(itemForFile(outerAlbumPhoto)));

        assertThat(preExistingAlbum2, is(emptyAlbum()));
    }

    @Test
    void mergesAlbumsWithExactlyMaxItemsAllowedPerRequest() throws Exception {
        createStandardTestFiles();
        var preExistingAlbum1 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        var preExistingAlbum2 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        uploadPhoto(preExistingAlbum1, "photo-in-album1.jpg");
        var items = IntStream.range(0, 49)
                .mapToObj(i -> getAsUnchecked(() -> uploadPhoto(preExistingAlbum2, "photo" + i + ".jpg")))
                .collect(toList());

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertThat(progressStatusFactory.getRecordedErrorsByProgressName(), hasEntry(
                equalTo("Reconciling 2 album(s) with Google Photos"),
                contains(KeyedError.of(new URL("http://photos.com/outer-album1"),
                        "Album 'outer-album' may now be empty and will require manual deletion (Google Photos API does not allow me to delete it for you)"))));

        var outerAlbumItems = preExistingAlbum1.getMediaItems().get(3, TimeUnit.SECONDS);
        assertThat(outerAlbumItems, hasSize(51));
        items.forEach(item -> assertThat(outerAlbumItems, hasItem(item)));
        assertThat(outerAlbumItems, hasItem(itemForFile(outerAlbumPhoto)));

        assertThat(preExistingAlbum2, is(emptyAlbum()));
    }

    @Test
    void mergesPreExistingAlbumsWithSameNameSecondOneNonEmptyAndReusesResultingAlbum() throws Exception {
        createStandardTestFiles();
        googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        var preExistingAlbum2 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        var preExistingPhoto2Item = uploadPhoto(preExistingAlbum2, "photo2.jpg");

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertNoRecordedProgressErrors();

        assertThat(googlePhotosClient.getAllItems(), containsInAnyOrder(
                allOf(itemForFile(rootPhoto), itemWithNoAlbum()),
                equalTo(preExistingPhoto2Item),
                allOf(itemForFile(outerAlbumPhoto), itemInAlbumWithId(equalTo("outer-album1"))),
                allOf(itemForFile(innerAlbumPhoto), itemInAlbumWithId(equalTo("outer-album: inner-album")))));
        assertThat(googlePhotosClient.getAllAlbums(), containsInAnyOrder(
                allOf(albumWithId("outer-album1"),
                        albumWithItems(containsInAnyOrder(itemForFile(outerAlbumPhoto), equalTo(preExistingPhoto2Item)))),
                allOf(albumWithId("outer-album"), emptyAlbum()),
                allOf(albumWithId("outer-album: inner-album"), albumWithItems(contains(itemForFile(innerAlbumPhoto))))));
    }

    @Test
    void mergesPreExistingAlbumsSameNameWithPreexistingItems() throws Exception {
        createStandardTestFiles();
        var preExistingAlbum1 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);
        var preExistingAlbum2 = googlePhotosClient.createAlbum("outer-album").get(1, TimeUnit.SECONDS);

        var preExistingPhoto1Item = uploadPhoto(preExistingAlbum1, "pre-existing-photo1.jpg");
        var preExistingPhoto2Item = uploadPhoto(preExistingAlbum2, "pre-existing-photo2.jpg");

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertThat(progressStatusFactory.getRecordedErrorsByProgressName().keySet(), hasSize(1));
        assertThat(progressStatusFactory.getRecordedErrorsByProgressName(), hasEntry(
                equalTo("Reconciling 2 album(s) with Google Photos"),
                contains(KeyedError.of(new URL("http://photos.com/outer-album1"),
                        "Album 'outer-album' may now be empty and will require manual deletion (Google Photos API does not allow me to delete it for you)"))));
        assertThat(googlePhotosClient.getAllItems(), containsInAnyOrder(
                allOf(itemForFile(rootPhoto), itemWithNoAlbum()),
                allOf(itemForFile(outerAlbumPhoto), itemInAlbumWithId(equalTo("outer-album"))),
                allOf(itemForFile(innerAlbumPhoto), itemInAlbumWithId(equalTo("outer-album: inner-album"))),
                equalTo(preExistingPhoto1Item),
                equalTo(preExistingPhoto2Item)));
        assertThat(googlePhotosClient.getAllAlbums(), containsInAnyOrder(
                allOf(equalTo(preExistingAlbum1), albumWithItems(containsInAnyOrder(
                        itemForFile(outerAlbumPhoto),
                        equalTo(preExistingPhoto1Item),
                        equalTo(preExistingPhoto2Item)))),
                allOf(equalTo(preExistingAlbum2), emptyAlbum()),
                allOf(albumWithId("outer-album: inner-album"), albumWithItems(contains(itemForFile(innerAlbumPhoto))))));
    }

    @Test
    void testPermanentUploadFailureResultsInGlobalError() throws Exception {
        var failedPhoto = root.resolve("failOnMe.jpg");
        writeMediaFile(failedPhoto);

        doExecuteUpload();

        assertThat(getLastFailure(), optionalWithValue());
        assertNoRecordedProgressErrors();
    }

    @Test
    void testPermanentAlbumCreationFailureStopsUpload() throws Exception {
        var failOnMeAlbumDir = root.resolve("failOnMe");
        Files.createDirectories(failOnMeAlbumDir);
        var photo = failOnMeAlbumDir.resolve("photo-new.jpg");
        writeMediaFile(photo);

        doExecuteUpload();

        assertNoRecordedProgressErrors();

        assertThat(googlePhotosClient.getAllItems(), is(empty()));
        assertThat(googlePhotosClient.getAllItems(), is(empty()));
        assertThat(getLastFailure(), optionalWithValue());
    }

    @Test
    void noResumeReUploadsExistingFile() throws Exception {
        createStandardTestFiles();
        doUploadTest();
        getLastFailure().ifPresent(Assertions::fail);

        doUploadTest("-no-resume");

        getLastFailure().ifPresent(Assertions::fail);
        assertNoRecordedProgressErrors();

        googlePhotosClient.getAllItems().forEach(mediaItem -> assertThat(mediaItem.getUploadCount(), is(2)));
    }

    @Test
    void forgettingUploadStateReUploadsExistingFile() throws Exception {
        IntegrationTestUploadStarter.forgetUploadStateOnShutdown();
        createStandardTestFiles();
        doExecuteUpload();
        getLastFailure().ifPresent(Assertions::fail);

        doUploadTest();

        getLastFailure().ifPresent(Assertions::fail);
        assertNoRecordedProgressErrors();

        googlePhotosClient.getAllItems().forEach(mediaItem -> assertThat(mediaItem.getUploadCount(), is(2)));
    }

    @Test
    void doesNotReUploadDataIfPreviouslyUploadedButMediaCreationFailed() throws Exception {
        var invalidMediaItemPath = root.resolve("failOnMeWithInvalidArgumentDuringCreationOfMediaItem.jpg").toAbsolutePath();
        var invalidMediaItemContents = writeMediaFile(invalidMediaItemPath);

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertThat(progressStatusFactory.getRecordedErrorsByProgressName().keySet(), hasSize(1));
        assertThat(progressStatusFactory.getRecordedErrorsByProgressName(), hasEntry(
                equalTo("Uploading media files"),
                contains(KeyedError.of(invalidMediaItemPath.toAbsolutePath(),
                        "INVALID_ARGUMENT: createMediaItems"))));

        progressStatusFactory.reset();
        googlePhotosClient.disableFileNameBaseFailures();

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertNoRecordedProgressErrors();

        googlePhotosClient.disableFileNameBaseFailures();

        assertThat(googlePhotosClient.getAllItems(), hasItem(allOf(itemForFile(invalidMediaItemPath), itemWithNoAlbum())));

        var varStoreData = readVarStoreDirectly();
        Map<String, ItemState> uploadedMediaItemIdByAbsolutePath = varStoreData.photosUploader().uploadedMediaItemIdByAbsolutePath();

        var invalidItemPathString = invalidMediaItemPath.toAbsolutePath().toString();
        var invalidItemState = uploadedMediaItemIdByAbsolutePath.get(invalidItemPathString);
        assertThat(invalidItemState, itemStateHavingMediaId(optionalWithValue(equalTo(invalidMediaItemContents.toMediaItemId()))));
    }

    @Test
    void uploadingEmptyDirectoryDoesNotFail() throws Exception {
        Files.createDirectory(root.resolve("empty-dir"));

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertNoRecordedProgressErrors();
    }

    @Test
    void expiredUploadTokenCausesReUploadOnlyForFilesThatWereNotSuccessfullyUploaded() throws Exception {
        var invalidMediaItemPath = root.resolve("failOnMeWithInvalidArgumentDuringCreationOfMediaItem.jpg").toAbsolutePath();
        var contents = MediaItemContents.of(EPOCH, uniqueData());
        writeMediaFile(invalidMediaItemPath, contents);

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);

        googlePhotosClient.disableFileNameBaseFailures();
        TestTimeModule.advanceTimeBy(Duration.ofDays(2));

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);

        var mediaItem = googlePhotosClient.getAllItems().stream()
                .filter(item -> item.getBinary().getContents().equals(contents))
                .findFirst();

        assertThat(mediaItem, optionalWithValue(allOf(
                itemForFile(invalidMediaItemPath),
                itemWithNoAlbum(),
                itemWithDescription(optionalWithValue(equalTo(invalidMediaItemPath.getFileName().toString()))))));
        googlePhotosClient.getAllItems().forEach(item -> assertThat(item.getUploadCount(), is(1)));
    }

    @Test
    void albumPermissionErrorUploadsItemButDoesNotAddToAlbum() throws Exception {
        googlePhotosClient.createNonWritableAlbum("pre-existing-not-writable-album");
        var preExistingAlbumPath = root.resolve("pre-existing-not-writable-album");
        Files.createDirectory(preExistingAlbumPath);
        var photoInPreExistingAlbumPath = preExistingAlbumPath.resolve("photoInPreExistingAlbum.jpg");
        writeMediaFile(photoInPreExistingAlbumPath);

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertThat(progressStatusFactory.getRecordedErrorsByProgressName().keySet(), hasSize(1));
        assertThat(progressStatusFactory.getRecordedErrorsByProgressName(), hasEntry(
                equalTo("Uploading media files"),
                contains(KeyedError.of(photoInPreExistingAlbumPath.toAbsolutePath(),
                        "INVALID_ARGUMENT: No permission to add media items to this album"))));

        assertThat(googlePhotosClient.getAllItems(), hasItem(
                allOf(itemForFile(photoInPreExistingAlbumPath), itemWithNoAlbum())
        ));
    }

    @Test
    void doesNotCreateAlbumsForDirectoriesWithOnlySkippableFiles() throws Exception {
        var skippableDir = root.resolve("skippable-dir");
        Files.createDirectory(skippableDir);
        writeMediaFile(skippableDir.resolve(".hiddenfile"));
        var skippableSubDir = skippableDir.resolve("skippable-sub-dir");
        Files.createDirectory(skippableSubDir);
        writeMediaFile(skippableSubDir.resolve(".hiddenfile2"));
        var skippableSubDir2 = skippableDir.resolve("skippable-sub-dir2-empty");
        Files.createDirectory(skippableSubDir2);

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertNoRecordedProgressErrors();

        assertThat(googlePhotosClient.getAllAlbums(), allOf(
                not(hasItem(albumWithId("skippable-dir"))),
                not(hasItem(albumWithId("skippable-sub-dir"))),
                not(hasItem(albumWithId("skippable-sub-dir2-empty")))));
    }

    @Test
    void worksForMoreThanBatchSizeItemsInDirectory() throws Exception {
        var largeDirPath = root.resolve("dirWithManyFiles").toAbsolutePath();
        Files.createDirectory(largeDirPath);
        var filesPaths = IntStream.range(0, GOOGLE_PHOTOS_API_BATCH_SIZE + 5)
                .mapToObj(i -> largeDirPath.resolve("file" + i + ".jpg"))
                .peek(path -> asUnchecked(() -> writeMediaFile(path)))
                .collect(toImmutableList());

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertNoRecordedProgressErrors();

        filesPaths.forEach(path -> assertThat(googlePhotosClient.getAllItems(), hasItem(itemForFile(path))));
    }

    @Test
    void inSortedModeAddsItemsToAlbumInTheOrderOfTheirCreationTime() throws Exception {
        IntegrationTestUploadStarterModule.setPreferences(Preferences.builder().setAddToAlbumStrategy(AFTER_CREATING_ITEMS_SORTED).build());

        var albumWithSortedFilesPath = root.resolve("albumWithSortedFiles").toAbsolutePath();
        Files.createDirectory(albumWithSortedFilesPath);
        var file3 = albumWithSortedFilesPath.resolve("file3.jpg");
        var file1 = albumWithSortedFilesPath.resolve("file1.jpg");
        var file2 = albumWithSortedFilesPath.resolve("file2.jpg");

        writeMediaFile(file3, MediaItemContents.of(Instant.ofEpochMilli(3), uniqueData()));
        writeMediaFile(file1, MediaItemContents.of(Instant.ofEpochMilli(1), uniqueData()));
        writeMediaFile(file2, MediaItemContents.of(Instant.ofEpochMilli(2), uniqueData()));

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertNoRecordedProgressErrors();

        var album = (Album) googlePhotosClient.getAllAlbums().stream()
                .filter(createdGooglePhotosAlbum -> "albumWithSortedFiles".equals(createdGooglePhotosAlbum.getTitle()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Could not find album 'albumWithSortedFiles'"));
        assertThat(album.getItems(), contains(
                itemForFile(file1),
                itemForFile(file2),
                itemForFile(file3)));
    }

    @Test
    void inRegularModeSortsByFilename() throws Exception {
        var albumWithSortedFilesPath = root.resolve("albumWithSortedFiles").toAbsolutePath();
        Files.createDirectory(albumWithSortedFilesPath);
        var file3 = albumWithSortedFilesPath.resolve("file3.jpg");
        var file1 = albumWithSortedFilesPath.resolve("file1.jpg");
        var file2 = albumWithSortedFilesPath.resolve("file2.jpg");

        writeMediaFile(file3);
        writeMediaFile(file2);
        writeMediaFile(file1);

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        assertNoRecordedProgressErrors();

        var album = getOnlyElement(googlePhotosClient.getAllAlbums());
        assertThat(album.getItems(), contains(
                itemForFile(file1),
                itemForFile(file2),
                itemForFile(file3)));
    }

    @Test
    void twoFilesInSameDirectoryWithIdenticalContent() throws Exception {
        var albumPath = root.resolve("album");
        Files.createDirectory(albumPath);
        var file1Path = albumPath.resolve("file1.jpg");
        var file2Path = albumPath.resolve("file2.jpg");
        var contents = MediaItemContents.of(EPOCH, uniqueData());
        writeMediaFile(file1Path, contents);
        writeMediaFile(file2Path, contents);

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);
        var recordedErrorsByProgressName = progressStatusFactory.getRecordedErrorsByProgressName();
        assertThat(recordedErrorsByProgressName.keySet(), hasSize(1));
        assertThat(recordedErrorsByProgressName, hasKey("Uploading media files"));
        var keyedErrors = getOnlyElement(recordedErrorsByProgressName.values());
        assertThat(keyedErrors, hasSize(1));
        var keyedError = getOnlyElement(keyedErrors);
        var errorKey = keyedError.getKey();
        assertThat((Path) errorKey, is(either(equalTo(file2Path)).or(equalTo(file1Path))));
        var primaryFile = errorKey.equals(file1Path) ? file2Path : file1Path;
        assertThat(keyedError.getError(), is("ALREADY_EXISTS: Failed: There was an error while trying to create this media item."));

        var album = (Album) getOnlyElement(googlePhotosClient.getAllAlbums());
        assertThat(album.getItems(), contains(itemForFile(primaryFile)));
    }

    /**
     * One of the issues from https://github.com/ylexus/jiotty-photos-uploader/issues/34
     */
    @Test
    void twoFilesInSameDirectoryWithIdenticalContentOnePreviouslyUploadedButNotAddedToAlbum() throws Exception {
        // need to ensure there's two batches, so that we successfully create two media items with same ID
        var largeDirPath = root.resolve("dirWithManyFiles").toAbsolutePath();
        Files.createDirectory(largeDirPath);
        var mediaItemContents = MediaItemContents.of(EPOCH, uniqueData());
        IntStream.range(0, GOOGLE_PHOTOS_API_BATCH_SIZE + 1)
                .mapToObj(i -> largeDirPath.resolve("file" + i + ".jpg"))
                .forEach(path -> asUnchecked(() -> writeMediaFile(path, mediaItemContents)));

        doExecuteUpload();

        getLastFailure().ifPresent(Assertions::fail);

        var album = (Album) getOnlyElement(googlePhotosClient.getAllAlbums());
        assertThat(album.getItems(), contains(itemWithContents(mediaItemContents)));
    }

    private void createStandardTestFiles() throws IOException {
        rootPhoto = root.resolve("root-photo.jpg");
        writeMediaFile(rootPhoto);

        var outerAlbumDir = root.resolve("outer-album");
        Files.createDirectories(outerAlbumDir);
        outerAlbumPhoto = outerAlbumDir.resolve("outer-album-photo.jpg");
        writeMediaFile(outerAlbumPhoto);
        writeMediaFile(outerAlbumDir.resolve("picasa.ini"));

        Files.createDirectories(root.resolve("DS_Store"));

        var innerAlbumDir = outerAlbumDir.resolve("inner-album");
        Files.createDirectories(innerAlbumDir);
        innerAlbumPhoto = innerAlbumDir.resolve("inner-album-photo.jpg");
        writeMediaFile(innerAlbumPhoto);
    }

    private void assertNoRecordedProgressErrors() {
        progressStatusFactory.getRecordedErrorsByProgressName().values().forEach(keyedErrors -> assertThat(keyedErrors, is(empty())));
    }

    private static MediaItemContents writeMediaFile(Path path) throws IOException {
        var mediaItemContents = MediaItemContents.of(EPOCH, uniqueData());
        writeMediaFile(path, mediaItemContents);
        return mediaItemContents;
    }

    private static void writeMediaFile(Path path, MediaItemContents contents) throws IOException {
        Files.writeString(path, Json.stringify(contents));
    }

    @SuppressWarnings("StaticVariableUsedBeforeInitialization")
    private static int uniqueData() {
        return dataGenerator++;
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
        assertThat(rootItemState, itemStateHavingMediaId(optionalWithValue(equalTo(readContents(rootPhoto).toMediaItemId()))));
        assertThat(rootItemState, itemStateHavingUploadState(optionalWithValue(allOf(
                uploadMediaItemStateHavingToken(startsWith(rootPhotoPath)),
                uploadMediaItemStateHavingInstant(equalTo(EPOCH))))));

        var innerItemState = uploadedMediaItemIdByAbsolutePath.get(innerPhotoPath);
        assertThat(innerItemState, itemStateHavingMediaId(optionalWithValue(equalTo(readContents(innerAlbumPhoto).toMediaItemId()))));
        assertThat(innerItemState, itemStateHavingUploadState(optionalWithValue(allOf(
                uploadMediaItemStateHavingToken(startsWith(innerPhotoPath)),
                uploadMediaItemStateHavingInstant(equalTo(EPOCH))))));

        var outerItemState = uploadedMediaItemIdByAbsolutePath.get(outerPhotoPath);
        assertThat(outerItemState, itemStateHavingMediaId(optionalWithValue(equalTo(readContents(outerAlbumPhoto).toMediaItemId()))));
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
                    .addModule(() -> new IntegrationTestUploadStarterModule(commandLine, progressStatusFactory))
                    .build()
                    .run();
            applicationExitedLatch.countDown();
        }, "application main").start();
        applicationExitedLatch.await(5, TimeUnit.SECONDS);
    }

    private MediaItem uploadPhoto(GooglePhotosAlbum album, String fileName) throws Exception {
        Path path = null;
        try {
            path = root.resolve(fileName);
            writeMediaFile(path);

            return (MediaItem) googlePhotosClient.uploadMediaItem(Optional.of(album.getId()), path).get(1, TimeUnit.SECONDS);
        } finally {
            if (path != null) {
                Files.delete(path);
            }
        }
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

    private static Matcher<RecordingGooglePhotosClient.MediaItem> itemInAlbumWithId(Matcher<String> albumIdMatcher) {
        return new FeatureMatcher<>(albumIdMatcher, "item in album", "item in album") {
            @Override
            protected String featureValueOf(RecordingGooglePhotosClient.MediaItem actual) {
                return getOnlyElement(actual.getAlbumIds());
            }
        };
    }

    @SuppressWarnings("TypeParameterExtendsFinalClass")
    private static Matcher<RecordingGooglePhotosClient.MediaItem> itemWithDescription(Matcher<Optional<? extends String>> descriptionMatcher) {
        return new FeatureMatcher<>(descriptionMatcher, "item description", "item description") {
            @Override
            protected Optional<String> featureValueOf(RecordingGooglePhotosClient.MediaItem actual) {
                return actual.getDescription();
            }
        };
    }

    private static Matcher<Album> albumWithId(String albumId) {
        return albumWithId(equalTo(albumId));
    }

    private static Matcher<Album> albumWithId(Matcher<String> albumIdMatcher) {
        return new FeatureMatcher<>(albumIdMatcher, "album with name", "album with name") {
            @Override
            protected String featureValueOf(Album actual) {
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

    private static Matcher<RecordingGooglePhotosClient.MediaItem> itemWithNoAlbum() {
        return new CustomTypeSafeMatcher<>("item with no album") {
            @Override
            protected boolean matchesSafely(RecordingGooglePhotosClient.MediaItem item) {
                return item.getAlbumIds().isEmpty();
            }
        };
    }

    private static Matcher<? super GoogleMediaItem> itemForFile(Path filePath) {
        var fileContents = readContents(filePath);
        return allOf(itemWithDescription(filePath), itemWithContents(fileContents));
    }

    private static FeatureMatcher<GoogleMediaItem, String> itemWithDescription(Path filePath) {
        return new FeatureMatcher<>(equalTo(filePath.getFileName().toString()), "item with description", "item with description") {
            @Override
            protected String featureValueOf(GoogleMediaItem actual) {
                return ((MediaItem) actual).getDescription().orElse("<no description>");
            }
        };
    }

    private static Matcher<GoogleMediaItem> itemWithContents(MediaItemContents fileContents) {
        return new FeatureMatcher<>(equalTo(fileContents), "item with contents", "item with contents") {
            @Override
            protected MediaItemContents featureValueOf(GoogleMediaItem actual) {
                return ((MediaItem) actual).getBinary().getContents();
            }
        };
    }

    private static MediaItemContents readContents(Path filePath) {
        return getAsUnchecked(() -> Json.parse(Files.readString(filePath), MediaItemContents.class));
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
