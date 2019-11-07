package net.yudichev.googlephotosupload.app;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

interface CloudOperationHelper {
    <T> CompletableFuture<T> withBackOffAndRetry(String operationName, Supplier<CompletableFuture<T>> action);
}
