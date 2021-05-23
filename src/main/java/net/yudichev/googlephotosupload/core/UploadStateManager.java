package net.yudichev.googlephotosupload.core;

import java.nio.file.Path;
import java.util.Map;

public interface UploadStateManager {
    Map<String, ItemState> loadUploadedMediaItemIdByAbsolutePath();

    void forgetState();

    void saveItemState(Path path, ItemState itemState);

    int itemCount();

    void startWebServer();
}
