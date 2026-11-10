package com.swiftdrop.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Current projected workload, including known idle couriers; not online availability or historical workload.")
public record CourierWorkloadReportResponse(
        @Schema(description = "Projection read time, not broker completeness") Instant asOf,
        @Schema(description = "Sum of active orders across couriers") long totalActiveOrders,
        @Schema(description = "Known couriers sorted by ID; empty array if none") List<Courier> couriers) {
    @Schema(description = "Known courier's current nonterminal assigned orders")
    public record Courier(
            @Schema(description = "Courier identifier") UUID courierId,
            @Schema(description = "Current active order count") long activeOrders) {}
}
