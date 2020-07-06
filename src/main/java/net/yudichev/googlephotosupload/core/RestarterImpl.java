package net.yudichev.googlephotosupload.core;

import com.google.inject.BindingAnnotation;
import net.yudichev.jiotty.common.app.ApplicationLifecycleControl;

import javax.inject.Inject;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
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

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface GoogleAuthRootDir {
    }
}
