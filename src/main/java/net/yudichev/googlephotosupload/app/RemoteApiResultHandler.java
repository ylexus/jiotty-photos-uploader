package net.yudichev.googlephotosupload.app;

import javax.annotation.Nullable;

interface RemoteApiResultHandler {
    boolean handle(String operationName, @Nullable Throwable exception);

    void reset();
}
