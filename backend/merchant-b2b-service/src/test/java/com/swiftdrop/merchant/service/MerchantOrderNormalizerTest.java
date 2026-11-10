package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.B2bTestData;
import com.swiftdrop.merchant.dto.*;
import com.swiftdrop.contracts.enums.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class MerchantOrderNormalizerTest {
    private static final jakarta.validation.ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final MerchantOrderNormalizer normalizer = new MerchantOrderNormalizer(FACTORY.getValidator());
    @AfterAll static void close() { FACTORY.close(); }

    @Test void convertsDistrictPhonesAndGramsToOrderContract() {
        UUID merchant = UUID.randomUUID();
        var normalized = normalizer.normalize(merchant, B2bTestData.order());
        assertThat(normalized.get("merchantId")).isEqualTo(merchant);
        assertThat(normalized.get("actualWeightKg")).isEqualTo(new BigDecimal("1.500"));
        assertThat(normalized.get("pickupContactPhone")).isEqualTo("+94771234567");
        assertThat(normalized.get("recipientPhone")).isEqualTo("+94711234567");
        assertThat(normalized.get("deliveryDistrict")).isEqualTo(SriLankanDistrict.GAMPAHA);
        assertThat(normalized.get("speed")).isEqualTo(OrderSpeed.EXPRESS);
        assertThat(normalizer.district("nuwara eliya")).isEqualTo(SriLankanDistrict.NUWARA_ELIYA);
        assertThat(normalizer.phone("0094771234567")).isEqualTo("+94771234567");
    }

    @Test void rejectsUnknownDistrictAndInvalidPhoneWithoutEchoingPii() {
        assertThatThrownBy(() -> normalizer.district("Not a district")).hasMessage("Invalid Sri Lankan district");
        assertThatThrownBy(() -> normalizer.phone("123")).hasMessage("Invalid Sri Lankan phone number");
    }

    @Test void requiresExactlyOneWeightAndExplicitDeclaration() {
        var base = B2bTestData.order();
        for (B2bParcel parcel : new B2bParcel[]{
                new B2bParcel(null, null, true, null, null, null),
                new B2bParcel(BigDecimal.ONE, new BigDecimal("1000"), true, null, null, null),
                new B2bParcel(BigDecimal.ONE, null, false, null, null, null),
                new B2bParcel(new BigDecimal("51"), null, true, null, null, null)}) {
            assertThatThrownBy(() -> normalizer.normalize(UUID.randomUUID(), new B2bOrderRequest(
                    base.externalRef(), base.pickup(), base.delivery(), parcel, base.speed())))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
