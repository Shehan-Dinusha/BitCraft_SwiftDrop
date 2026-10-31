package com.swiftdrop.analytics.ai.tools;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CourierEtaCalculationTool {

    // Approximate transit baseline minutes between districts (hub-and-spoke Sri Lanka)
    private static final Map<String, Integer> DISTRICT_BASE_TIMES = Map.ofEntries(
            Map.entry("COLOMBO_COLOMBO", 35),
            Map.entry("COLOMBO_GAMPAHA", 45),
            Map.entry("COLOMBO_KALUTARA", 55),
            Map.entry("COLOMBO_KANDY", 120),
            Map.entry("COLOMBO_GALLE", 90),
            Map.entry("COLOMBO_MATARA", 110),
            Map.entry("COLOMBO_HAMBANTOTA", 150),
            Map.entry("COLOMBO_KURUNEGALA", 95),
            Map.entry("COLOMBO_JAFFNA", 360),
            Map.entry("COLOMBO_RATNAPURA", 100),
            Map.entry("GAMPAHA_KANDY", 90),
            Map.entry("KANDY_NUWARA_ELIYA", 75),
            Map.entry("KANDY_MATALE", 40)
    );

    public record EtaResult(int baselineMinutes, int adjustedMinutes, int delayMinutes, String explanation) {}

    public EtaResult calculateEta(SriLankanDistrict origin, SriLankanDistrict destination,
                                  String weather, String traffic) {
        if (origin == null) origin = SriLankanDistrict.COLOMBO;
        if (destination == null) destination = SriLankanDistrict.COLOMBO;

        int baseMinutes = getBaseTransitMinutes(origin, destination);
        double multiplier = 1.0;
        StringBuilder explanation = new StringBuilder("Base transit: " + baseMinutes + " mins. ");

        if (weather != null) {
            String w = weather.toUpperCase();
            if (w.contains("RAIN")) {
                multiplier += 0.20;
                explanation.append("Rain adds +20%. ");
            } else if (w.contains("MONSOON") || w.contains("STORM")) {
                multiplier += 0.50;
                explanation.append("Monsoon weather adds +50%. ");
            }
        }

        if (traffic != null) {
            String t = traffic.toUpperCase();
            if (t.contains("SEVERE") || t.contains("HIGH")) {
                multiplier += 0.35;
                explanation.append("Severe traffic adds +35%. ");
            } else if (t.contains("MODERATE")) {
                multiplier += 0.15;
                explanation.append("Moderate traffic adds +15%. ");
            }
        }

        int adjustedMinutes = (int) Math.round(baseMinutes * multiplier);
        int delayMinutes = Math.max(0, adjustedMinutes - baseMinutes);

        return new EtaResult(baseMinutes, adjustedMinutes, delayMinutes, explanation.toString().trim());
    }

    private int getBaseTransitMinutes(SriLankanDistrict origin, SriLankanDistrict destination) {
        if (origin == destination) return 30;
        String key1 = origin.name() + "_" + destination.name();
        String key2 = destination.name() + "_" + origin.name();

        if (DISTRICT_BASE_TIMES.containsKey(key1)) return DISTRICT_BASE_TIMES.get(key1);
        if (DISTRICT_BASE_TIMES.containsKey(key2)) return DISTRICT_BASE_TIMES.get(key2);

        return 120; // Default inter-district transit baseline
    }
}
