package net.yudichev.googlephotosupload.app;

import com.google.common.collect.ImmutableList;
import net.jiotty.common.app.ApplicationLifecycleControl;
import net.jiotty.common.inject.BaseLifecycleComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.jiotty.common.lang.CompletableFutures.logErrorOnFailure;
import static net.jiotty.common.lang.CompletableFutures.toFutureOfList;
import static net.jiotty.common.lang.MoreThrowables.asUnchecked;

final class UploadPhotos extends BaseLifecycleComponent {
    private static final Logger logger = LoggerFactory.getLogger(UploadPhotos.class);
    private final DirectoryTreeWalker directoryTreeWalker;
    private final GooglePhotosUploader uploader;
    private final ApplicationLifecycleControl applicationLifecycleControl;

    @Inject
    UploadPhotos(DirectoryTreeWalker directoryTreeWalker,
                 GooglePhotosUploader uploader,
                 ApplicationLifecycleControl applicationLifecycleControl) {
        this.directoryTreeWalker = checkNotNull(directoryTreeWalker);
        this.uploader = checkNotNull(uploader);
        this.applicationLifecycleControl = checkNotNull(applicationLifecycleControl);
    }

    @Override
    protected void doStart() {
        asUnchecked(() -> {
            ImmutableList.Builder<CompletableFuture<Void>> resultFutureBuilder = ImmutableList.builder();

            directoryTreeWalker.walk(file -> {
                logger.info("Scheduling upload of {}", file);
                resultFutureBuilder.add(uploader.uploadFile(file));
            });

            ImmutableList<CompletableFuture<Void>> futureList = resultFutureBuilder.build();
            logger.info("Started uploading {} file(s)", futureList.size());
            futureList.stream()
                    .collect(toFutureOfList())
                    .thenAccept(list -> logger.info("All done without errors, files uploaded: {}", list.size()))
                    .whenComplete(logErrorOnFailure(logger, "Failed to upload some file(s)"))
                    .whenComplete((ignored1, ignored2) -> applicationLifecycleControl.initiateShutdown());
        });
    }

}
