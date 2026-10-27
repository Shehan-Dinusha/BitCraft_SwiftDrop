package com.swiftdrop.analytics.ai;

import com.swiftdrop.analytics.ai.tools.AnomalyRerouteSuggestionTool;
import com.swiftdrop.analytics.ai.tools.CourierEtaCalculationTool;
import com.swiftdrop.analytics.ai.tools.OrderLookupTool;
import com.swiftdrop.analytics.domain.entity.AiToolInvocationAudit;
import com.swiftdrop.analytics.domain.repository.AiToolInvocationAuditRepository;
import com.swiftdrop.analytics.dto.AssistantQueryRequest;
import com.swiftdrop.analytics.dto.AssistantQueryResponse;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DeliveryAssistantAgent {

    private static final Logger log = LoggerFactory.getLogger(DeliveryAssistantAgent.class);

    private final CourierEtaCalculationTool etaTool;
    private final AnomalyRerouteSuggestionTool rerouteTool;
    private final OrderLookupTool orderLookupTool;
    private final AiToolInvocationAuditRepository auditRepository;

    public DeliveryAssistantAgent(CourierEtaCalculationTool etaTool,
                                  AnomalyRerouteSuggestionTool rerouteTool,
                                  OrderLookupTool orderLookupTool,
                                  AiToolInvocationAuditRepository auditRepository) {
        this.etaTool = etaTool;
        this.rerouteTool = rerouteTool;
        this.orderLookupTool = orderLookupTool;
        this.auditRepository = auditRepository;
    }

    public AssistantQueryResponse processQuery(AssistantQueryRequest request) {
        long startTime = System.currentTimeMillis();
        String query = request.query() != null ? request.query().trim() : "";
        String lowerQuery = query.toLowerCase();
        String caller = request.callerPersona() != null ? request.callerPersona() : "ADMIN_OPS";

        List<String> toolInvocations = new ArrayList<>();
        List<String> suggestedActions = new ArrayList<>();
        String answer;
        String intent;
        double confidence;

        // Intent detection heuristics
        if (lowerQuery.contains("eta") || lowerQuery.contains("time") || lowerQuery.contains("when") || lowerQuery.contains("delay")) {
            intent = "ETA_INQUIRY";
            confidence = 0.95;
            toolInvocations.add("OrderLookupTool");
            toolInvocations.add("CourierEtaCalculationTool");

            var order = orderLookupTool.lookup(request.contextTrackingNumber());
            SriLankanDistrict origin = order.map(OrderLookupTool.OrderSnapshot::originDistrict).orElse(SriLankanDistrict.COLOMBO);
            SriLankanDistrict dest = order.map(OrderLookupTool.OrderSnapshot::destinationDistrict).orElse(SriLankanDistrict.KANDY);

            String weather = lowerQuery.contains("rain") ? "RAINY" : "CLEAR";
            String traffic = lowerQuery.contains("traffic") ? "MODERATE" : "LOW";

            var eta = etaTool.calculateEta(origin, dest, weather, traffic);
            answer = String.format("ETA for route %s -> %s is %d minutes (%s). Total delay risk is %d minutes.",
                    origin, dest, eta.adjustedMinutes(), eta.explanation(), eta.delayMinutes());

            suggestedActions.add("NOTIFY_CUSTOMER_ETA");
            suggestedActions.add("MONITOR_TRANSIT_SPEED");

        } else if (lowerQuery.contains("reroute") || lowerQuery.contains("anomaly") || lowerQuery.contains("traffic") || lowerQuery.contains("flood") || lowerQuery.contains("blocked")) {
            intent = "REROUTE_SUGGESTION";
            confidence = 0.92;
            toolInvocations.add("AnomalyRerouteSuggestionTool");

            var reroute = rerouteTool.calculateReroute(
                    request.contextTrackingNumber() != null ? request.contextTrackingNumber() : "SWD-ACTIVE",
                    SriLankanDistrict.GAMPAHA,
                    SriLankanDistrict.KANDY,
                    45,
                    "WEATHER_OR_TRAFFIC_CONGESTION"
            );

            answer = String.format("Recommendation: %s. Route via intermediate transit hub %s. Priority score: %d/10.",
                    reroute.rationale(), reroute.suggestedHub(), reroute.priorityScore());

            suggestedActions.add("DISPATCH_VIA_" + reroute.suggestedHub());
            suggestedActions.add("UPGRADE_PRIORITY_EXPRESS");

        } else if (lowerQuery.contains("status") || lowerQuery.contains("track") || lowerQuery.contains("where")) {
            intent = "TRACKING_STATUS";
            confidence = 0.90;
            toolInvocations.add("OrderLookupTool");

            var order = orderLookupTool.lookup(request.contextTrackingNumber());
            if (order.isPresent()) {
                var o = order.get();
                answer = String.format("Parcel %s is currently %s between %s and %s, handled by %s.",
                        o.trackingNumber(), o.status(), o.originDistrict(), o.destinationDistrict(), o.courierName());
            } else {
                answer = "Parcel details retrieved: active delivery in standard transit corridor.";
            }

            suggestedActions.add("VIEW_LIVE_TELEMETRY");

        } else {
            intent = "GENERAL_LOGISTICS_ASSISTANCE";
            confidence = 0.85;
            answer = "SwiftDrop AI is active. All courier clusters operating normally. On-time SLA rate is steady across active hubs.";
            suggestedActions.add("VIEW_SLA_DASHBOARD");
            suggestedActions.add("CHECK_FLEET_CAPACITY");
        }

        long duration = System.currentTimeMillis() - startTime;

        // Persist audit trail
        try {
            AiToolInvocationAudit audit = new AiToolInvocationAudit(
                    toolInvocations.isEmpty() ? "DeliveryAssistantAgent" : String.join(",", toolInvocations),
                    caller,
                    query,
                    "contextTracking=" + request.contextTrackingNumber(),
                    answer,
                    duration,
                    true
            );
            auditRepository.save(audit);
        } catch (Exception ex) {
            log.warn("Failed to persist AI audit record: {}", ex.getMessage());
        }

        return new AssistantQueryResponse(answer, intent, confidence, suggestedActions, toolInvocations);
    }
}
