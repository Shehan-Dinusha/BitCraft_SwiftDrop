package com.swiftdrop.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Rates from summed delivered/failed outcomes; cancellations and returns are excluded.")
public record DeliverySuccessRateResponse(
        @Schema(description = "First included date") LocalDate from,
        @Schema(description = "Last included date") LocalDate to,
        @Schema(description = "Business reporting timezone", example = "Asia/Colombo") String timezone,
        @Schema(description = "Delivered outcomes") long delivered,
        @Schema(description = "Final failed outcomes") long failed,
        @Schema(description = "Delivered plus failed") long completed,
        @Schema(description = "100 * delivered / completed, rounded HALF_UP to two decimals; null when completed is zero", types = {"number", "null"}) BigDecimal successRatePercent,
        @Schema(description = "100 * failed / completed, rounded HALF_UP to two decimals; null when completed is zero", types = {"number", "null"}) BigDecimal failureRatePercent) {}
