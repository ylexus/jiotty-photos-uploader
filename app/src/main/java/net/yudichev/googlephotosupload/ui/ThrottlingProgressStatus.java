package net.yudichev.googlephotosupload.ui;

import com.google.inject.BindingAnnotation;
import com.google.inject.assistedinject.Assisted;
import net.yudichev.googlephotosupload.core.KeyedError;
import net.yudichev.googlephotosupload.core.ProgressStatus;
import net.yudichev.jiotty.common.async.ExecutorFactory;
import net.yudichev.jiotty.common.async.SchedulingExecutor;
import net.yudichev.jiotty.common.lang.Closeable;
import net.yudichev.jiotty.common.lang.throttling.ThrottlingConsumer;

import javax.inject.Inject;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.stream.Collectors.toMap;

final class ThrottlingProgressStatus implements ProgressStatus {
    private final ProgressValueUpdater delegate;
    private final Map<Event, ThrottlingConsumer<Runnable>> eventSinksByEvent;
    private final AtomicInteger successCount = new AtomicInteger();
    private final AtomicInteger totalCount = new AtomicInteger();
    private final AtomicReference<String> description = new AtomicReference<>();
    private final BlockingQueue<KeyedError> pendingErrors = new ArrayBlockingQueue<>(65536);
    private final SchedulingExecutor executor;

    private volatile boolean closed;

    @Inject
    ThrottlingProgressStatus(@Delegate ProgressValueUpdaterFactory delegateFactory,
                             ExecutorFactory executorFactory,
                             @Assisted String name,
                             @Assisted Optional<Integer> totalCount) {
        delegate = delegateFactory.create(name, totalCount);
        executor = executorFactory.createSingleThreadedSchedulingExecutor("progress-status");
        eventSinksByEvent = new EnumMap<>(Stream.of(Event.values()).collect(toMap(
                Function.identity(), event -> new ThrottlingConsumer<>(executor, Duration.ofMillis(200), Runnable::run))));
    }

    @Override
    public void updateSuccess(int newValue) {
        ensureNotClosed();
        successCount.set(newValue);
        eventSinksByEvent.get(Event.UPDATE_SUCCESS).accept(() -> delegate.updateSuccess(successCount.get()));
    }

    @Override
    public void updateTotal(int newValue) {
        ensureNotClosed();
        totalCount.set(newValue);
        eventSinksByEvent.get(Event.UPDATE_TOTAL).accept(() -> delegate.updateTotal(totalCount.get()));
    }

    @Override
    public void updateDescription(String newValue) {
        ensureNotClosed();
        description.set(newValue);
        eventSinksByEvent.get(Event.UPDATE_DESC).accept(() -> delegate.updateDescription(description.get()));
    }

    @Override
    public void incrementSuccessBy(int increment) {
        ensureNotClosed();
        successCount.updateAndGet(operand -> operand + increment);
        eventSinksByEvent.get(Event.UPDATE_SUCCESS).accept(() -> delegate.updateSuccess(successCount.get()));
    }

    @Override
    public void addFailure(KeyedError keyedError) {
        ensureNotClosed();
        pendingErrors.add(keyedError);
        eventSinksByEvent.get(Event.ADD_FAILURES).accept(this::drainPendingFailuresToDelegate);
    }

    @Override
    public void onBackoffDelay(long backoffDelayMs) {
        ensureNotClosed();
        eventSinksByEvent.get(Event.ON_BACKOFF_DELAY).accept(() -> delegate.onBackoffDelay(backoffDelayMs));
    }

    @Override
    public void close(boolean success) {
        closed = true;
        delegate.updateSuccess(successCount.get());
        drainPendingFailuresToDelegate();
        eventSinksByEvent.values().forEach(Closeable::close);
        executor.close();
        delegate.completed(success);
    }

    private void drainPendingFailuresToDelegate() {
        Collection<KeyedError> errors = new ArrayList<>();
        pendingErrors.drainTo(errors);
        if (!errors.isEmpty()) {
            delegate.addFailures(errors);
        }
    }

    private void ensureNotClosed() {
        checkState(!closed, "closed");
    }

    enum Event {
        UPDATE_SUCCESS,
        UPDATE_TOTAL,
        UPDATE_DESC,
        ADD_FAILURES,
        ON_BACKOFF_DELAY,
    }

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface Delegate {
    }
}
