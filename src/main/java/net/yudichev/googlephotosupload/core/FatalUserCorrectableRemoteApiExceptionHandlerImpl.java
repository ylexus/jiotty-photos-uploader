package net.yudichev.googlephotosupload.core;

import com.google.rpc.Code;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.yudichev.jiotty.connector.google.photos.MediaItemCreationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.getCausalChain;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

final class FatalUserCorrectableRemoteApiExceptionHandlerImpl implements FatalUserCorrectableRemoteApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(FatalUserCorrectableRemoteApiExceptionHandlerImpl.class);
    private static final Predicate<Throwable> PREDICATE = checkNotNull(((Predicate<Throwable>) e ->
            e instanceof MediaItemCreationFailedException &&
                    ((MediaItemCreationFailedException) e).getStatus().getCode() == Code.INVALID_ARGUMENT_VALUE)

            // TODO this is a special case, if ever https://github.com/google/java-photoslibrary/issues/29 is fixed,
            //  this workaround should be removed
            .or(e -> e instanceof IllegalArgumentException && e.getMessage().contains("failed to finalize or get the result"))

            // https://github.com/ylexus/jiotty-photos-uploader/issues/14: this covers issues like "No permissions to add this media item to the album"
            .or(e -> e instanceof StatusRuntimeException && ((StatusRuntimeException) e).getStatus().getCode() == Status.Code.INVALID_ARGUMENT));

    @Override
    public boolean handle(String operationName, Throwable exception) {
        return getCausalChain(exception).stream()
                .filter(PREDICATE)
                .findFirst()
                .map(e -> {
                    logger.debug("Fatal user correctable error while performing '{}'", operationName, e);
                    return TRUE;
                })
                .orElse(FALSE);
    }
}
