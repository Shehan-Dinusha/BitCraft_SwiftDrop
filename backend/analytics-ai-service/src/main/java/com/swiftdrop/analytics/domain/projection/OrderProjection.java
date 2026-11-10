package com.swiftdrop.analytics.domain.projection;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

public record OrderProjection(UUID orderId, Instant createdAt, SriLankanDistrict pickupDistrict,
        PublicOrderStatus currentStatus, Instant statusAt, SriLankanDistrict district,
        UUID courierId, Instant assignedAt, PublicOrderStatus outcome, Instant outcomeAt,
        SriLankanDistrict outcomeDistrict, UUID outcomeCourierId, String conflictReason) {
    public static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Colombo");

    public static LocalDate date(Instant instant) {
        return instant.atZone(REPORTING_ZONE).toLocalDate();
    }

    public boolean active() {
        return conflictReason == null && courierId != null && (currentStatus == null || !currentStatus.isTerminal());
    }

    public boolean completed() {
        return conflictReason == null && (outcome == PublicOrderStatus.DELIVERED || outcome == PublicOrderStatus.FAILED);
    }
}
