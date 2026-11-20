package com.swiftdrop.notification.service.push;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.regex.Pattern;

/** Web Push over the JDK HTTP client: RFC 8030 request, RFC 8291 body, RFC 8292 VAPID header. */
public class HttpWebPushClient implements WebPushClient {

    /** Topic header: at most 32 characters of the URL-safe base64 alphabet (RFC 8030 section 5.4). */
    private static final Pattern TOPIC = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    private final VapidKeys keys;
    private final HttpClient http;
    private final Clock clock;

    public HttpWebPushClient(VapidKeys keys) {
        this(keys, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), Clock.systemUTC());
    }

    HttpWebPushClient(VapidKeys keys, HttpClient http, Clock clock) {
        this.keys = keys;
        this.http = http;
        this.clock = clock;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String publicKey() {
        return keys.publicKeyBase64Url();
    }

    @Override
    public int send(String endpoint, byte[] p256dh, byte[] auth, byte[] payload, int ttlSeconds, String urgency, String topic)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/octet-stream")
                .header("Content-Encoding", "aes128gcm")
                .header("TTL", Integer.toString(Math.max(0, ttlSeconds)))
                .header("Urgency", urgency)
                .header("Authorization", keys.authorization(endpoint, clock.instant()))
                .POST(HttpRequest.BodyPublishers.ofByteArray(WebPushEncryption.encrypt(payload, p256dh, auth)));
        if (topic != null && TOPIC.matcher(topic).matches()) {
            request.header("Topic", topic);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
