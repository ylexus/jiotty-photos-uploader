package net.yudichev.googlephotosupload.core;

import com.google.common.collect.ImmutableList;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Function;

import static com.google.common.base.Throwables.getCausalChain;
import static net.yudichev.jiotty.common.lang.HumanReadableExceptionMessage.humanReadableMessage;

final class FatalUserCorrectableRemoteApiExceptionHandlerImpl implements FatalUserCorrectableRemoteApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(FatalUserCorrectableRemoteApiExceptionHandlerImpl.class);
    private final List<Function<Throwable, Optional<String>>> exceptionToRetryableErrorMsg;

    @Inject
    FatalUserCorrectableRemoteApiExceptionHandlerImpl(ResourceBundle resourceBundle) {
        exceptionToRetryableErrorMsg = ImmutableList.<Function<Throwable, Optional<String>>>of(
                // TODO this is a special case, if ever https://github.com/google/java-photoslibrary/issues/29 is fixed,
                //  this workaround should be removed
                e -> e instanceof IllegalArgumentException && e.getMessage().contains("failed to finalize or get the result") ?
                        Optional.of(resourceBundle.getString("fatalUserCorrectableRemoteApiException.maybeEmptyFile")) : Optional.empty(),

                // https://github.com/ylexus/jiotty-photos-uploader/issues/14: this covers issues like "No permissions to add this media item to the album"
                e -> e instanceof StatusRuntimeException && ((StatusRuntimeException) e).getStatus().getCode() == Status.Code.INVALID_ARGUMENT ?
                        Optional.of(humanReadableMessage(e)) : Optional.empty());
    }

    @Override
    public Optional<String> handle(String operationName, Throwable exception) {
        return getCausalChain(exception).stream()
                .flatMap(throwable -> exceptionToRetryableErrorMsg.stream()
                        .map(function -> {
                            var result = function.apply(throwable);
                            result.ifPresent(errorMessage ->
                                    logger.debug("Fatal user correctable error while performing '{}': {}", operationName, errorMessage, throwable));
                            return result;
                        })
                        .filter(Optional::isPresent)
                        .map(Optional::get))
                .findFirst();
    }
}
