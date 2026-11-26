package com.swiftdrop.order.service.promise;

import com.swiftdrop.order.exception.SameDayNotAvailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static com.swiftdrop.contracts.enums.OrderSpeed.EXPRESS;
import static com.swiftdrop.contracts.enums.OrderSpeed.SAME_DAY;
import static com.swiftdrop.contracts.enums.OrderSpeed.STANDARD;
import static com.swiftdrop.contracts.enums.SriLankanDistrict.COLOMBO;
import static com.swiftdrop.contracts.enums.SriLankanDistrict.KANDY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryPromiseTest {

    /** Thursday 4 December 2026 is a Poya day in these tests. */
    private static final LocalDate POYA = LocalDate.of(2026, 12, 4);

    private static DeliveryPromise at(String localTime) {
        Clock clock = Clock.fixed(LocalDateTime.parse(localTime).atZone(DeliveryPromise.ZONE).toInstant(),
                DeliveryPromise.ZONE);
        return new DeliveryPromise(clock, (from, to) -> Set.of(POYA));
    }

    @Test
    @DisplayName("Booked on a Tuesday morning: Same day today, Express Wednesday, Standard Thursday or Friday")
    void tuesdayMorning() {
        DeliveryPromise promise = at("2026-11-24T09:30");
        assertThat(promise.deliverBy(SAME_DAY, COLOMBO, COLOMBO)).isEqualTo("2026-11-24");
        assertThat(promise.deliverBy(EXPRESS, COLOMBO, KANDY)).isEqualTo("2026-11-25");
        assertThat(promise.deliverBy(STANDARD, COLOMBO, COLOMBO)).isEqualTo("2026-11-26");
        assertThat(promise.deliverBy(STANDARD, COLOMBO, KANDY)).isEqualTo("2026-11-27");
    }

    @Test
    @DisplayName("Same day only in one district and by 11:00 on a working day")
    void sameDayRules() {
        assertThatThrownBy(() -> at("2026-11-24T09:30").deliverBy(SAME_DAY, COLOMBO, KANDY))
                .isInstanceOf(SameDayNotAvailableException.class).hasMessageContaining("one district");
        assertThatThrownBy(() -> at("2026-11-24T11:00").deliverBy(SAME_DAY, COLOMBO, COLOMBO))
                .isInstanceOf(SameDayNotAvailableException.class).hasMessageContaining("11:00");
        assertThatThrownBy(() -> at("2026-11-29T09:00").deliverBy(SAME_DAY, COLOMBO, COLOMBO))
                .isInstanceOf(SameDayNotAvailableException.class);
        assertThatThrownBy(() -> at("2026-12-04T09:00").deliverBy(SAME_DAY, COLOMBO, COLOMBO))
                .isInstanceOf(SameDayNotAvailableException.class);
    }

    @Test
    @DisplayName("After 15:00, on Sundays and on holidays the pickup moves to the next working day")
    void pickupMoves() {
        // Saturday 16:00: picked up Monday, Express Tuesday.
        assertThat(at("2026-11-28T16:00").deliverBy(EXPRESS, COLOMBO, KANDY)).isEqualTo("2026-12-01");
        // Sunday: picked up Monday, Standard in one district Wednesday.
        assertThat(at("2026-11-29T10:00").deliverBy(STANDARD, COLOMBO, COLOMBO)).isEqualTo("2026-12-02");
        // Wednesday 3 December, Express: Thursday is Poya, so Friday.
        assertThat(at("2026-12-03T10:00").deliverBy(EXPRESS, COLOMBO, COLOMBO)).isEqualTo("2026-12-05");
    }
}
