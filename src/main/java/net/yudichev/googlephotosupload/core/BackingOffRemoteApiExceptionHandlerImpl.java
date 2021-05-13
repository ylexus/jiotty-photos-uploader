package net.yudichev.googlephotosupload.core;

import com.google.api.client.util.BackOff;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.gax.rpc.*;
import com.google.common.collect.ImmutableMap;
import com.google.inject.BindingAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Throwables.getCausalChain;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

final class BackingOffRemoteApiExceptionHandlerImpl implements BackingOffRemoteApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(BackingOffRemoteApiExceptionHandlerImpl.class);
    // Unfortunately, the "retryable" flag in most, if not all, all these exceptions is not reliable; some of these
    // are marked as not retryable while in reality they are
    private final Map<Class<? extends Throwable>, BackOff> backoffByRetryableExceptionType;
    private final BackOff normalBackoff;
    private final BackOff resourceExhaustedBackoff;

    @Inject
    BackingOffRemoteApiExceptionHandlerImpl(@NormalBackoffInitialDelay Duration normalBackoffInitialDelay,
                                            @ResourceExhaustedBackoffInitialDelay Duration resourceExhaustedBackoffInitialDelay) {
        normalBackoff = createBackOff(normalBackoffInitialDelay);
        resourceExhaustedBackoff = createBackOff(resourceExhaustedBackoffInitialDelay);
        backoffByRetryableExceptionType = ImmutableMap.of(
                ResourceExhaustedException.class, resourceExhaustedBackoff,
                UnavailableException.class, normalBackoff,
                DeadlineExceededException.class, normalBackoff,
                AbortedException.class, normalBackoff,
                InternalException.class, normalBackoff);
    }

    private static BackOff createBackOff(Duration initialDelay) {
        //noinspection NumericCastThatLosesPrecision thanks to ExponentialBackOff API using int
        return new SynchronizedBackOff(new ExponentialBackOff.Builder()
                .setInitialIntervalMillis((int) initialDelay.toMillis())
                .setMaxIntervalMillis(60000)
                .setMaxElapsedTimeMillis(Integer.MAX_VALUE)
                .build());
    }

    @Override
    public Optional<Long> handle(String operationName, Throwable exception) {
        for (var e : getCausalChain(exception)) {
            var backOff = backoffByRetryableExceptionType.get(e.getClass());
            if (backOff != null) {
                long backOffMs = getAsUnchecked(backOff::nextBackOffMillis);
                logger.debug("Retryable exception performing operation '{}', backing off by waiting for {}ms", operationName, backOffMs, e);
                asUnchecked(() -> Thread.sleep(backOffMs));
                return Optional.of(backOffMs);
            }
        }
        return Optional.empty();
    }

    @Override
    public void reset() {
        asUnchecked(normalBackoff::reset);
        asUnchecked(resourceExhaustedBackoff::reset);
    }

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface NormalBackoffInitialDelay {
    }

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface ResourceExhaustedBackoffInitialDelay {
    }
}
