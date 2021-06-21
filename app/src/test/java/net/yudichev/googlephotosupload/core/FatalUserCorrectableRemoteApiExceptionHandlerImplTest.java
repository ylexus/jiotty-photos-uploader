package net.yudichev.googlephotosupload.core;

import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.ApiException;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import static net.yudichev.googlephotosupload.core.OptionalMatchers.optionalWithValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class FatalUserCorrectableRemoteApiExceptionHandlerImplTest {
    private FatalUserCorrectableRemoteApiExceptionHandlerImpl resultHandler;

    @BeforeEach
    void setUp() throws IOException {
        ResourceBundle resourceBundle;
        try (var resourceAsStream = getClass().getResourceAsStream("/Resources.properties")) {
            resourceBundle = new PropertyResourceBundle(resourceAsStream);
        }
        resultHandler = new FatalUserCorrectableRemoteApiExceptionHandlerImpl(resourceBundle);
    }

    @Test
    void failedToGetResult() {
        var invalidMediaItem = resultHandler.handle("operationName", new ApiException(
                new IllegalArgumentException("The upload was completed but failed to finalize or get the result"),
                GrpcStatusCode.of(Status.Code.INVALID_ARGUMENT),
                true));
        assertThat(invalidMediaItem, optionalWithValue(equalTo("oops")));
    }
}