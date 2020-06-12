package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.varstore.VarStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.System.identityHashCode;
import static net.yudichev.jiotty.common.lang.Locks.inLock;

final class UploadStateManagerImpl implements UploadStateManager {
    private static final Logger logger = LoggerFactory.getLogger(UploadStateManagerImpl.class);

    private static final String VAR_STORE_KEY = "photosUploader";
    private final VarStore varStore;
    private final Lock lock = new ReentrantLock();
    private UploadState uploadState;

    @Inject
    UploadStateManagerImpl(VarStore varStore) {
        this.varStore = checkNotNull(varStore);
        uploadState = varStore.readValue(UploadState.class, VAR_STORE_KEY).orElseGet(() -> UploadState.builder().build());
    }

    @Override
    public UploadState get() {
        return inLock(lock, () -> uploadState);
    }

    @Override
    public void save(UploadState uploadState) {
        inLock(lock, () -> {
            this.uploadState = uploadState;
            varStore.saveValue(VAR_STORE_KEY, uploadState);
            logger.debug("Saved state {} with {} item(s)", identityHashCode(uploadState), uploadState.uploadedMediaItemIdByAbsolutePath().size());
        });
    }
}
