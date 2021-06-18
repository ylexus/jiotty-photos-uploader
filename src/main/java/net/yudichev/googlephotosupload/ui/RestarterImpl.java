package net.yudichev.googlephotosupload.ui;

import net.yudichev.googlephotosupload.core.Uploader;
import net.yudichev.jiotty.common.app.ApplicationLifecycleControl;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static net.yudichev.googlephotosupload.core.Bindings.GoogleAuthRootDir;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;

final class RestarterImpl implements Restarter {
    private final Path googleAuthRootDir;
    private final ApplicationLifecycleControl applicationLifecycleControl;
    private final Uploader uploader;

    @Inject
    RestarterImpl(@GoogleAuthRootDir Path googleAuthRootDir,
                  ApplicationLifecycleControl applicationLifecycleControl,
                  Uploader uploader) {
        this.googleAuthRootDir = checkNotNull(googleAuthRootDir);
        this.applicationLifecycleControl = checkNotNull(applicationLifecycleControl);
        this.uploader = checkNotNull(uploader);
    }

    @Override
    public void initiateLogoutAndRestart() {
        if (Files.exists(googleAuthRootDir)) {
            asUnchecked(() -> deleteRecursively(googleAuthRootDir, ALLOW_INSECURE));
            uploader.forgetUploadState();
        }
        applicationLifecycleControl.initiateRestart();
    }

    @Override
    public void initiateRestart() {
        applicationLifecycleControl.initiateRestart();
    }
}
