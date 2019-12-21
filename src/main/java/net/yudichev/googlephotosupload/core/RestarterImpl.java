package net.yudichev.googlephotosupload.core;

import com.google.inject.BindingAnnotation;
import net.yudichev.jiotty.common.app.ApplicationLifecycleControl;

import javax.inject.Inject;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ForkJoinPool;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;

final class RestarterImpl implements Restarter {
    private final Path googleAuthRootDir;
    private final ApplicationLifecycleControl applicationLifecycleControl;

    @Inject
    RestarterImpl(@GoogleAuthRootDir Path googleAuthRootDir,
                  ApplicationLifecycleControl applicationLifecycleControl) {
        this.googleAuthRootDir = checkNotNull(googleAuthRootDir);
        this.applicationLifecycleControl = checkNotNull(applicationLifecycleControl);
    }

    @Override
    public void initiateLogoutAndRestart() {
        ForkJoinPool.commonPool().execute(() -> {
            if (Files.exists(googleAuthRootDir)) {
                asUnchecked(() -> deleteRecursively(googleAuthRootDir, ALLOW_INSECURE));
            }
            applicationLifecycleControl.initiateRestart();
        });
    }

    @Override
    public void initiateRestart() {
        ForkJoinPool.commonPool().execute(applicationLifecycleControl::initiateRestart);
    }

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface GoogleAuthRootDir {
    }
}
