package net.yudichev.googlephotosupload.core;

interface InvalidMediaItemRemoteApiExceptionHandler {
    boolean handle(String operationName, Throwable exception);
}
