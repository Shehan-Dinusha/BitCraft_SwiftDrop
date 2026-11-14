package com.swiftdrop.webhook.delivery;

import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import com.swiftdrop.webhook.security.BlockedDestinationException;
import com.swiftdrop.webhook.security.BlockingAddressResolverGroup;
import com.swiftdrop.webhook.security.UnsafeWebhookUrlException;
import com.swiftdrop.webhook.security.WebhookUrlPolicy;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.PrematureCloseException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * Sends one signed webhook attempt and returns a structured {@link AttemptOutcome}. It never throws for HTTP, network
 * or policy failures. Not wired to anything yet: the delivery worker (Step 10) calls {@link #send}; test pings keep
 * the legacy dispatcher until Step 11.
 *
 * <p><b>Exact bytes.</b> The body array of the {@link WebhookRequest} is signed by {@link WebhookSigner} and the same
 * array is wrapped (not copied, not re-serialised) as the request body. Content-Length is set from that array, so the
 * body is not chunked or altered. The timestamp is read once per attempt and used for both the signature and
 * {@link WebhookHeaders#TIMESTAMP}.</p>
 *
 * <p><b>Destination safety.</b>
 * (1) {@link WebhookUrlPolicy} re-checks the stored URL (scheme, user-info, names, a DNS check of every address).
 * (2) The HTTP client resolves names only through {@link BlockingAddressResolverGroup}, so the address actually
 *     connected to is checked in the same lookup (connect-time pinning; DNS rebinding cannot swap it).
 * (3) Redirects are never followed: a 3xx is a permanent {@code INVALID_RESPONSE} and its Location is ignored.
 * (4) No proxy.
 * A refusal at either layer is {@code BLOCKED_DESTINATION}, and nothing is sent.</p>
 *
 * <p><b>Limits.</b> Connect timeout 2 s, response timeout 5 s (request sent to response headers), overall cap 10 s.
 * Response headers are limited to 16 KB. The response body is never read: only the status and the
 * {@code Retry-After} header are taken, then the connection is dropped. Each attempt uses a new connection, so no
 * pooled connection to an earlier address is reused.</p>
 *
 * <p><b>Logging.</b> Only ids, attempt, status, error class and latency. Never the URL, secret, signature, body,
 * response or exception text.</p>
 */
@Slf4j
@Component
public class WebhookHttpSender {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);
    static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RESPONSE_HEADER_BYTES = 16 * 1024;
    private static final int MAX_RETRY_AFTER_LENGTH = 64;

    private final WebhookUrlPolicy urlPolicy;
    private final WebhookSigner signer;
    private final HttpClient httpClient;

    @Autowired
    public WebhookHttpSender(WebhookUrlPolicy urlPolicy, WebhookSigner signer) {
        this(urlPolicy, signer, new BlockingAddressResolverGroup(urlPolicy));
    }

    /** The resolver group is a parameter so the connect-time layer can be exercised on its own (manual checks). */
    public WebhookHttpSender(WebhookUrlPolicy urlPolicy, WebhookSigner signer, BlockingAddressResolverGroup resolver) {
        this.urlPolicy = urlPolicy;
        this.signer = signer;
        this.httpClient = HttpClient.newConnection()
                .resolver(resolver)
                .followRedirect(false)
                .noProxy()
                .compress(false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
                .responseTimeout(RESPONSE_TIMEOUT)
                .httpResponseDecoder(spec -> spec.maxHeaderSize(MAX_RESPONSE_HEADER_BYTES));
    }

    public AttemptOutcome send(WebhookRequest request) {
        long started = System.nanoTime();

        Optional<UnsafeWebhookUrlException.Reason> refused = urlPolicy.check(request.targetUrl());
        if (refused.isPresent()) {
            return finish(request, started, null, AttemptErrorClass.BLOCKED_DESTINATION, null, refused.get().name());
        }

        byte[] body = request.bodyForSending();
        long timestamp = signer.currentTimestampSeconds();
        String signature = signer.sign(request.secret(), timestamp, body);

        try {
            ResponseHead head = httpClient
                    .headers(headers -> headers
                            .set(HttpHeaderNames.CONTENT_TYPE, "application/json")
                            .set(HttpHeaderNames.CONTENT_LENGTH, body.length)
                            .set(WebhookHeaders.SIGNATURE, signature)
                            .set(WebhookHeaders.TIMESTAMP, Long.toString(timestamp))
                            .set(WebhookHeaders.EVENT_ID, request.eventId().toString())
                            .set(WebhookHeaders.DELIVERY_ID, request.deliveryId().toString())
                            .set(WebhookHeaders.EVENT, request.eventType())
                            .set(WebhookHeaders.ATTEMPT, Integer.toString(request.attempt()))
                            .set(WebhookHeaders.USER_AGENT, WebhookHeaders.USER_AGENT_VALUE))
                    .post()
                    .uri(request.targetUrl())
                    .send(Mono.fromSupplier(() -> Unpooled.wrappedBuffer(body)))
                    .response()
                    .map(response -> new ResponseHead(response.status().code(),
                            response.responseHeaders().get(HttpHeaderNames.RETRY_AFTER)))
                    .timeout(TOTAL_TIMEOUT)
                    .block();
            if (head == null) {
                return finish(request, started, null, AttemptErrorClass.UNKNOWN, null, null);
            }
            int status = head.status();
            String retryAfter = (status == 429 || status == 503) ? boundedRetryAfter(head.retryAfter()) : null;
            return finish(request, started, status, classifyStatus(status), retryAfter, null);
        } catch (RuntimeException failure) {
            return finish(request, started, null, classifyException(failure), null, null);
        }
    }

    private AttemptOutcome finish(WebhookRequest request, long started, Integer status, AttemptErrorClass errorClass,
                                  String retryAfter, String policyReason) {
        int latencyMs = (int) Math.min(Integer.MAX_VALUE, Duration.ofNanos(System.nanoTime() - started).toMillis());
        AttemptOutcome outcome = new AttemptOutcome(status, errorClass, retryable(status, errorClass), latencyMs, retryAfter);
        if (outcome.success()) {
            log.info("Webhook delivery {} (event {}, attempt {}) succeeded: status={} latencyMs={}",
                    request.deliveryId(), request.eventId(), request.attempt(), status, latencyMs);
        } else if (policyReason != null) {
            log.warn("Webhook delivery {} (event {}, attempt {}) refused before sending: errorClass={} reason={}",
                    request.deliveryId(), request.eventId(), request.attempt(), errorClass, policyReason);
        } else {
            log.warn("Webhook delivery {} (event {}, attempt {}) failed: errorClass={} status={} retryable={} latencyMs={}",
                    request.deliveryId(), request.eventId(), request.attempt(), errorClass, status, outcome.retryable(),
                    latencyMs);
        }
        return outcome;
    }

    /** Decision D3. Null means success. */
    static AttemptErrorClass classifyStatus(int status) {
        if (status >= 200 && status < 300) return null;
        if (status == 429) return AttemptErrorClass.HTTP_RATE_LIMITED;
        if (status >= 400 && status < 500) return AttemptErrorClass.HTTP_CLIENT_ERROR;
        if (status >= 500 && status < 600) return AttemptErrorClass.HTTP_SERVER_ERROR;
        return AttemptErrorClass.INVALID_RESPONSE; // 3xx (redirects are not followed) and anything non-standard
    }

    /** Decision D3: which failures the retry policy may retry. */
    static boolean retryable(Integer status, AttemptErrorClass errorClass) {
        if (errorClass == null) return false;
        return switch (errorClass) {
            case TIMEOUT, CONNECTION_FAILURE, HTTP_RATE_LIMITED, HTTP_SERVER_ERROR, UNKNOWN -> true;
            case HTTP_CLIENT_ERROR -> status != null && (status == 408 || status == 425);
            case INVALID_RESPONSE, BLOCKED_DESTINATION -> false;
        };
    }

    /** By exception type only; messages (which contain hosts and URLs) are never read. */
    static AttemptErrorClass classifyException(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof BlockedDestinationException) {
                return AttemptErrorClass.BLOCKED_DESTINATION;
            }
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof ReadTimeoutException
                    || cause instanceof io.netty.channel.ConnectTimeoutException
                    || cause instanceof SocketTimeoutException) {
                return AttemptErrorClass.TIMEOUT;
            }
            if (cause instanceof ConnectException || cause instanceof UnknownHostException
                    || cause instanceof NoRouteToHostException || cause instanceof SSLException
                    || cause instanceof PrematureCloseException || cause instanceof ClosedChannelException) {
                return AttemptErrorClass.CONNECTION_FAILURE;
            }
        }
        return AttemptErrorClass.UNKNOWN;
    }

    private static String boundedRetryAfter(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() > MAX_RETRY_AFTER_LENGTH ? trimmed.substring(0, MAX_RETRY_AFTER_LENGTH) : trimmed;
    }

    private record ResponseHead(int status, String retryAfter) {
    }
}
