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

import static com.google.common.base.Preconditions.checkState;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

final class ThrottlingProgressStatus implements ProgressStatus {
    private final ProgressValueUpdater delegate;
    private final ThrottlingConsumer<Runnable> eventSink;
    private final AtomicInteger successCount = new AtomicInteger();
    private final AtomicInteger failureCount = new AtomicInteger();
    private final SchedulingExecutor executor;

    private volatile boolean closed;

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
    public void updateSuccess(int newValue) {
        ensureNotClosed();
        successCount.set(newValue);
        eventSink.accept(() -> delegate.updateSuccess(successCount.get()));
    }

    @Override
    public void incrementSuccessBy(int increment) {
        ensureNotClosed();
        successCount.updateAndGet(operand -> operand + increment);
        eventSink.accept(() -> delegate.updateSuccess(successCount.get()));
    }

    @Override
    public void incrementFailureBy(int increment) {
        ensureNotClosed();
        failureCount.updateAndGet(operand -> operand + increment);
        eventSink.accept(() -> delegate.updateFailure(failureCount.get()));
    }

    @Override
    public void close() {
        closed = true;
        delegate.updateSuccess(successCount.get());
        delegate.updateFailure(failureCount.get());
        eventSink.close();
        executor.close();
        delegate.close();
    }

    private void ensureNotClosed() {
        checkState(!closed, "closed");
    }

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface Delegate {
    }
}
