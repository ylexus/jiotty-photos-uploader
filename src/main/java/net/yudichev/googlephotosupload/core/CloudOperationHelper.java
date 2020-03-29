package net.yudichev.googlephotosupload.core;

import java.util.concurrent.CompletableFuture;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

interface CloudOperationHelper {
    <T> CompletableFuture<T> withBackOffAndRetry(String operationName, Supplier<CompletableFuture<T>> action, LongConsumer backoffEventConsumer);
}
