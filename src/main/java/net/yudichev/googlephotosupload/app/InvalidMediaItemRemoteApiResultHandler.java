package net.yudichev.googlephotosupload.app;

import net.jiotty.connector.google.photos.MediaItemCreationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Throwables.getCausalChain;
import static com.google.rpc.Code.INVALID_ARGUMENT_VALUE;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

final class InvalidMediaItemRemoteApiResultHandler implements RemoteApiResultHandler {
    private static final Logger logger = LoggerFactory.getLogger(InvalidMediaItemRemoteApiResultHandler.class);

    @Override
    public boolean handle(String operationName, Throwable exception) {
        return getCausalChain(exception).stream()
                .filter(e -> e instanceof MediaItemCreationFailedException)
                .map(e -> (MediaItemCreationFailedException) e)
                .filter(e -> e.getStatus().getCode() == INVALID_ARGUMENT_VALUE)
                .findFirst()
                .map(e -> {
                    logger.info("Invalid/unsupported media item while performing '{}'", operationName);
                    return TRUE;
                })
                .orElse(FALSE);
    }

    @Override
    public void reset() {
    }
}
