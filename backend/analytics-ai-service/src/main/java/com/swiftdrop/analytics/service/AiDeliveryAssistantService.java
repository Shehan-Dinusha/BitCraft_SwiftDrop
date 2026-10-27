package com.swiftdrop.analytics.service;

import com.swiftdrop.analytics.ai.DeliveryAssistantAgent;
import com.swiftdrop.analytics.ai.tools.AnomalyRerouteSuggestionTool;
import com.swiftdrop.analytics.ai.tools.CourierEtaCalculationTool;
import com.swiftdrop.analytics.domain.entity.AiDeliveryInsight;
import com.swiftdrop.analytics.domain.entity.AiToolInvocationAudit;
import com.swiftdrop.analytics.domain.repository.AiDeliveryInsightRepository;
import com.swiftdrop.analytics.domain.repository.AiToolInvocationAuditRepository;
import com.swiftdrop.analytics.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class AiDeliveryAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiDeliveryAssistantService.class);

    private final CourierEtaCalculationTool etaTool;
    private final AnomalyRerouteSuggestionTool rerouteTool;
    private final DeliveryAssistantAgent agent;
    private final AiDeliveryInsightRepository insightRepository;
    private final AiToolInvocationAuditRepository auditRepository;

    public AiDeliveryAssistantService(CourierEtaCalculationTool etaTool,
                                      AnomalyRerouteSuggestionTool rerouteTool,
                                      DeliveryAssistantAgent agent,
                                      AiDeliveryInsightRepository insightRepository,
                                      AiToolInvocationAuditRepository auditRepository) {
        this.etaTool = etaTool;
        this.rerouteTool = rerouteTool;
        this.agent = agent;
        this.insightRepository = insightRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public DelayRiskPredictionResponse predictDelayRisk(DelayRiskPredictionRequest request) {
        var eta = etaTool.calculateEta(
                request.originDistrict(),
                request.destinationDistrict(),
                request.weatherCondition(),
                request.trafficCongestion()
        );

        int delayMins = eta.delayMinutes();
        String speed = request.deliverySpeed() != null ? request.deliverySpeed().toUpperCase() : "STANDARD";

        // Calculate risk score (0 to 100)
        double rawRisk = (delayMins * 1.5);
        if ("EXPRESS".equals(speed) || "SAME_DAY".equals(speed)) {
            rawRisk += 20.0; // Higher SLA penalty sensitivity for express
        }
        if (request.weatherCondition() != null && request.weatherCondition().toUpperCase().contains("RAIN")) {
            rawRisk += 15.0;
        }

        BigDecimal riskScore = BigDecimal.valueOf(Math.min(100.0, Math.max(5.0, rawRisk)))
                .setScale(2, RoundingMode.HALF_UP);

        String riskLevel;
        String action;
        if (riskScore.compareTo(new BigDecimal("70.00")) >= 0) {
            riskLevel = "CRITICAL";
            action = "DISPATCH_EXPEDITE_EXPRESS_SECONDARY_CORRIDOR";
        } else if (riskScore.compareTo(new BigDecimal("45.00")) >= 0) {
            riskLevel = "HIGH";
            action = "ALERT_COURIER_ASSIGN_BUFFER";
        } else if (riskScore.compareTo(new BigDecimal("20.00")) >= 0) {
            riskLevel = "MEDIUM";
            action = "MONITOR_TRANSIT_SPEED";
        } else {
            riskLevel = "LOW";
            action = "STANDARD_DISPATCH_DISCHARGE";
        }

        String envFactors = String.format("%s / %s",
                request.weatherCondition() != null ? request.weatherCondition() : "CLEAR",
                request.trafficCongestion() != null ? request.trafficCongestion() : "NORMAL");

        AiDeliveryInsight insight = new AiDeliveryInsight();
        insight.setInsightId(UUID.randomUUID());
        insight.setOriginDistrict(request.originDistrict());
        insight.setDestinationDistrict(request.destinationDistrict());
        insight.setDeliverySpeed(speed);
        insight.setRiskLevel(riskLevel);
        insight.setRiskScore(riskScore);
        insight.setPredictedDelayMinutes(delayMins);
        insight.setRecommendedAction(action);
        insight.setReasoning(eta.explanation());
        insight.setWeatherOrTrafficFactor(envFactors);

        insightRepository.save(insight);
        log.info("Saved AI delivery risk insight {} with score {}", insight.getInsightId(), riskScore);

        return new DelayRiskPredictionResponse(
                insight.getInsightId(),
                insight.getOriginDistrict(),
                insight.getDestinationDistrict(),
                insight.getDeliverySpeed(),
                insight.getRiskLevel(),
                insight.getRiskScore(),
                insight.getPredictedDelayMinutes(),
                insight.getRecommendedAction(),
                insight.getReasoning(),
                insight.getWeatherOrTrafficFactor()
        );
    }

    public AnomalyRerouteResponse suggestReroute(AnomalyRerouteRequest request) {
        var decision = rerouteTool.calculateReroute(
                request.trackingNumber(),
                request.currentDistrict(),
                request.destinationDistrict(),
                request.delayMinutes(),
                request.anomalyReason()
        );

        return new AnomalyRerouteResponse(
                request.trackingNumber(),
                decision.suggestedHub(),
                decision.recommendedSpeed(),
                decision.priorityScore(),
                decision.suggestedSteps(),
                decision.rationale()
        );
    }

    public AssistantQueryResponse processAgentQuery(AssistantQueryRequest request) {
        return agent.processQuery(request);
    }

    @Transactional(readOnly = true)
    public List<AiAuditResponse> listRecentAudits() {
        return auditRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::mapToAuditResponse)
                .toList();
    }

    private AiAuditResponse mapToAuditResponse(AiToolInvocationAudit a) {
        return new AiAuditResponse(
                a.getAuditId(),
                a.getToolName(),
                a.getCallerPersona(),
                a.getPromptOrQuery(),
                a.getOutputResult(),
                a.getExecutionTimeMs(),
                a.isSuccess(),
                a.getCreatedAt()
        );
    }
}
