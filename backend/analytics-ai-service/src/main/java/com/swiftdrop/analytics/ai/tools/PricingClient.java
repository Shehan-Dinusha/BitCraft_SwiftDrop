package com.swiftdrop.analytics.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.analytics.ai.*;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

@Component
public class PricingClient {
    private final JsonHttpClient http;
    private final String gateway;
    public PricingClient(ObjectMapper mapper, @Value("${ai.gateway-url:http://localhost:8080}") String gateway) {
        this.http = new JsonHttpClient(mapper, Duration.ofSeconds(3));
        this.gateway = gateway.replaceAll("/+$", "");
    }

    public Map<String, Object> estimate(BigDecimal weight, SriLankanDistrict origin,
                                        SriLankanDistrict destination, ToolRequestContext context) {
        ToolExecutor.validateWeight(weight);
        if (origin == null || destination == null) throw new IllegalArgumentException("Districts required");
        var root = http.request("POST", gateway + "/api/v1/pricing/estimate", TrackingClient.headers(context),
                Map.of("pickupDistrict", origin.name(), "deliveryDistrict", destination.name(),
                        "actualWeightKg", weight, "speed", "STANDARD"));
        var data = root.path("data");
        if (!root.path("success").asBoolean() || !data.path("estimatedFee").isNumber()
                || data.path("estimatedFee").decimalValue().signum() < 0
                || !"LKR".equals(data.path("currency").asText())) throw new IllegalStateException("Invalid estimate result");
        return Map.of("outcome", "OK", "estimatedFee", data.path("estimatedFee").decimalValue(),
                "currency", "LKR", "originDistrict", origin.name(), "destinationDistrict", destination.name(),
                "weightKg", weight, "speed", "STANDARD", "basis", "ACTUAL_WEIGHT_ONLY");
    }
}
