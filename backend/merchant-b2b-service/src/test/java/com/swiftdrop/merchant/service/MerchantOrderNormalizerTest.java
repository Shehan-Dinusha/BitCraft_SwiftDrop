package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.B2bTestData;
import com.swiftdrop.merchant.dto.*;
import com.swiftdrop.contracts.enums.*;
import com.swiftdrop.contracts.phone.SriLankanPhone;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class MerchantOrderNormalizerTest {
    private static final jakarta.validation.ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final MerchantOrderNormalizer normalizer = new MerchantOrderNormalizer(FACTORY.getValidator());
    private static final BigDecimal L = new BigDecimal("30"), W = new BigDecimal("20"), H = new BigDecimal("10");

    @AfterAll static void close() { FACTORY.close(); }

    @Test void convertsDistrictPhonesAndGramsToOrderContract() {
        UUID merchant = UUID.randomUUID();
        var normalized = normalizer.normalize(merchant, B2bTestData.order());
        assertThat(normalized.get("merchantId")).isEqualTo(merchant);
        assertThat(normalized.get("actualWeightKg")).isEqualTo(new BigDecimal("1.500"));
        assertThat(normalized.get("lengthCm")).isEqualTo(new BigDecimal("30"));
        assertThat(normalized.get("widthCm")).isEqualTo(new BigDecimal("20"));
        assertThat(normalized.get("heightCm")).isEqualTo(new BigDecimal("10"));
        assertThat(normalized.get("pickupContactPhone")).isEqualTo("+94771234567");
        assertThat(normalized.get("recipientPhone")).isEqualTo("+94711234567");
        assertThat(normalized.get("deliveryDistrict")).isEqualTo(SriLankanDistrict.GAMPAHA);
        assertThat(normalized.get("speed")).isEqualTo(OrderSpeed.EXPRESS);
        assertThat(normalizer.district("nuwara eliya")).isEqualTo(SriLankanDistrict.NUWARA_ELIYA);
    }

    @Test void rejectsUnknownDistrictAndInvalidPhoneWithoutEchoingPii() {
        assertThatThrownBy(() -> normalizer.district("Not a district")).hasMessage("Invalid Sri Lankan district");
        var base = B2bTestData.order();
        for (String phone : new String[]{"123", "077 123 4567", "0094771234567", "07712345678"}) {
            var delivery = new B2bAddress("25 Kandy Road", "Gampaha", "gampaha", "11000", "Test Recipient", phone);
            assertThatThrownBy(() -> normalizer.normalize(UUID.randomUUID(), new B2bOrderRequest(
                    base.externalRef(), base.pickup(), delivery, base.parcel(), base.speed())))
                    .hasMessage("delivery.contactPhone: " + SriLankanPhone.MESSAGE);
        }
    }

    @Test void requiresEverySideAndASizeThatFitsALorry() {
        BigDecimal max = new BigDecimal("500");
        rejects(new B2bParcel(BigDecimal.ONE, null, null, W, H, true, null, null, null), "Parcel length is required");
        rejects(new B2bParcel(BigDecimal.ONE, null, L, null, H, true, null, null, null), "Parcel width is required");
        rejects(new B2bParcel(BigDecimal.ONE, null, L, W, null, true, null, null, null), "Parcel height is required");
        rejects(new B2bParcel(BigDecimal.ONE, null, new BigDecimal("501"), W, H, true, null, null, null), "Must not exceed 500 cm");
        rejects(new B2bParcel(BigDecimal.ONE, null, max, max, max, true, null, null, null), "must not exceed 15,000 litres");
    }

    @Test void refusesCashOnDeliveryInsteadOfIgnoringIt() {
        rejects(new B2bParcel(BigDecimal.ONE, null, L, W, H, true, null, null, new BigDecimal("2500")),
                "SwiftDrop collects no money from the recipient");
        var base = B2bTestData.order();
        var body = normalizer.normalize(UUID.randomUUID(), new B2bOrderRequest(base.externalRef(), base.pickup(),
                base.delivery(), new B2bParcel(BigDecimal.ONE, null, L, W, H, true, null, null, BigDecimal.ZERO), base.speed()));
        assertThat(body).doesNotContainKey("codAmount");
    }

    private void rejects(B2bParcel parcel, String message) {
        var base = B2bTestData.order();
        assertThatThrownBy(() -> normalizer.normalize(UUID.randomUUID(), new B2bOrderRequest(
                base.externalRef(), base.pickup(), base.delivery(), parcel, base.speed())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }

    @Test void requiresExactlyOneWeightAndExplicitDeclaration() {
        var base = B2bTestData.order();
        for (B2bParcel parcel : new B2bParcel[]{
                new B2bParcel(null, null, L, W, H, true, null, null, null),
                new B2bParcel(BigDecimal.ONE, new BigDecimal("1000"), L, W, H, true, null, null, null),
                new B2bParcel(BigDecimal.ONE, null, L, W, H, false, null, null, null),
                new B2bParcel(new BigDecimal("51"), null, L, W, H, true, null, null, null)}) {
            assertThatThrownBy(() -> normalizer.normalize(UUID.randomUUID(), new B2bOrderRequest(
                    base.externalRef(), base.pickup(), base.delivery(), parcel, base.speed())))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
