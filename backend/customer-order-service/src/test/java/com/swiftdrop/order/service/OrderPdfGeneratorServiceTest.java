package com.swiftdrop.order.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.lowagie.text.pdf.PRStream;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.swiftdrop.contracts.enums.FeeRevisionStatus;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderPdfGeneratorServiceTest {

    private static final String SENDER_PHONE = "0700000001";
    private static final String RECIPIENT_PHONE = "0700000002";
    private static final String RECIPIENT_EMAIL = "recipient@example.test";

    private final OrderPdfGeneratorService pdfGeneratorService = new OrderPdfGeneratorService("https://track.example.test");
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
                .pickupContactName("Test Sender")
                .pickupContactPhone(SENDER_PHONE)
                .pickupAddressLine1("1 Sender Street")
                .pickupCity("Colombo 03")
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .pickupPostalCode("00300")
                .recipientName("Test Recipient")
                .recipientPhone(RECIPIENT_PHONE)
                .recipientEmail(RECIPIENT_EMAIL)
                .deliveryAddressLine1("2 Recipient Road")
                .deliveryCity("Peradeniya")
                .deliveryDistrict(SriLankanDistrict.KANDY)
                .deliveryPostalCode("20400")
                .actualWeightKg(new BigDecimal("1.80"))
                .chargeableWeightKg(new BigDecimal("2.00"))
                .lengthCm(new BigDecimal("25.0"))
                .widthCm(new BigDecimal("20.0"))
                .heightCm(new BigDecimal("10.0"))
                .itemDescription("Books")
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

    @Test
    @DisplayName("Label page keeps the recipient phone for the courier but has no email, sender phone or order id")
    void labelPageShowsRecipientPhoneOnly() throws Exception {
        String label = pageText(pdfGeneratorService.generateShippingLabelAndReceiptPdf(sampleOrder), 1);

        assertThat(label).contains("SD-7K4M-9QX2", RECIPIENT_PHONE, "COD: LKR 2500.00");
        assertThat(label).doesNotContain(RECIPIENT_EMAIL, SENDER_PHONE, sampleOrder.getOrderId().toString());
    }

    @Test
    @DisplayName("Label without COD says no cash to collect and no longer claims prepaid")
    void labelWithoutCodSaysNoCash() throws Exception {
        sampleOrder.setCodAmount(BigDecimal.ZERO);

        String label = pageText(pdfGeneratorService.generateShippingLabelAndReceiptPdf(sampleOrder), 1);

        assertThat(label).contains("No cash to collect");
        assertThat(label).doesNotContain("Prepaid");
    }

    @Test
    @DisplayName("Fee page prints the stored fee and has no phone, email or street address")
    void feePageHasFeeAndNoPersonalContact() throws Exception {
        String fee = pageText(pdfGeneratorService.generateShippingLabelAndReceiptPdf(sampleOrder), 2);

        assertThat(fee).contains("SD-7K4M-9QX2", "LKR 1185.00", "EXPRESS", "2.00 kg billable", "COLOMBO", "KANDY");
        assertThat(fee).doesNotContain(RECIPIENT_PHONE, SENDER_PHONE, RECIPIENT_EMAIL, "Recipient Road", "Sender Street");
        assertThat(fee).doesNotContain("Base Delivery Service", "Weight Surcharge");
    }

    @Test
    @DisplayName("Delivery receipt shows tracking number, fee, both cities and times, and no phone, email or street")
    void deliveryReceiptHasExpectedFieldsAndNoContactDetails() throws Exception {
        String text = fullText(pdfGeneratorService.generateDeliveryReceiptPdf(sampleOrder));

        assertThat(text).contains("SD-7K4M-9QX2", "LKR 1185.00", "Colombo 03", "Peradeniya", "Booked:", "Last update:");
        assertThat(text).doesNotContain(RECIPIENT_PHONE, SENDER_PHONE, RECIPIENT_EMAIL, "Recipient Road");
    }

    @Test
    @DisplayName("Delivery receipt falls back to the estimated fee when no final fee is set")
    void deliveryReceiptUsesEstimatedFeeWhenFinalFeeMissing() throws Exception {
        sampleOrder.setFinalFee(null);

        String text = fullText(pdfGeneratorService.generateDeliveryReceiptPdf(sampleOrder));

        assertThat(text).contains("Delivery Fee: LKR 1185.00");
    }

    @Test
    @DisplayName("The QR code on the label and on the receipt points to <base>/track/<trackingNumber>")
    void qrCodesPointToThePublicTrackingPage() throws Exception {
        String expected = "https://track.example.test/track/SD-7K4M-9QX2";

        assertThat(pdfGeneratorService.trackingUrl(sampleOrder)).isEqualTo(expected);
        assertThat(decodeQr(pdfGeneratorService.generateShippingLabelAndReceiptPdf(sampleOrder))).isEqualTo(expected);
        assertThat(decodeQr(pdfGeneratorService.generateDeliveryReceiptPdf(sampleOrder))).isEqualTo(expected);
    }

    @Test
    @DisplayName("A trailing slash on the base URL is stripped")
    void trailingSlashIsStripped() {
        OrderPdfGeneratorService service = new OrderPdfGeneratorService("http://localhost:5173//");

        assertThat(service.trackingUrl(sampleOrder)).isEqualTo("http://localhost:5173/track/SD-7K4M-9QX2");
    }

    @Test
    @DisplayName("A base URL that is not http(s) is rejected")
    void nonHttpBaseUrlIsRejected() {
        assertThatThrownBy(() -> new OrderPdfGeneratorService("javascript:alert(1)")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderPdfGeneratorService("ftp://example.test")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderPdfGeneratorService("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderPdfGeneratorService(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static String pageText(byte[] pdf, int page) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(reader).getTextFromPage(page);
        } finally {
            reader.close();
        }
    }

    private static String fullText(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(new PdfTextExtractor(reader).getTextFromPage(page)).append('\n');
            }
            return text.toString();
        } finally {
            reader.close();
        }
    }

    /** Finds the first image on page 1 (raw 8-bit RGB samples) and decodes it as a QR code. */
    private static String decodeQr(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfDictionary resources = reader.getPageN(1).getAsDict(PdfName.RESOURCES);
            PdfDictionary xObjects = resources == null ? null : resources.getAsDict(PdfName.XOBJECT);
            assertThat(xObjects).as("images on page 1").isNotNull();
            PdfName imageName = xObjects.getKeys().iterator().next();
            PRStream stream = (PRStream) PdfReader.getPdfObject(xObjects.get(imageName));
            int width = stream.getAsNumber(PdfName.WIDTH).intValue();
            int height = stream.getAsNumber(PdfName.HEIGHT).intValue();
            int components = PdfName.DEVICERGB.equals(stream.get(PdfName.COLORSPACE)) ? 3 : 1;
            byte[] samples = PdfReader.getStreamBytes(stream);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int offset = (y * width + x) * components;
                    int r = samples[offset] & 0xFF;
                    int g = samples[offset + components / 3 * 1] & 0xFF;
                    int b = samples[offset + components / 3 * 2] & 0xFF;
                    image.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
        } finally {
            reader.close();
        }
    }
}
