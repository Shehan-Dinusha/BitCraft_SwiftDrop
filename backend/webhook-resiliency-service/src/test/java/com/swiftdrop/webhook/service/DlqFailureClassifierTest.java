package com.swiftdrop.webhook.service;

import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DlqFailureClassifierTest {

    /** Shapes of messages the legacy dispatcher actually stored (exception getMessage()). */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "503 Service Unavailable from POST https://shop-a.internal.example:8443/hooks/abc | HTTP_SERVER_ERROR | Endpoint returned a server error (HTTP 503)",
            "500 Internal Server Error from POST http://10.20.30.40/webhook                   | HTTP_SERVER_ERROR | Endpoint returned a server error (HTTP 500)",
            "404 Not Found from POST https://shop-a.internal.example/hooks/abc                 | HTTP_CLIENT_ERROR | Endpoint rejected the request (HTTP 404)",
            "429 Too Many Requests from POST https://shop-a.internal.example/hooks             | HTTP_RATE_LIMITED | Endpoint rate-limited the request (HTTP 429)",
            "java.util.concurrent.TimeoutException: Did not observe any item or completion within 3000ms in 'source(MonoFlatMap)' | TIMEOUT | Endpoint did not respond before the timeout",
            "Connection refused: no further information: shop-a.internal.example/10.0.0.5:443 | CONNECTION_FAILURE | Could not connect to the endpoint (network, DNS or TLS failure)",
            "Failed to resolve 'shop-a.internal.example' [A(1)] after 2 queries               | CONNECTION_FAILURE | Could not connect to the endpoint (network, DNS or TLS failure)",
            "javax.net.ssl.SSLHandshakeException: PKIX path building failed for shop-a.internal.example | CONNECTION_FAILURE | Could not connect to the endpoint (network, DNS or TLS failure)",
            "Something odd happened at https://shop-a.internal.example/x                       | UNKNOWN           | Delivery failed for an unclassified reason"
    })
    void classifiesWithoutEchoingRawText(String raw, AttemptErrorClass expectedClass, String expectedSummary) {
        DlqFailureClassifier.Classification result = DlqFailureClassifier.classify(raw.trim());

        assertThat(result.errorClass()).isEqualTo(expectedClass);
        assertThat(result.summary()).isEqualTo(expectedSummary)
                .doesNotContain("shop-a", "internal.example", "http", "10.", "/hooks", "'");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingReasonIsUnknown(String raw) {
        assertThat(DlqFailureClassifier.classify(raw).errorClass()).isEqualTo(AttemptErrorClass.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"999 Weird from POST https://h/x", "200 OK from POST https://h/x"})
    void statusOutsideErrorRangeIsNotTrusted(String raw) {
        assertThat(DlqFailureClassifier.classify(raw).errorClass()).isEqualTo(AttemptErrorClass.UNKNOWN);
    }
}
