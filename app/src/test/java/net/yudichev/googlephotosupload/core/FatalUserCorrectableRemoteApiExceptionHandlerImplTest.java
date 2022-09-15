package net.yudichev.googlephotosupload.core;

import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.ApiException;
import io.grpc.Status;
import org.apache.http.client.HttpResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.stream.Stream;

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

    @ParameterizedTest
    @MethodSource
    void failedToGetResult(Throwable exception, String expectedErrorMsg) {
        var invalidMediaItem = resultHandler.handle("operationName", exception);
        assertThat(invalidMediaItem, optionalWithValue(equalTo(expectedErrorMsg)));
    }

    public static Stream<Arguments> failedToGetResult() {
        return Stream.of(
                Arguments.of(new ApiException(new IllegalArgumentException("The file is empty"), GrpcStatusCode.of(Status.Code.INVALID_ARGUMENT), true),
                        "oops"),
                Arguments.of(new HttpResponseException(413, "The upload progress could not be verified. Request Entity Too Large"),
                        "The upload progress could not be verified. Request Entity Too Large")
        );
    }
}