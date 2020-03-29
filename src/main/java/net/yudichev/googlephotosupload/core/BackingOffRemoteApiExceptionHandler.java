package net.yudichev.googlephotosupload.core;

interface BackingOffRemoteApiExceptionHandler {
    /**
     * @return backoff delay applied, in milliseconds, or 0, if no backoff delay was applied
     */
    long handle(String operationName, Throwable exception);

    void reset();
}
