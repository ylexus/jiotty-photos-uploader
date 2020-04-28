package net.yudichev.googlephotosupload.core;

import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.ApiException;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class InvalidMediaItemFatalUserCorrectableRemoteApiExceptionHandlerTest {
    private FatalUserCorrectableRemoteApiExceptionHandlerImpl resultHandler;

    @BeforeEach
    void setUp() {
        resultHandler = new FatalUserCorrectableRemoteApiExceptionHandlerImpl();
    }

    @Test
    void failedToGetResult() {
        var invalidMediaItem = resultHandler.handle("operationName", new ApiException(
                new IllegalArgumentException("The upload was completed but failed to finalize or get the result"),
                GrpcStatusCode.of(Status.Code.INVALID_ARGUMENT),
                true));
        assertThat(invalidMediaItem, is(true));
    }
}