package net.yudichev.googlephotosupload.core;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;

import java.util.ResourceBundle;

import static com.google.api.client.http.HttpStatusCodes.STATUS_CODE_FORBIDDEN;
import static com.google.common.base.Throwables.getCausalChain;
import static net.yudichev.jiotty.common.lang.HumanReadableExceptionMessage.humanReadableMessage;

public final class HumanReadableExceptionMessage {
    private HumanReadableExceptionMessage() {
    }

    public static String toHumanReadableMessage(ResourceBundle resourceBundle, Throwable exception) {
        return getCausalChain(exception).stream()
                .filter(throwable -> throwable instanceof GoogleJsonResponseException)
                .findFirst()
                .map(throwable -> (GoogleJsonResponseException) throwable)
                .map(jsonResponseException -> {
                    // better error for GoogleJsonResponseException, otherwise there's too much technical details.
                    var details = jsonResponseException.getDetails();
                    if (details != null && details.getMessage() != null) {
                        if (details.getCode() == STATUS_CODE_FORBIDDEN) {
                            return details.getMessage() + ' ' + resourceBundle.getString("uploadPanePermissionErrorSuffix");
                        } else {
                            return details.getMessage();
                        }
                    }
                    return null;
                })
                .orElseGet(() -> humanReadableMessage(exception));
    }
}
