package com.swiftdrop.analytics.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

/** Bounded HTTP requests without redirects or automatic retries. Never logs response bodies. */
public class JsonHttpClient {
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final Duration timeout;

    public JsonHttpClient(ObjectMapper mapper, Duration timeout) {
        this.mapper = mapper;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public JsonNode request(String method, String url, Map<String, String> headers, Object body) {
        try {
            var builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).header("Accept", "application/json");
            headers.forEach(builder::header);
            if (body == null) builder.GET();
            else builder.header("Content-Type", "application/json").method(method,
                    HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new Failure(response.statusCode());
            if (response.body().length() > 131072) throw new Failure(502);
            return mapper.readTree(response.body());
        } catch (Failure ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new Failure(503);
        } catch (Exception ex) {
            throw new Failure(503);
        }
    }

    public static final class Failure extends RuntimeException {
        private final int status;
        public Failure(int status) { super("Upstream request failed"); this.status = status; }
        public int status() { return status; }
    }
}
