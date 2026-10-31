package com.swiftdrop.order.service;

import com.swiftdrop.contracts.enums.FeeRevisionStatus;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPdfGeneratorServiceTest {

    private final OrderPdfGeneratorService pdfGeneratorService = new OrderPdfGeneratorService();
    private CustomerOrder sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = CustomerOrder.builder()
                .id(100L)
                .orderId(UUID.randomUUID())
                .trackingNumber("SD-7K4M-9QX2")
                .customerId(UUID.randomUUID())
                .orderSpeed(OrderSpeed.EXPRESS)
                .status(PublicOrderStatus.CREATED)
                .internalQueueStatus(InternalQueueStatus.CREATED)
                .pickupContactName("Kamal Jayawardena")
                .pickupContactPhone("+94771234567")
                .pickupAddressLine1("No. 50, Galle Road")
                .pickupCity("Colombo 03")
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .pickupPostalCode("00300")
                .recipientName("Nimal Senanayake")
                .recipientPhone("+94719876543")
                .recipientEmail("nimal.s@gmail.com")
                .deliveryAddressLine1("No. 12, Kandy Road")
                .deliveryCity("Peradeniya")
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .deliveryPostalCode("20400")
                .actualWeightKg(new BigDecimal("1.80"))
                .chargeableWeightKg(new BigDecimal("2.00"))
                .lengthCm(new BigDecimal("25.0"))
                .widthCm(new BigDecimal("20.0"))
                .heightCm(new BigDecimal("10.0"))
                .itemDescription("Books and Electronics")
                .codAmount(new BigDecimal("2500.00"))
                .declaredValue(new BigDecimal("7500.00"))
                .prohibitedItemsDeclared(true)
                .pricingRuleVersionId("PRICING-2026-V1")
                .estimatedFee(new BigDecimal("1185.00"))
                .finalFee(new BigDecimal("1185.00"))
                .currency("LKR")
                .feeRevisionStatus(FeeRevisionStatus.APPROVED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Generate 2-page A5 shipping label and receipt PDF successfully")
    void testGenerateShippingLabelAndReceiptPdf() {
        byte[] pdfBytes = pdfGeneratorService.generateShippingLabelAndReceiptPdf(sampleOrder);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(1024);

        // Verify standard PDF signature %PDF-
        String pdfHeader = new String(pdfBytes, 0, 5);
        assertThat(pdfHeader).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("Generate A5 final delivery and cash collection receipt PDF successfully")
    void testGenerateDeliveryReceiptPdf() {
        byte[] pdfBytes = pdfGeneratorService.generateDeliveryReceiptPdf(sampleOrder);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(1024);

        String pdfHeader = new String(pdfBytes, 0, 5);
        assertThat(pdfHeader).isEqualTo("%PDF-");
    }
}
