package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.domain.repository.CourierLoadRow;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Weight and volume of the parcels a courier holds. Summed from the assignments each time rather than kept
 * in a counter, so it cannot drift when a parcel is delivered, cancelled, moved or edited.
 */
public record CourierLoad(BigDecimal weightKg, BigDecimal volumeLitres) {

    public static final CourierLoad NONE = new CourierLoad(BigDecimal.ZERO, BigDecimal.ZERO);

    public static CourierLoad of(DispatchAssignmentRepository repository, UUID courierId) {
        return all(repository, java.util.List.of(courierId)).getOrDefault(courierId, NONE);
    }

    /** Loads by courier; a courier who holds nothing has no entry. */
    public static Map<UUID, CourierLoad> all(DispatchAssignmentRepository repository, Collection<UUID> courierIds) {
        if (courierIds.isEmpty()) {
            return Map.of();
        }
        return repository.sumActiveLoads(courierIds).stream()
                .collect(Collectors.toMap(CourierLoadRow::getCourierId, row ->
                        new CourierLoad(zeroIfNull(row.getWeightKg()), zeroIfNull(row.getVolumeLitres()))));
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
