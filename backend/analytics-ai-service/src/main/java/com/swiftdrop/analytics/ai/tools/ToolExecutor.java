package com.swiftdrop.analytics.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.analytics.ai.*;
import com.swiftdrop.analytics.domain.entity.AiToolInvocationAudit;
import com.swiftdrop.analytics.domain.repository.AiToolInvocationAuditRepository;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

@Component
public class ToolExecutor {
    private final TrackingClient tracking;
    private final PricingClient pricing;
    private final AiToolInvocationAuditRepository audits;

    public ToolExecutor(TrackingClient tracking, PricingClient pricing, AiToolInvocationAuditRepository audits) {
        this.tracking = tracking; this.pricing = pricing; this.audits = audits;
    }

    public Map<String, Object> execute(LlmClient.ToolCall call, ToolRequestContext context) {
        long start = System.nanoTime();
        String name = call.name();
        String outcome;
        Map<String, Object> result;
        try {
            if (!Set.of("track_parcel", "calculate_estimate").contains(name)) {
                result = Map.of("outcome", "REFUSED");
            } else {
                var args = call.arguments();
                if (args == null || !args.isObject()) throw new IllegalArgumentException();
                Set<String> fields = new HashSet<>();
                args.fieldNames().forEachRemaining(fields::add);
                if ("track_parcel".equals(name)) {
                    if (!fields.equals(Set.of("trackingNumber"))) throw new IllegalArgumentException();
                    String number = requiredText(args, "trackingNumber");
                    validateTracking(number);
                    result = tracking.track(number, context);
                } else {
                    if (!fields.equals(Set.of("weightKg", "originCity", "destinationCity"))
                            || !args.path("weightKg").isNumber()) throw new IllegalArgumentException();
                    var weight = args.path("weightKg").decimalValue();
                    validateWeight(weight);
                    var origin = SriLankanDistrict.fromValue(requiredText(args, "originCity"));
                    var dest = SriLankanDistrict.fromValue(requiredText(args, "destinationCity"));
                    if (origin == null || dest == null) throw new IllegalArgumentException();
                    result = pricing.estimate(weight, origin, dest, context);
                }
            }
        } catch (IllegalArgumentException ex) {
            result = Map.of("outcome", "INVALID_ARGUMENTS");
        } catch (JsonHttpClient.Failure ex) {
            result = Map.of("outcome", ex.status() == 404 ? "NOT_FOUND" : "UNAVAILABLE");
        } catch (RuntimeException ex) {
            result = Map.of("outcome", "UNAVAILABLE");
        }
        outcome = (String) result.get("outcome");
        audit(name, context, outcome, (System.nanoTime() - start) / 1_000_000);
        return result;
    }

    public void audit(String name, ToolRequestContext context, String outcome, long durationMs) {
        // Never persist model-supplied tool names, raw prompts, arguments, answers or credentials.
        String safeName = Set.of("track_parcel", "calculate_estimate", "chat").contains(name) ? name : "DISALLOWED_TOOL";
        var audit = new AiToolInvocationAudit(safeName, context.role(), null, null, outcome, durationMs, "OK".equals(outcome));
        audit.setActorUserId(context.userId());
        audit.setCorrelationId(context.correlationId());
        audit.setOutcome(outcome);
        // Fail closed if durable audit cannot be written.
        audits.save(audit);
    }

    private static String requiredText(JsonNode node, String field) {
        if (!node.path(field).isTextual() || node.path(field).textValue().isBlank()) throw new IllegalArgumentException();
        return node.path(field).textValue();
    }

    public static void validateTracking(String number) {
        if (number == null || number.length() > 64 || !number.matches("(?:SD|SWD)-[A-Z0-9]{2,16}(?:-[A-Z0-9]{2,16}){1,5}")) {
            throw new IllegalArgumentException("Invalid tracking number");
        }
    }

    public static void validateWeight(BigDecimal weight) {
        if (weight == null || weight.compareTo(new BigDecimal("0.01")) < 0 || weight.compareTo(new BigDecimal("50")) > 0) {
            throw new IllegalArgumentException("Weight must be 0.01 to 50 kg");
        }
    }

    public static List<Map<String, Object>> definitions() {
        Map<String, Object> tracking = Map.of("trackingNumber", Map.of("type", "string", "description", "Public SD- or SWD- tracking number"));
        Map<String, Object> pricing = Map.of("weightKg", Map.of("type", "number", "minimum", 0.01, "maximum", 50),
                "originCity", Map.of("type", "string", "enum", districts()),
                "destinationCity", Map.of("type", "string", "enum", districts()));
        return List.of(definition("track_parcel", "Get public parcel status only; ask for tracking number if missing.", tracking),
                definition("calculate_estimate", "Estimate STANDARD actual-weight-only delivery fee. Ask for missing origin, destination or weight; never infer them.", pricing));
    }

    private static List<String> districts() { return Arrays.stream(SriLankanDistrict.values()).map(Enum::name).toList(); }
    private static Map<String, Object> definition(String name, String description, Map<String, Object> properties) {
        return Map.of("type", "function", "function", Map.of("name", name, "description", description, "strict", true,
                "parameters", Map.of("type", "object", "properties", properties,
                        "required", properties.keySet().stream().sorted().toList(), "additionalProperties", false)));
    }
}
