package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.lang.CompletableFutures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.googlephotosupload.core.Bindings.Backoff;
import static net.yudichev.jiotty.common.lang.CompletableFutures.logErrorOnFailure;

final class CloudOperationHelperImpl implements CloudOperationHelper {
    private static final Logger logger = LoggerFactory.getLogger(CloudOperationHelperImpl.class);
    private final RemoteApiResultHandler backOffHandler;

    @Inject
    CloudOperationHelperImpl(@Backoff RemoteApiResultHandler backOffHandler) {
        this.backOffHandler = checkNotNull(backOffHandler);
    }

    @Override
    public <T> CompletableFuture<T> withBackOffAndRetry(String operationName, Supplier<CompletableFuture<T>> action) {
        return action.get()
                .thenApply(value -> {
                    backOffHandler.reset();
                    return Either.<T, RetryableFailure>left(value);
                })
                .exceptionally(exception -> {
                    boolean shouldRetry = backOffHandler.handle(operationName, exception);
                    return Either.right(RetryableFailure.of(exception, shouldRetry));
                })
                .thenCompose(eitherValueOrRetryableFailure -> eitherValueOrRetryableFailure.map(
                        CompletableFuture::completedFuture,
                        retryableFailure -> {
                            if (retryableFailure.shouldRetry()) {
                                logger.debug("Retrying operation '{}'", operationName);
                                return withBackOffAndRetry(operationName, action);
                            } else {
                                return CompletableFutures.failure(retryableFailure.exception());
                            }
                        }
                ))
                .whenComplete(logErrorOnFailure(logger, "Unhandled exception performing '%s'", operationName));
    }
}
