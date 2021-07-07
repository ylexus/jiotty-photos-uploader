package net.yudichev.googlephotosupload.core;

import com.google.common.collect.ImmutableSet;
import net.yudichev.jiotty.connector.google.drive.GoogleDriveClient;
import net.yudichev.jiotty.connector.google.drive.GoogleDrivePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static net.yudichev.jiotty.common.lang.Locks.inLock;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

final class DriveSpaceTrackerImpl implements DriveSpaceTracker {
    private static final Logger logger = LoggerFactory.getLogger(DriveSpaceTrackerImpl.class);

    private static final long CHECK_SPACE_EVERY_BYTES = 10 * 1024 * 1024; // 50 MB
    private static final ImmutableSet<String> FIELDS = ImmutableSet.of("storageQuota/limit", "storageQuota/usage");
    private static final byte[] NO_DATA = new byte[0];
    private final ProgressStatusFactory progressStatusFactory;
    private final GoogleDriveClient googleDriveClient;
    private final PreferencesManager preferencesManager;
    private final ResourceBundle resourceBundle;
    private final CloudOperationHelper cloudOperationHelper;
    private final Lock lock = new ReentrantLock();

    private ProgressStatus driveSpaceStatus;
    private Optional<Long> limit;
    private long usage;
    private long bytesUploaded;
    private long bytesUploadedSinceSpaceCheck;

    @Inject
    DriveSpaceTrackerImpl(ProgressStatusFactory progressStatusFactory,
                          GoogleDriveClient googleDriveClient,
                          PreferencesManager preferencesManager,
                          ResourceBundle resourceBundle,
                          CloudOperationHelper cloudOperationHelper) {
        this.progressStatusFactory = progressStatusFactory;
        this.googleDriveClient = googleDriveClient;
        this.preferencesManager = checkNotNull(preferencesManager);
        this.resourceBundle = checkNotNull(resourceBundle);
        this.cloudOperationHelper = checkNotNull(cloudOperationHelper);
    }

    @Override
    public CompletableFuture<Void> reset() {
        return CompletableFuture.<Void>supplyAsync(() -> {
            inLock(lock, () -> {
                //noinspection AssignmentToNull assigned in the method called next
                driveSpaceStatus = null;
                refreshDriveQuota();
            });
            return null;
        }).whenComplete((ignored, e) -> {
            if (driveSpaceStatus != null && e != null) {
                driveSpaceStatus.close(false);
            }
        });
    }

    @Override
    public boolean validationEnabled() {
        return inLock(lock, () -> preferencesManager.get().failOnDriveSpace().isPresent() && limit.isPresent());
    }

    @Override
    public void beforeUpload() {
        inLock(lock, () -> {
            if (driveSpaceStatus != null) {
                validateUsage();
            }
        });
    }

    @Override
    public void afterUpload(List<PathState> pathStates) {
        inLock(lock, () -> {
            var totalFileSize = pathStates.stream().map(PathState::path).mapToLong(path -> getAsUnchecked(() -> Files.size(path))).sum();
            bytesUploaded += totalFileSize;
            refreshStatusDescription();
            bytesUploadedSinceSpaceCheck += totalFileSize;
            if (bytesUploadedSinceSpaceCheck > CHECK_SPACE_EVERY_BYTES) {
                logger.debug("bytesUploadedSinceSpaceCheck ({}) > CHECK_SPACE_EVERY_BYTES ({})", bytesUploadedSinceSpaceCheck, CHECK_SPACE_EVERY_BYTES);
                bytesUploadedSinceSpaceCheck = 0;
                refreshDriveQuota();
                validateUsage();
            }
        });
    }

    private void refreshStatusDescription() {
        if (driveSpaceStatus != null) {
            driveSpaceStatus.updateDescription(
                    String.format(resourceBundle.getString("driveSpaceUploadedTotal"),
                            limit.map(DriveSpaceTrackerImpl::formatSize).orElse("∞"),
                            formatSize(bytesUploaded)));
        }
    }

    private void refreshDriveQuota() {
        logger.debug("Refreshing drive quota");
        if (driveSpaceStatus == null) {
            driveSpaceStatus = progressStatusFactory.create(resourceBundle.getString("driveSpaceStatusTitle"), Optional.empty());
        }
        cloudOperationHelper.withBackOffAndRetry("Get drive quota",
                // Creating a file in Drive refreshes usage stats
                () -> googleDriveClient.getAppDataFolder(directExecutor()).createFile("file.txt", "text/plain", NO_DATA)
                        .thenCompose(GoogleDrivePath::delete)
                        .thenCompose(ignored -> googleDriveClient.aboutDrive(FIELDS, directExecutor())),
                value -> {})
                .thenAccept(about -> {
                    limit = Optional.ofNullable(about.getStorageQuota().getLimit());
                    var usage = about.getStorageQuota().getUsage();
                    if (usage != null) {
                        limit.map(DriveSpaceTrackerImpl::toMegabytes).ifPresent(newValue -> driveSpaceStatus.updateTotal(newValue.intValue()));
                        this.usage = usage;
                        //noinspection NumericCastThatLosesPrecision
                        driveSpaceStatus.updateSuccess((int) toMegabytes(usage));
                        refreshStatusDescription();
                    }
                })
                .getNow(null); // otherwise any exceptions will be silently swallowed;
    }

    private void validateUsage() {
        preferencesManager.get().failOnDriveSpace().ifPresent(option -> limit.ifPresent(limitBytes -> option.minFreeMegabytes().ifPresentOrElse(
                minFreeMegabytes -> {
                    if (toMegabytes(limitBytes - usage) <= minFreeMegabytes) {
                        throw new IllegalStateException(String.format(resourceBundle.getString("driveSpaceMinFreeSpaceViolated"), minFreeMegabytes));
                    }
                },
                () -> {
                    //noinspection OptionalGetWithoutIsPresent mutually exclusive
                    if (toMegabytes(usage / limitBytes * 100) >= option.maxUsedPercentage().get()) {
                        throw new IllegalStateException(String
                                .format(resourceBundle.getString("driveSpaceMaxUsedPercentageViolated"), option.maxUsedPercentage().get()));
                    }
                }
        )));
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format("%,.2f MB", toMegabytes(bytes));
        } else if (bytes >= 1024) {
            return String.format("%,.2f KB", toKilobytes(bytes));
        } else {
            return String.format("%s B", bytes);
        }
    }

    private static double toMegabytes(long bytes) {
        return toKilobytes(bytes) / 1024;
    }

    private static double toKilobytes(long bytes) {
        return (double) bytes / 1024;
    }
}
