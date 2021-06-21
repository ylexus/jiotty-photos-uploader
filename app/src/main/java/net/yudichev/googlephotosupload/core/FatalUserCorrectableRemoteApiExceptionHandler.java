package net.yudichev.googlephotosupload.core;

import java.util.Optional;

interface FatalUserCorrectableRemoteApiExceptionHandler {
    Optional<String> handle(String operationName, Throwable exception);
}
