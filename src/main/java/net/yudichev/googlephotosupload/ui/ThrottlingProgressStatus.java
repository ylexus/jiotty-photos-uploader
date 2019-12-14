package net.yudichev.googlephotosupload.ui;

import com.google.inject.BindingAnnotation;
import com.google.inject.assistedinject.Assisted;
import net.yudichev.googlephotosupload.core.ProgressStatus;
import net.yudichev.googlephotosupload.core.ProgressStatusFactory;
import net.yudichev.jiotty.common.lang.throttling.ThresholdThrottlingConsumerFactory;

import javax.inject.Inject;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

final class ThrottlingProgressStatus implements ProgressStatus {
    private final ProgressStatus delegate;
    private final Consumer<Runnable> eventSink;

    @Inject
    ThrottlingProgressStatus(@Delegate ProgressStatusFactory delegateFactory,
                             @Delegate ThresholdThrottlingConsumerFactory<Runnable> thresholdThrottlingConsumerFactory,
                             @Assisted String name,
                             @Assisted Optional<Integer> totalCount) {
        this.delegate = delegateFactory.create(name, totalCount);
        // TODO duration?
        // TODO this is not good throttling
        eventSink = thresholdThrottlingConsumerFactory.create(0, Duration.ofSeconds(1), Runnable::run);
    }

    @Override
    public void update(int newValue) {
        eventSink.accept(() -> delegate.update(newValue));
    }

    @Override
    public void incrementBy(int increment) {
        eventSink.accept(() -> delegate.incrementBy(increment));
    }

    @Override
    public void close() {
        delegate.close();
    }

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface Delegate {
    }
}
