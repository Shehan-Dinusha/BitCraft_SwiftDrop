package com.swiftdrop.gateway.trackinghub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.dto.tracking.HistoryEntry;
import com.swiftdrop.contracts.dto.tracking.InternalTrackingView;
import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingViewDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingDataSanitizerProxyTest {

    private final TrackingDataSanitizerProxy sanitizer = new TrackingDataSanitizerProxy();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void buildsPiiFreeViewFromStatusEvent() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-10-26T06:30:00Z");

        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SD-2026-000123",
                orderId,
                customerId,
                PublicOrderStatus.PICKED_UP,
                PublicOrderStatus.OUT_FOR_DELIVERY,
                InternalQueueStatus.OUT_FOR_DELIVERY,
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.KANDY,
                null,
                timestamp
        );

        PublicTrackingViewDto view = sanitizer.fromStatus(event);
        String json = objectMapper.writeValueAsString(view);

        assertThat(view.trackingNumber()).isEqualTo("SD-2026-000123");
        assertThat(view.currentPublicStatus()).isEqualTo(PublicOrderStatus.OUT_FOR_DELIVERY);
        assertThat(view.originDistrict()).isEqualTo(SriLankanDistrict.COLOMBO);
        assertThat(view.destinationDistrict()).isEqualTo(SriLankanDistrict.KANDY);
        assertThat(view.statusTimeline()).hasSize(1);
        assertThat(view.statusTimeline().getFirst().timestamp()).isEqualTo(timestamp);
        assertThat(json)
                .doesNotContain(customerId.toString())
                .doesNotContain(orderId.toString())
                .doesNotContain("courierId")
                .doesNotContain("address");
    }

    @Test
    void derivesPublicStatusFromInternalStatusWhenPublicStatusIsMissing() {
        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                null, null, "SD-1", null, null, null, null,
                InternalQueueStatus.OUTBOUND, SriLankanDistrict.GALLE,
                SriLankanDistrict.JAFFNA, null, null
        );

        PublicTrackingViewDto view = sanitizer.fromStatus(event);

        assertThat(view.currentPublicStatus()).isEqualTo(PublicOrderStatus.IN_TRANSIT);
    }

    @Test
    void dropsUnknownPiiFieldsFromRawEventJson() throws Exception {
        String raw = """
                {
                  "eventId": "6f1a3f1e-6f7e-4f2a-9a2b-0d3f9f5a1c11",
                  "trackingNumber": "SD-2026-000123",
                  "orderId": "0f8b2c14-1b2c-4d5e-8f90-123456789abc",
                  "customerId": "5c4b3a29-1f2e-4d3c-9b8a-76543210fedc",
                  "newPublicStatus": "OUT_FOR_DELIVERY",
                  "district": "COLOMBO",
                  "destinationDistrict": "KANDY",
                  "timestamp": "2026-10-26T06:30:00Z",
                  "customerName": "Nimal Perera",
                  "customerPhone": "0771234567",
                  "deliveryAddress": "12 Galle Road, Colombo 03",
                  "courierId": "9a8b7c6d-5e4f-3a2b-1c0d-ef1234567890",
                  "courierPhone": "0719876543"
                }
                """;

        ParcelStatusChangedEvent event = objectMapper.readValue(raw, ParcelStatusChangedEvent.class);
        String json = objectMapper.writeValueAsString(sanitizer.fromStatus(event));

        assertThat(json)
                .doesNotContain("Nimal Perera")
                .doesNotContain("0771234567")
                .doesNotContain("Galle Road")
                .doesNotContain("0719876543")
                .doesNotContain("customerId")
                .doesNotContain("courierId")
                .doesNotContain("orderId");
        assertThat(json).contains("SD-2026-000123", "OUT_FOR_DELIVERY", "COLOMBO", "KANDY");
    }

    @Test
    void viewExposesOnlyWhitelistedFields() throws Exception {
        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "SD-2026-000123", UUID.randomUUID(), UUID.randomUUID(),
                null, PublicOrderStatus.IN_TRANSIT, InternalQueueStatus.OUTBOUND,
                SriLankanDistrict.COLOMBO, SriLankanDistrict.KANDY, null, Instant.now());

        JsonNode view = objectMapper.readTree(objectMapper.writeValueAsString(sanitizer.fromStatus(event)));

        assertThat(fieldNames(view)).containsExactlyInAnyOrder(
                "trackingNumber", "currentPublicStatus", "originDistrict", "destinationDistrict",
                "originCity", "destinationCity", "senderNameMasked", "senderPhoneMasked",
                "receiverNameMasked", "receiverPhoneMasked", "statusTimeline");
        assertThat(fieldNames(view.get("statusTimeline").get(0)))
                .containsExactlyInAnyOrder("status", "district", "timestamp");
    }

    @Test
    void mapsCityAndMaskedContactFromInternalView() {
        InternalTrackingView internal = new InternalTrackingView(
                "SD-INT-001",
                PublicOrderStatus.IN_TRANSIT,
                List.of(new HistoryEntry(PublicOrderStatus.CREATED, Instant.parse("2026-10-26T06:00:00Z"))),
                "Colombo 03",
                SriLankanDistrict.COLOMBO,
                "Kandy",
                SriLankanDistrict.KANDY,
                "N. P.",
                "07X***4567",
                "K. S.",
                "07X***1234");

        PublicTrackingViewDto view = sanitizer.fromInternalView(internal);

        assertThat(view.originCity()).isEqualTo("Colombo 03");
        assertThat(view.destinationCity()).isEqualTo("Kandy");
        assertThat(view.senderNameMasked()).isEqualTo("N. P.");
        assertThat(view.senderPhoneMasked()).isEqualTo("07X***4567");
        assertThat(view.receiverNameMasked()).isEqualTo("K. S.");
        assertThat(view.receiverPhoneMasked()).isEqualTo("07X***1234");
        assertThat(view.statusTimeline()).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.district()).isNull();
                    assertThat(entry.status()).isEqualTo(PublicOrderStatus.CREATED);
                });
    }

    @Test
    void appendKeepsCityAndMaskedContactFromTheCachedView() {
        InternalTrackingView internal = new InternalTrackingView(
                "SD-APP-001",
                PublicOrderStatus.PICKED_UP,
                List.of(new HistoryEntry(PublicOrderStatus.CREATED, Instant.parse("2026-10-26T06:00:00Z"))),
                "Colombo 03",
                SriLankanDistrict.COLOMBO,
                "Kandy",
                SriLankanDistrict.KANDY,
                "N. P.",
                "07X***4567",
                "K. S.",
                "07X***1234");
        PublicTrackingViewDto current = sanitizer.fromInternalView(internal);

        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "SD-APP-001", UUID.randomUUID(), UUID.randomUUID(),
                null, PublicOrderStatus.IN_TRANSIT, InternalQueueStatus.OUTBOUND,
                SriLankanDistrict.MATALE, SriLankanDistrict.KANDY, null, Instant.now());

        PublicTrackingViewDto view = sanitizer.append(current, event);

        assertThat(view.originCity()).isEqualTo("Colombo 03");
        assertThat(view.destinationCity()).isEqualTo("Kandy");
        assertThat(view.senderNameMasked()).isEqualTo("N. P.");
        assertThat(view.receiverPhoneMasked()).isEqualTo("07X***1234");
        assertThat(view.statusTimeline()).hasSize(2);
    }

    @Test
    void needsContactDetailsOnlyWhenNoCityOrMaskedContactIsCached() {
        PublicTrackingViewDto empty = new PublicTrackingViewDto(
                "SD-EMPTY-001",
                PublicOrderStatus.IN_TRANSIT,
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.KANDY,
                List.of());
        PublicTrackingViewDto enriched = sanitizer.fromInternalView(new InternalTrackingView(
                "SD-FULL-001",
                PublicOrderStatus.IN_TRANSIT,
                List.of(),
                "Colombo 03",
                SriLankanDistrict.COLOMBO,
                "Kandy",
                SriLankanDistrict.KANDY,
                "N. P.",
                "07X***4567",
                "K. S.",
                "07X***1234"));

        assertThat(sanitizer.needsContactDetails(empty)).isTrue();
        assertThat(sanitizer.needsContactDetails(enriched)).isFalse();
        assertThat(sanitizer.needsContactDetails(null)).isFalse();
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void mapsAllInternalStatusesToPublicStatuses() {
        assertThat(InternalQueueStatus.CREATED.toPublicStatus()).isEqualTo(PublicOrderStatus.CREATED);
        assertThat(InternalQueueStatus.UNASSIGNED.toPublicStatus()).isEqualTo(PublicOrderStatus.CREATED);
        assertThat(InternalQueueStatus.PICKUP_ASSIGNED.toPublicStatus()).isEqualTo(PublicOrderStatus.PICKUP_ASSIGNED);
        assertThat(InternalQueueStatus.PICKED_UP.toPublicStatus()).isEqualTo(PublicOrderStatus.PICKED_UP);
        assertThat(InternalQueueStatus.AT_HUB.toPublicStatus()).isEqualTo(PublicOrderStatus.IN_TRANSIT);
        assertThat(InternalQueueStatus.OUTBOUND.toPublicStatus()).isEqualTo(PublicOrderStatus.IN_TRANSIT);
        assertThat(InternalQueueStatus.INCOMING.toPublicStatus()).isEqualTo(PublicOrderStatus.IN_TRANSIT);
        assertThat(InternalQueueStatus.OUT_FOR_DELIVERY.toPublicStatus()).isEqualTo(PublicOrderStatus.OUT_FOR_DELIVERY);
        assertThat(InternalQueueStatus.DELIVERED.toPublicStatus()).isEqualTo(PublicOrderStatus.DELIVERED);
        assertThat(InternalQueueStatus.FAILED_RETURN.toPublicStatus()).isEqualTo(PublicOrderStatus.RETURNED_TO_SENDER);
        assertThat(InternalQueueStatus.CANCELLED.toPublicStatus()).isEqualTo(PublicOrderStatus.CANCELLED);
    }

    @Test
    void mapsLegacyDeliveryStates() {
        assertThat(PublicOrderStatus.fromDeliveryState(DeliveryState.CREATED)).isEqualTo(PublicOrderStatus.CREATED);
        assertThat(PublicOrderStatus.fromDeliveryState(DeliveryState.ASSIGNED)).isEqualTo(PublicOrderStatus.PICKUP_ASSIGNED);
        assertThat(PublicOrderStatus.fromDeliveryState(DeliveryState.PICKED_UP)).isEqualTo(PublicOrderStatus.PICKED_UP);
        assertThat(PublicOrderStatus.fromDeliveryState(DeliveryState.IN_TRANSIT)).isEqualTo(PublicOrderStatus.IN_TRANSIT);
        assertThat(PublicOrderStatus.fromDeliveryState(DeliveryState.DELIVERED)).isEqualTo(PublicOrderStatus.DELIVERED);
        assertThat(PublicOrderStatus.fromDeliveryState(DeliveryState.FAILED)).isEqualTo(PublicOrderStatus.FAILED);
        assertThat(PublicOrderStatus.fromDeliveryState(DeliveryState.CANCELLED)).isEqualTo(PublicOrderStatus.CANCELLED);
        assertThat(PublicOrderStatus.fromDeliveryState(null)).isNull();
    }
}
