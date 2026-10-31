package com.swiftdrop.analytics.ai.tools;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AnomalyRerouteSuggestionTool {

    public record RerouteDecision(
            SriLankanDistrict suggestedHub,
            String recommendedSpeed,
            int priorityScore,
            List<String> suggestedSteps,
            String rationale
    ) {}

    public RerouteDecision calculateReroute(String trackingNumber,
                                           SriLankanDistrict currentDistrict,
                                           SriLankanDistrict destinationDistrict,
                                           int delayMinutes,
                                           String anomalyReason) {
        if (currentDistrict == null) currentDistrict = SriLankanDistrict.COLOMBO;
        if (destinationDistrict == null) destinationDistrict = SriLankanDistrict.COLOMBO;

        SriLankanDistrict suggestedHub;
        if (destinationDistrict == SriLankanDistrict.JAFFNA || destinationDistrict == SriLankanDistrict.ANURADHAPURA) {
            suggestedHub = SriLankanDistrict.KURUNEGALA;
        } else if (destinationDistrict == SriLankanDistrict.KANDY || destinationDistrict == SriLankanDistrict.NUWARA_ELIYA) {
            suggestedHub = SriLankanDistrict.GAMPAHA;
        } else if (destinationDistrict == SriLankanDistrict.GALLE || destinationDistrict == SriLankanDistrict.MATARA) {
            suggestedHub = SriLankanDistrict.KALUTARA;
        } else {
            suggestedHub = SriLankanDistrict.COLOMBO;
        }

        int priorityScore = Math.min(10, Math.max(1, (delayMinutes / 15) + 3));
        String recommendedSpeed = delayMinutes > 40 ? "EXPRESS" : "STANDARD";

        List<String> steps = new ArrayList<>();
        steps.add("Hold consignment at current transit hub in " + currentDistrict);
        steps.add("Reroute bulk parcel batch via secondary expressway interchange through " + suggestedHub);
        steps.add("Hand off to dedicated priority regional courier in " + destinationDistrict);

        String rationale = String.format(
                "Anomaly '%s' caused %d min delay. Bypassing primary bottleneck through %s hub saves ~%d mins.",
                anomalyReason != null ? anomalyReason : "TRAFFIC_BOTTLENECK",
                delayMinutes,
                suggestedHub,
                Math.max(15, delayMinutes / 2)
        );

        return new RerouteDecision(suggestedHub, recommendedSpeed, priorityScore, steps, rationale);
    }
}
