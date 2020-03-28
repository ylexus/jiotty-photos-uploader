package net.yudichev.googlephotosupload.core;

import com.google.rpc.Code;
import net.yudichev.jiotty.connector.google.photos.MediaItemCreationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

import static com.google.common.base.Throwables.getCausalChain;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

final class InvalidMediaItemRemoteApiResultHandler implements RemoteApiResultHandler {
    private static final Logger logger = LoggerFactory.getLogger(InvalidMediaItemRemoteApiResultHandler.class);
    private static final Predicate<Throwable> PREDICATE =
            ((Predicate<Throwable>) e ->
                    e instanceof MediaItemCreationFailedException &&
                            ((MediaItemCreationFailedException) e).getStatus().getCode() == Code.INVALID_ARGUMENT_VALUE)
                    // TODO this is a special case, if ever https://github.com/google/java-photoslibrary/issues/29 is fixed,
                    //  this workaround should be removed
                    .or(e -> e instanceof IllegalArgumentException && e.getMessage().contains("failed to finalize or get the result"));

    @Override
    public boolean handle(String operationName, Throwable exception) {
        return getCausalChain(exception).stream()
                .filter(PREDICATE)
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
