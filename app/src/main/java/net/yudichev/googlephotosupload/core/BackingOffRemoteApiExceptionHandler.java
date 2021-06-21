package net.yudichev.googlephotosupload.core;

import java.util.Optional;

interface BackingOffRemoteApiExceptionHandler {
    /**
     * @return backoff delay applied, in milliseconds, or 0, if no backoff delay was applied
     */
    Optional<Long> handle(String operationName, Throwable exception);

    void reset();
}
