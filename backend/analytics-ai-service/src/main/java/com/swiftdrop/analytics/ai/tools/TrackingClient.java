package com.swiftdrop.analytics.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.analytics.ai.*;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.*;

@Component
public class TrackingClient {
    private final JsonHttpClient http;
    private final String gateway;
    public TrackingClient(ObjectMapper mapper, @Value("${ai.gateway-url:http://localhost:8080}") String gateway) {
        this.http = new JsonHttpClient(mapper, Duration.ofSeconds(3));
        this.gateway = gateway.replaceAll("/+$", "");
    }

    public Map<String, Object> track(String tracking, ToolRequestContext context) {
        ToolExecutor.validateTracking(tracking);
        // Verify ownership through the order service before consulting public tracking.
        // The protected payload is never passed to the model or stored in an audit.
        try {
            var order = http.request("GET", gateway + "/api/v1/orders/tracking/" + tracking, headers(context), null);
            if (!order.path("success").asBoolean() || !context.userId().toString().equals(order.path("data").path("customerId").asText())) {
                throw new JsonHttpClient.Failure(404);
            }
        } catch (JsonHttpClient.Failure ex) {
            // Avoid disclosing whether another customer's parcel exists.
            if (ex.status() == 403) throw new JsonHttpClient.Failure(404);
            throw ex;
        }
        var node = http.request("GET", gateway + "/api/v1/public/track/" + tracking, headers(context), null);
        // Select and type-check every field. Never pass timelines or arbitrary upstream text to a model.
        PublicOrderStatus status;
        try { status = PublicOrderStatus.valueOf(node.path("currentPublicStatus").asText()); }
        catch (IllegalArgumentException ex) { throw new JsonHttpClient.Failure(502); }
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("outcome", "OK");
        safe.put("trackingNumber", tracking);
        safe.put("status", status.name());
        for (String field : List.of("originDistrict", "destinationDistrict")) {
            var district = SriLankanDistrict.fromValue(node.path(field).asText());
            if (district != null) safe.put(field, district.name());
        }
        return safe;
    }

    static Map<String, String> headers(ToolRequestContext context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Correlation-Id", context.correlationId());
        if (context.authorization() != null && context.authorization().startsWith("Bearer ")) {
            headers.put("Authorization", context.authorization());
        }
        return headers;
    }
}
