package net.yudichev.googlephotosupload.core;

import com.google.api.client.util.BackOff;
import com.google.api.client.util.ExponentialBackOff;
import com.google.inject.BindingAnnotation;

import javax.inject.Inject;
import javax.inject.Provider;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

final class BackOffProvider implements Provider<BackOff> {
    private final int initialDelayMs;

    @Inject
    BackOffProvider(@InitialDelayMs int initialDelayMs) {
        this.initialDelayMs = initialDelayMs;
    }

    @Override
    public BackOff get() {
        return new SynchronizedBackOff(new ExponentialBackOff.Builder()
                .setInitialIntervalMillis(initialDelayMs)
                .setMaxIntervalMillis(60000)
                .setMaxElapsedTimeMillis(Integer.MAX_VALUE)
                .build());
    }

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface InitialDelayMs {
    }
}
