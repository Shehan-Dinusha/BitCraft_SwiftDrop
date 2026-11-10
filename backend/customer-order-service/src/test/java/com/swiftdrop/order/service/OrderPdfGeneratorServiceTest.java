package com.swiftdrop.order.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderPdfGeneratorServiceTest {

    private static final String PICKUP_PHONE = "+94771234567";
    private static final String RECIPIENT_PHONE = "+94719876543";
    private static final String RECIPIENT_EMAIL = "recipient@example.com";

    private final OrderPdfGeneratorService pdfGeneratorService = new OrderPdfGeneratorService("https://swiftdrop.lk");
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
                .pickupContactName("Pickup Contact")
                .pickupContactPhone(PICKUP_PHONE)
                .pickupAddressLine1("No. 50, Galle Road")
                .pickupCity("Colombo 03")
                .pickupDistrict(SriLankanDistrict.COLOMBO)
                .pickupPostalCode("00300")
                .recipientName("Test Recipient")
                .recipientPhone(RECIPIENT_PHONE)
                .recipientEmail(RECIPIENT_EMAIL)
                .deliveryAddressLine1("No. 12, Kandy Road")
                .deliveryCity("Peradeniya")
                .deliveryDistrict(SriLankanDistrict.NUWARA_ELIYA)
                .deliveryPostalCode("20400")
                .actualWeightKg(new BigDecimal("1.80"))
                .chargeableWeightKg(new BigDecimal("2.00"))
                .lengthCm(new BigDecimal("25.5"))
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
                .createdAt(Instant.parse("2026-11-05T03:00:00Z"))
                .updatedAt(Instant.parse("2026-11-06T09:30:00Z"))
                .build();
    }

    @Test
    @DisplayName("Shipping label PDF has two pages, the label first and the receipt second")
    void shippingLabelAndReceiptHaveTwoPages() throws Exception {
        byte[] pdf = pdfGeneratorService.generateShippingLabelAndReceiptPdf(sampleOrder);

        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isEqualTo(2);
        assertThat(pageText(reader, 1)).contains("SwiftDrop", "SD-7K4M-9QX2", "DELIVER TO", "Print at actual size");
        assertThat(pageText(reader, 2)).contains("Booking receipt", "SD-7K4M-9QX2");
    }

    @Test
    @DisplayName("Label shows what the courier needs and nothing more")
    void labelCarriesOnlyCourierDetails() throws Exception {
        String label = pageText(new PdfReader(pdfGeneratorService.generateShippingLabelAndReceiptPdf(sampleOrder)), 1);

        assertThat(label)
                .contains("Test Recipient", "+94 71 987 6543", "No. 12, Kandy Road", "Peradeniya", "20400")
                .contains("NUWARA ELIYA", "EXPRESS", "COLLECT CASH FROM RECIPIENT", "LKR 2,500.00", "25.5 x 20 x 10 cm", "1 of 1");
        assertThat(label)
                .doesNotContain(PICKUP_PHONE, "+94 77 123 4567")
                .doesNotContain(RECIPIENT_EMAIL)
                .doesNotContain(sampleOrder.getOrderId().toString());
    }

    @Test
    @DisplayName("Label shows the recipient's backup phone and a one-line instruction, not the pickup backup phone")
    void labelShowsBackupPhoneAndInstruction() throws Exception {
        sampleOrder.setRecipientAlternatePhone("+94715556677");
        sampleOrder.setPickupContactAlternatePhone("+94779998888");
        sampleOrder.setCustomerNotes("Call on arrival, the gate is blue. " + "Leave with the neighbour if nobody answers. ".repeat(6));

        String label = pageText(new PdfReader(pdfGeneratorService.generateShippingLabelAndReceiptPdf(sampleOrder)), 1);

        assertThat(label).contains("Alt +94 71 555 6677", "Call on arrival, the gate is blue.", "...");
        assertThat(label).doesNotContain("+94 77 999 8888", "+94779998888");
    }

    @Test
    @DisplayName("Booking receipt shows the delivery instructions in full but no backup phones")
    void receiptShowsInstructionsOnly() throws Exception {
        sampleOrder.setRecipientAlternatePhone("+94715556677");
        sampleOrder.setPickupContactAlternatePhone("+94779998888");
        sampleOrder.setCustomerNotes("Call on arrival, the gate is blue.");

        String receipt = pageText(new PdfReader(pdfGeneratorService.generateShippingLabelAndReceiptPdf(sampleOrder)), 2);

        assertThat(receipt).contains("DELIVERY INSTRUCTIONS", "Call on arrival, the gate is blue.");
        assertThat(receipt).doesNotContain("555 6677", "999 8888", "5556677", "9998888");
    }

    @Test
    @DisplayName("Booking receipt has the fee but no phones, emails, street addresses or internal ids")
    void bookingReceiptIsPrivate() throws Exception {
        String receipt = pageText(new PdfReader(pdfGeneratorService.generateShippingLabelAndReceiptPdf(sampleOrder)), 2);

        assertThat(receipt)
                .contains("Estimated delivery fee", "LKR 1,185.00", "Colombo 03", "Peradeniya", "Nuwara Eliya district")
                .contains("Cash to collect from the recipient", "LKR 2,500.00");
        assertThat(receipt)
                .doesNotContain(PICKUP_PHONE, RECIPIENT_PHONE, "+94 71 987 6543", RECIPIENT_EMAIL)
                .doesNotContain("Galle Road", "Kandy Road")
                .doesNotContain("PRICING-2026-V1")
                .doesNotContain("Tax Invoice", "TOTAL PAID");
    }

    @Test
    @DisplayName("An accepted fee change shows the adjustment and the new total")
    void acceptedFeeChangeIsItemised() throws Exception {
        sampleOrder.setFinalFee(new BigDecimal("1305.00"));

        String receipt = pageText(new PdfReader(pdfGeneratorService.generateShippingLabelAndReceiptPdf(sampleOrder)), 2);

        assertThat(receipt).contains("Adjusted at pickup", "LKR +120.00", "LKR 1,305.00");
    }

    @Test
    @DisplayName("Delivery receipt shows the fee and cash collected separately, with no phone numbers")
    void deliveryReceipt() throws Exception {
        sampleOrder.setStatus(PublicOrderStatus.DELIVERED);
        byte[] pdf = pdfGeneratorService.generateDeliveryReceiptPdf(sampleOrder);

        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isEqualTo(1);
        String text = pageText(reader, 1);
        assertThat(text)
                .contains("Delivery receipt", "DELIVERED", "SD-7K4M-9QX2", "Test Recipient")
                .contains("Delivery fee", "LKR 1,185.00", "Cash collected from the recipient", "LKR 2,500.00");
        assertThat(text)
                .doesNotContain(PICKUP_PHONE, RECIPIENT_PHONE, "+94 71 987 6543", RECIPIENT_EMAIL)
                .doesNotContain("TOTAL PAID", "Tax Invoice");
    }

    @Test
    @DisplayName("Optional fields may be missing without breaking the documents")
    void toleratesMissingOptionalFields() throws Exception {
        sampleOrder.setCodAmount(null);
        sampleOrder.setDeclaredValue(null);
        sampleOrder.setItemDescription(null);
        sampleOrder.setChargeableWeightKg(null);
        sampleOrder.setOrderSpeed(null);

        String label = pageText(new PdfReader(pdfGeneratorService.generateShippingLabelAndReceiptPdf(sampleOrder)), 1);

        assertThat(label).contains("STANDARD", "NO CASH TO COLLECT");
        assertThat(pdfGeneratorService.generateDeliveryReceiptPdf(sampleOrder)).isNotEmpty();
    }

    @Test
    @DisplayName("QR codes point at the configured public tracking page")
    void trackingUrlUsesConfiguredBase() {
        OrderPdfGeneratorService custom = new OrderPdfGeneratorService("https://track.example.com/");

        assertThat(pdfGeneratorService.trackingUrl(sampleOrder)).isEqualTo("https://swiftdrop.lk/track/SD-7K4M-9QX2");
        assertThat(custom.trackingUrl(sampleOrder)).isEqualTo("https://track.example.com/track/SD-7K4M-9QX2");
    }

    @Test
    @DisplayName("A tracking base URL that is not http(s) is rejected at startup")
    void rejectsInvalidBaseUrl() {
        assertThatThrownBy(() -> new OrderPdfGeneratorService("javascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderPdfGeneratorService(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("The label QR code decodes to <base>/track/<trackingNumber>")
    void labelQrCodeDecodes() throws Exception {
        String url = pdfGeneratorService.trackingUrl(sampleOrder);

        BufferedImage image = padded(MatrixToImageWriter.toBufferedImage(OrderPdfGeneratorService.labelQrMatrix(url)), 8);

        assertThat(decode(image)).isEqualTo("https://swiftdrop.lk/track/SD-7K4M-9QX2");
    }

    @Test
    @DisplayName("The delivery receipt QR code, read back from the PDF, decodes to the tracking URL")
    void deliveryReceiptQrCodeDecodes() throws Exception {
        PdfReader reader = new PdfReader(pdfGeneratorService.generateDeliveryReceiptPdf(sampleOrder));
        PdfDictionary xObjects = reader.getPageN(1).getAsDict(PdfName.RESOURCES).getAsDict(PdfName.XOBJECT);
        PRStream stream = (PRStream) PdfReader.getPdfObject(xObjects.get(xObjects.getKeys().iterator().next()));
        int width = stream.getAsNumber(PdfName.WIDTH).intValue();
        int height = stream.getAsNumber(PdfName.HEIGHT).intValue();
        int components = PdfName.DEVICERGB.equals(stream.get(PdfName.COLORSPACE)) ? 3 : 1;
        byte[] samples = PdfReader.getStreamBytes(stream);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int offset = (y * width + x) * components;
                int grey = samples[offset] & 0xFF;
                image.setRGB(x, y, (grey << 16) | (grey << 8) | grey);
            }
        }

        assertThat(decode(image)).isEqualTo("https://swiftdrop.lk/track/SD-7K4M-9QX2");
    }

    @Test
    @DisplayName("The label Code 128 barcode decodes to the tracking number and fits the label at full bar width")
    void labelBarcodeDecodes() throws Exception {
        var code = OrderPdfGeneratorService.code128("SD-7K4M-9QX2");

        // 10X quiet zones on each side must fit inside the label content width (about 255 pt).
        assertThat(code.getBarcodeSize().getWidth() + 20 * 1.2f).isLessThan(255f);
        java.awt.Image drawn = code.createAwtImage(Color.BLACK, Color.WHITE);
        new javax.swing.ImageIcon(drawn); // loads the pixels synchronously
        BufferedImage bars = new BufferedImage(drawn.getWidth(null), drawn.getHeight(null), BufferedImage.TYPE_INT_RGB);
        bars.createGraphics().drawImage(drawn, 0, 0, null);
        BufferedImage padded = padded(bars, 3);

        assertThat(decode(padded)).isEqualTo("SD-7K4M-9QX2");
    }

    @Test
    @DisplayName("A trailing slash on the base URL is removed")
    void trailingSlashIsRemoved() {
        OrderPdfGeneratorService custom = new OrderPdfGeneratorService("http://localhost:5173//");

        assertThat(custom.trackingUrl(sampleOrder)).isEqualTo("http://localhost:5173/track/SD-7K4M-9QX2");
    }

    @Test
    @DisplayName("Delivery receipt falls back to the estimated fee when there is no final fee")
    void deliveryReceiptUsesEstimatedFeeWhenFinalFeeMissing() throws Exception {
        sampleOrder.setFinalFee(null);
        sampleOrder.setStatus(PublicOrderStatus.DELIVERED);

        String text = pageText(new PdfReader(pdfGeneratorService.generateDeliveryReceiptPdf(sampleOrder)), 1);

        assertThat(text).contains("Delivery fee", "LKR 1,185.00");
    }

    /** Enlarges the image by {@code scale} and surrounds it with a white quiet zone. */
    private static BufferedImage padded(BufferedImage source, int scale) {
        int margin = 40;
        BufferedImage out = new BufferedImage(source.getWidth() * scale + 2 * margin, source.getHeight() * scale + 2 * margin,
                BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(source, margin, margin, source.getWidth() * scale, source.getHeight() * scale, null);
        g.dispose();
        return out;
    }

    private static String decode(BufferedImage image) throws Exception {
        return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)))).getText();
    }

    private static String pageText(PdfReader reader, int page) throws Exception {
        return new PdfTextExtractor(reader).getTextFromPage(page);
    }
}
