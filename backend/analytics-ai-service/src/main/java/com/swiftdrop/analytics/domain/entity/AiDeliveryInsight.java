package com.swiftdrop.analytics.domain.entity;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_delivery_insights")
public class AiDeliveryInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "insight_id", nullable = false, unique = true)
    private UUID insightId = UUID.randomUUID();

    @Column(name = "tracking_number", length = 64)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_district", nullable = false, length = 64)
    private SriLankanDistrict originDistrict;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_district", nullable = false, length = 64)
    private SriLankanDistrict destinationDistrict;

    @Column(name = "delivery_speed", nullable = false, length = 64)
    private String deliverySpeed;

    @Column(name = "risk_level", nullable = false, length = 32)
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(name = "risk_score", precision = 5, scale = 2, nullable = false)
    private BigDecimal riskScore;

    @Column(name = "predicted_delay_minutes", nullable = false)
    private int predictedDelayMinutes;

    @Column(name = "recommended_action", nullable = false)
    private String recommendedAction;

    @Column(name = "reasoning", columnDefinition = "TEXT", nullable = false)
    private String reasoning;

    @Column(name = "weather_or_traffic_factor")
    private String weatherOrTrafficFactor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AiDeliveryInsight() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getInsightId() { return insightId; }
    public void setInsightId(UUID insightId) { this.insightId = insightId; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public SriLankanDistrict getOriginDistrict() { return originDistrict; }
    public void setOriginDistrict(SriLankanDistrict originDistrict) { this.originDistrict = originDistrict; }

    public SriLankanDistrict getDestinationDistrict() { return destinationDistrict; }
    public void setDestinationDistrict(SriLankanDistrict destinationDistrict) { this.destinationDistrict = destinationDistrict; }

    public String getDeliverySpeed() { return deliverySpeed; }
    public void setDeliverySpeed(String deliverySpeed) { this.deliverySpeed = deliverySpeed; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }

    public int getPredictedDelayMinutes() { return predictedDelayMinutes; }
    public void setPredictedDelayMinutes(int predictedDelayMinutes) { this.predictedDelayMinutes = predictedDelayMinutes; }

    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public String getWeatherOrTrafficFactor() { return weatherOrTrafficFactor; }
    public void setWeatherOrTrafficFactor(String weatherOrTrafficFactor) { this.weatherOrTrafficFactor = weatherOrTrafficFactor; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
