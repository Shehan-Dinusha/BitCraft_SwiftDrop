package com.swiftdrop.analytics.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import java.util.*;
import java.util.regex.Pattern;

/** Offline intent parser. Operational values always come from tools. */
public class MockLlmClient implements LlmClient {
    private final ObjectMapper mapper;
    public MockLlmClient(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public Turn complete(List<Map<String, Object>> messages) {
        var last = messages.getLast();
        if ("tool".equals(last.get("role"))) {
            try {
                var result = mapper.readTree((String) last.get("content"));
                if (!"OK".equals(result.path("outcome").asText())) {
                    return Turn.answer(switch (result.path("outcome").asText()) {
                        case "NOT_FOUND" -> "No parcel is available for that tracking number in your account.";
                        case "INVALID_ARGUMENTS" -> "Please provide a valid tracking number or a weight from 0.01 to 50 kg and valid origin/destination districts.";
                        case "REFUSED" -> "I can only track a parcel or calculate a delivery estimate.";
                        default -> "The requested service is unavailable. Please try again later.";
                    });
                }
                if (result.has("status")) return Turn.answer("Parcel " + result.path("trackingNumber").asText()
                        + " is currently " + result.path("status").asText() + ".");
                return Turn.answer("Estimated STANDARD delivery fee: " + result.path("currency").asText()
                        + " " + result.path("estimatedFee").asText()
                        + ". This estimate uses actual weight only; dimensions may change the final price.");
            } catch (Exception ex) {
                return Turn.answer("The requested service returned an invalid result. Please try again later.");
            }
        }
        // Only user messages are considered intent input, never provider/tool instructions.
        var userMessages = messages.stream().filter(m -> "user".equals(m.get("role")))
                .map(m -> (String) m.get("content")).toList();
        String query = userMessages.getLast();
        // A short clarification supplements the preceding question. A new question replaces it.
        if (userMessages.size() > 1 && query.toLowerCase(Locale.ROOT).matches("(?s)^\\s*(from|to)\\s+.*")) {
            query = userMessages.get(userMessages.size() - 2) + " " + query;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        if (SafeText.requestsPrivateData(query)) return Turn.answer("I cannot disclose private customer information or credentials.");
        if (lower.matches("(?s).*(where|track|status).*")) {
            var match = Pattern.compile("\\b(?:SD|SWD)-[A-Z0-9]+(?:-[A-Z0-9]+)+\\b", Pattern.CASE_INSENSITIVE).matcher(query);
            if (!match.find()) return Turn.answer("What is your parcel tracking number?");
            return call("track_parcel", Map.of("trackingNumber", match.group().toUpperCase(Locale.ROOT)));
        }
        if (lower.matches("(?s).*(price|cost|how much|estimate|send|ship).*")) {
            var weight = Pattern.compile("(?i)(-?\\d+(?:\\.\\d+)?)\\s*(?:kg|kilograms?)\\b").matcher(query);
            if (!weight.find()) return Turn.answer("What is the parcel weight in kilograms?");
            var origin = districtAfter(query, "from");
            var dest = districtAfter(query, "to");
            if (dest == null) return Turn.answer("Which destination district should I use?");
            if (origin == null) return Turn.answer("Which origin district are you sending from? Please reply 'from Colombo', for example.");
            return call("calculate_estimate", Map.of("weightKg", new java.math.BigDecimal(weight.group(1)),
                    "originCity", origin, "destinationCity", dest));
        }
        return Turn.answer("I can track a parcel or estimate STANDARD delivery costs. Provide a tracking number, or weight and origin/destination districts.");
    }

    private String districtAfter(String query, String preposition) {
        String found = null;
        int latest = -1;
        for (var district : SriLankanDistrict.values()) {
            var matcher = Pattern.compile("(?i)\\b" + preposition + "\\s+" + district.displayName() + "\\b").matcher(query);
            while (matcher.find()) {
                if (matcher.start() > latest) { found = district.name(); latest = matcher.start(); }
            }
        }
        return found;
    }

    private Turn call(String name, Map<String, Object> args) {
        return new Turn(null, List.of(new ToolCall(UUID.randomUUID().toString(), name, mapper.valueToTree(args))));
    }
}
