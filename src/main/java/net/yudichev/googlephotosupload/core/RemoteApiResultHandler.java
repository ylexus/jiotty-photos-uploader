package net.yudichev.googlephotosupload.core;

import javax.annotation.Nullable;

interface RemoteApiResultHandler {
    boolean handle(String operationName, @Nullable Throwable exception);

    void reset();
}
