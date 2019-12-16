package net.yudichev.googlephotosupload.ui;

import com.google.inject.BindingAnnotation;
import com.google.inject.assistedinject.Assisted;
import net.yudichev.googlephotosupload.core.ProgressStatus;
import net.yudichev.jiotty.common.async.ExecutorFactory;
import net.yudichev.jiotty.common.async.SchedulingExecutor;
import net.yudichev.jiotty.common.lang.throttling.ThrottlingConsumer;

import javax.inject.Inject;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

final class ThrottlingProgressStatus implements ProgressStatus {
    private final ProgressValueUpdater delegate;
    private final Consumer<Runnable> eventSink;
    private final AtomicInteger value = new AtomicInteger();
    private final SchedulingExecutor executor;

    @Inject
    ThrottlingProgressStatus(@Delegate ProgressValueUpdaterFactory delegateFactory,
                             ExecutorFactory executorFactory,
                             @Assisted String name,
                             @Assisted Optional<Integer> totalCount) {
        this.delegate = delegateFactory.create(name, totalCount);
        executor = executorFactory.createSingleThreadedSchedulingExecutor("progress-status");
        eventSink = new ThrottlingConsumer<>(executor, Duration.ofMillis(200), Runnable::run);
    }

    @Override
    public void update(int newValue) {
        value.set(newValue);
        eventSink.accept(() -> delegate.update(value.get()));
    }

    @Override
    public void incrementBy(int increment) {
        value.updateAndGet(operand -> operand + increment);
        eventSink.accept(() -> delegate.update(value.get()));
    }

    @Override
    public void close() {
        delegate.update(value.get());
        executor.close();
        delegate.close();
    }

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface Delegate {
    }
}
