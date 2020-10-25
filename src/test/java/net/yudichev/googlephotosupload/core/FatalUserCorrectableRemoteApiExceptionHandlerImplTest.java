package net.yudichev.googlephotosupload.core;

import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.ApiException;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ResourceBundle;

import static net.yudichev.googlephotosupload.core.OptionalMatchers.optionalWithValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class FatalUserCorrectableRemoteApiExceptionHandlerImplTest {
    private FatalUserCorrectableRemoteApiExceptionHandlerImpl resultHandler;
    @Mock
    private ResourceBundle resourceBundle;

    @BeforeEach
    void setUp() {
        lenient().when(resourceBundle.getString("fatalUserCorrectableRemoteApiException.maybeEmptyFile")).thenReturn("oops");
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