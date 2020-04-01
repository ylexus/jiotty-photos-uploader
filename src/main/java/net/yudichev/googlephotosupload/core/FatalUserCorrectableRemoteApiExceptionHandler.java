package net.yudichev.googlephotosupload.core;

interface FatalUserCorrectableRemoteApiExceptionHandler {
    boolean handle(String operationName, Throwable exception);
}
