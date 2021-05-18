package net.yudichev.googlephotosupload.core;

import com.google.api.gax.rpc.*;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.inject.BindingAnnotation;
import net.yudichev.jiotty.common.lang.backoff.BackOff;
import net.yudichev.jiotty.common.lang.backoff.ExponentialBackOff;
import net.yudichev.jiotty.common.lang.backoff.SynchronizedBackOff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.Optional;

import static com.google.common.base.Throwables.getCausalChain;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;
import static net.yudichev.jiotty.common.lang.backoff.BackOff.STOP;

final class BackingOffRemoteApiExceptionHandlerImpl implements BackingOffRemoteApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(BackingOffRemoteApiExceptionHandlerImpl.class);
    // Unfortunately, the "retryable" flag in most, if not all, all these exceptions is not reliable; some of these
    // are marked as not retryable while in reality they are
    private final BiMap<Class<? extends Throwable>, BackOff> backoffByRetryableExceptionType;

    @Inject
    BackingOffRemoteApiExceptionHandlerImpl(@GlobalInitialDelayOverride Optional<Duration> globalInitialDelayOverride) {
        backoffByRetryableExceptionType = ImmutableBiMap.of(
                // defaults as per https://developers.google.com/photos/library/guides/best-practices#retrying-failed-requests
                ResourceExhaustedException.class, createBackOff(globalInitialDelayOverride.orElse(Duration.ofSeconds(30)), MAX_VALUE),
                UnavailableException.class, createBackOff(globalInitialDelayOverride.orElse(Duration.ofSeconds(1)), MAX_VALUE),
                DeadlineExceededException.class, createBackOff(globalInitialDelayOverride.orElse(Duration.ofSeconds(1)), MAX_VALUE),
                AbortedException.class, createBackOff(globalInitialDelayOverride.orElse(Duration.ofSeconds(1)), MAX_VALUE),
                // Internal failures are strange; https://github.com/ylexus/jiotty-photos-uploader/issues/98 showed that INTERNAL could be retryable or not.
                // So give a max of 10 seconds, then fail
                InternalException.class, createBackOff(globalInitialDelayOverride.orElse(Duration.ofSeconds(1)), 10_000));
    }

    private static BackOff createBackOff(Duration initialDelay, int maxElapsedTimeMillis) {
        //noinspection NumericCastThatLosesPrecision thanks to ExponentialBackOff API using int
        return new SynchronizedBackOff(new ExponentialBackOff.Builder()
                .setInitialIntervalMillis((int) initialDelay.toMillis())
                .setMaxIntervalMillis(60000)
                .setMaxElapsedTimeMillis(maxElapsedTimeMillis)
                .build());
    }

    @Override
    public Optional<Long> handle(String operationName, Throwable exception) {
        for (var e : getCausalChain(exception)) {
            var backOff = backoffByRetryableExceptionType.get(e.getClass());
            if (backOff != null) {
                long backOffMs = getAsUnchecked(backOff::nextBackOffMillis);
                if (backOffMs == STOP) {
                    return Optional.empty();
                }
                logger.debug("Retryable exception performing operation '{}', backing off by waiting for {}ms", operationName, backOffMs, e);
                asUnchecked(() -> Thread.sleep(backOffMs));
                return Optional.of(backOffMs);
            }
        }
        return Optional.empty();
    }

    @Override
    public void reset() {
        backoffByRetryableExceptionType.values().forEach(backOff -> asUnchecked(backOff::reset));
    }

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface GlobalInitialDelayOverride {
    }
}
