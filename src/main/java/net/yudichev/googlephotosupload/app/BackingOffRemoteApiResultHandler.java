package net.yudichev.googlephotosupload.app;

import com.google.api.client.util.BackOff;
import com.google.api.gax.rpc.*;
import com.google.common.collect.ImmutableSet;
import com.google.inject.BindingAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.getCausalChain;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

final class BackingOffRemoteApiResultHandler implements RemoteApiResultHandler {
    private static final Logger logger = LoggerFactory.getLogger(BackingOffRemoteApiResultHandler.class);
    private final BackOff backOff;
    private Set<Class<? extends Throwable>> EXCEPTION_TYPES_REQUIRING_BACKOFF = ImmutableSet.of(
            ResourceExhaustedException.class,
            UnavailableException.class,
            DeadlineExceededException.class,
            AbortedException.class,
            InternalException.class);

    @Inject
    BackingOffRemoteApiResultHandler(@Dependency BackOff backOff) {
        this.backOff = checkNotNull(backOff);
    }

    @Override
    public boolean handle(String operationName, Throwable exception) {
        return getCausalChain(exception).stream()
                .filter(e -> EXCEPTION_TYPES_REQUIRING_BACKOFF.contains(e.getClass()))
                .findFirst()
                .map(throwable -> {
                    long backOffMs = getAsUnchecked(backOff::nextBackOffMillis);
                    logger.debug("Retryable exception performing operation '{}', backing off by waiting for {}ms ({})", operationName, backOffMs, throwable);
                    asUnchecked(() -> Thread.sleep(backOffMs));
                    return TRUE;
                })
                .orElse(FALSE);
    }

    @Override
    public void reset() {
        asUnchecked(backOff::reset);
    }

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface Dependency {
    }
}
