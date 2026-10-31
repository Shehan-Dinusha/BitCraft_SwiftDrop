package com.swiftdrop.order.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class OrderPdfGeneratorService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Colombo"));

    private static final Color BRAND_NAVY = new Color(15, 23, 42); // slate-900
    private static final Color BRAND_AMBER = new Color(245, 158, 11); // amber-500
    private static final Color LIGHT_BG = new Color(248, 250, 252); // slate-50
    private static final Color BORDER_COLOR = new Color(226, 232, 240); // slate-200

    /**
     * Generates a 2-page A5 PDF:
     * - Page 1: Shipping Label with ZXing QR Code (FR-C4)
     * - Page 2: Fee Estimate Receipt with breakdown & prohibited declaration (FR-C5)
     */
    public byte[] generateShippingLabelAndReceiptPdf(CustomerOrder order) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // A5 Page Size: 419.5 x 595.3 points (148 x 210 mm)
            Document document = new Document(PageSize.A5, 24, 24, 24, 24);
            PdfWriter.getInstance(document, baos);
            document.open();

            // ═══════════════════════════════════════════════════════════════════
            // PAGE 1: SHIPPING LABEL
            // ═══════════════════════════════════════════════════════════════════
            buildPage1ShippingLabel(document, order);

            // Advance to Page 2
            document.newPage();

            // ═══════════════════════════════════════════════════════════════════
            // PAGE 2: FEE ESTIMATE RECEIPT
            // ═══════════════════════════════════════════════════════════════════
            buildPage2FeeEstimateReceipt(document, order);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate shipping label PDF for order {}", order.getOrderId(), e);
            throw new RuntimeException("Error generating shipping label PDF", e);
        }
    }

    /**
     * Generates an A5 Final Delivery & Cash Collection Receipt (FR-C7)
     */
    public byte[] generateDeliveryReceiptPdf(CustomerOrder order) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A5, 24, 24, 24, 24);
            PdfWriter.getInstance(document, baos);
            document.open();

            buildDeliveryReceipt(document, order);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate delivery receipt PDF for order {}", order.getOrderId(), e);
            throw new RuntimeException("Error generating delivery receipt PDF", e);
        }
    }

    private void buildPage1ShippingLabel(Document doc, CustomerOrder order) throws Exception {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BRAND_NAVY);
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND_NAVY);
        Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
        Font trackingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BRAND_NAVY);
        Font badgeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);

        // Header Table (Brand + Service Speed Badge)
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{3f, 1.5f});

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.addElement(new Paragraph("SwiftDrop Logistics", headerFont));
        titleCell.addElement(new Paragraph("Official Air & Ground Waybill", subFont));
        headerTable.addCell(titleCell);

        PdfPCell badgeCell = new PdfPCell();
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badgeCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        badgeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        String speedText = order.getOrderSpeed() != null ? order.getOrderSpeed().name() : "STANDARD";
        PdfPCell badgeInner = new PdfPCell(new Phrase("  " + speedText + "  ", badgeFont));
        badgeInner.setBackgroundColor(speedText.contains("EXPRESS") ? BRAND_AMBER : BRAND_NAVY);
        badgeInner.setHorizontalAlignment(Element.ALIGN_CENTER);
        badgeInner.setPadding(4);

        PdfPTable badgeWrap = new PdfPTable(1);
        badgeWrap.setWidthPercentage(90);
        badgeWrap.setHorizontalAlignment(Element.ALIGN_RIGHT);
        badgeWrap.addCell(badgeInner);
        badgeCell.addElement(badgeWrap);
        headerTable.addCell(badgeCell);

        doc.add(headerTable);
        doc.add(new Paragraph(" "));

        // Tracking Number + QR Code Section
        PdfPTable trackingTable = new PdfPTable(2);
        trackingTable.setWidthPercentage(100);
        trackingTable.setWidths(new float[]{2.5f, 1.5f});

        PdfPCell trackInfoCell = new PdfPCell();
        trackInfoCell.setBackgroundColor(LIGHT_BG);
        trackInfoCell.setBorderColor(BORDER_COLOR);
        trackInfoCell.setPadding(8);
        trackInfoCell.addElement(new Paragraph("TRACKING NUMBER", subFont));
        trackInfoCell.addElement(new Paragraph(order.getTrackingNumber(), trackingFont));
        trackInfoCell.addElement(new Paragraph("Order ID: " + order.getOrderId(), subFont));
        trackInfoCell.addElement(new Paragraph("Booked: " + DATE_FORMATTER.format(order.getCreatedAt()), subFont));
        trackingTable.addCell(trackInfoCell);

        // Generate ZXing QR Code
        byte[] qrBytes = generateQrCodeBytes("https://swiftdrop.lk/track/" + order.getTrackingNumber(), 120, 120);
        Image qrImage = Image.getInstance(qrBytes);
        qrImage.setAlignment(Image.ALIGN_CENTER);
        qrImage.scalePercent(65);

        PdfPCell qrCell = new PdfPCell(qrImage);
        qrCell.setBackgroundColor(LIGHT_BG);
        qrCell.setBorderColor(BORDER_COLOR);
        qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        qrCell.setPadding(4);
        trackingTable.addCell(qrCell);

        doc.add(trackingTable);
        doc.add(new Paragraph(" "));

        // Addresses Section: Sender (Pickup) & Recipient (Delivery)
        PdfPTable addressTable = new PdfPTable(2);
        addressTable.setWidthPercentage(100);
        addressTable.setWidths(new float[]{1f, 1f});

        // Pickup / Sender Cell
        PdfPCell pickupCell = new PdfPCell();
        pickupCell.setBorderColor(BORDER_COLOR);
        pickupCell.setPadding(8);
        pickupCell.addElement(new Paragraph("FROM (ORIGIN / SENDER)", boldFont));
        pickupCell.addElement(new Paragraph(order.getPickupContactName(), boldFont));
        pickupCell.addElement(new Paragraph("Phone: " + order.getPickupContactPhone(), textFont));
        pickupCell.addElement(new Paragraph(order.getPickupAddressLine1(), textFont));
        if (order.getPickupAddressLine2() != null && !order.getPickupAddressLine2().isBlank()) {
            pickupCell.addElement(new Paragraph(order.getPickupAddressLine2(), textFont));
        }
        pickupCell.addElement(new Paragraph(order.getPickupCity() + ", " + order.getPickupDistrict() + " (" + order.getPickupPostalCode() + ")", textFont));
        addressTable.addCell(pickupCell);

        // Delivery / Recipient Cell
        PdfPCell deliveryCell = new PdfPCell();
        deliveryCell.setBorderColor(BORDER_COLOR);
        deliveryCell.setPadding(8);
        deliveryCell.addElement(new Paragraph("TO (DESTINATION / RECIPIENT)", boldFont));
        deliveryCell.addElement(new Paragraph(order.getRecipientName(), boldFont));
        deliveryCell.addElement(new Paragraph("Phone: " + order.getRecipientPhone(), boldFont));
        deliveryCell.addElement(new Paragraph("Email: " + order.getRecipientEmail(), textFont));
        deliveryCell.addElement(new Paragraph(order.getDeliveryAddressLine1(), textFont));
        if (order.getDeliveryAddressLine2() != null && !order.getDeliveryAddressLine2().isBlank()) {
            deliveryCell.addElement(new Paragraph(order.getDeliveryAddressLine2(), textFont));
        }
        deliveryCell.addElement(new Paragraph(order.getDeliveryCity() + ", " + order.getDeliveryDistrict() + " (" + order.getDeliveryPostalCode() + ")", textFont));
        addressTable.addCell(deliveryCell);

        doc.add(addressTable);
        doc.add(new Paragraph(" "));

        // Package Metrics & COD
        PdfPTable metricsTable = new PdfPTable(3);
        metricsTable.setWidthPercentage(100);
        metricsTable.setWidths(new float[]{1.2f, 1.2f, 1.2f});

        PdfPCell weightCell = new PdfPCell();
        weightCell.setBorderColor(BORDER_COLOR);
        weightCell.setPadding(6);
        weightCell.addElement(new Paragraph("WEIGHT METRICS", subFont));
        weightCell.addElement(new Paragraph("Actual: " + order.getActualWeightKg() + " kg", boldFont));
        weightCell.addElement(new Paragraph("Billable: " + order.getChargeableWeightKg() + " kg", textFont));
        metricsTable.addCell(weightCell);

        PdfPCell dimCell = new PdfPCell();
        dimCell.setBorderColor(BORDER_COLOR);
        dimCell.setPadding(6);
        dimCell.addElement(new Paragraph("DIMENSIONS (L x W x H)", subFont));
        dimCell.addElement(new Paragraph(order.getLengthCm() + " x " + order.getWidthCm() + " x " + order.getHeightCm() + " cm", boldFont));
        dimCell.addElement(new Paragraph(order.getItemDescription() != null ? order.getItemDescription() : "Standard Parcel", textFont));
        metricsTable.addCell(dimCell);

        PdfPCell codCell = new PdfPCell();
        codCell.setBorderColor(BORDER_COLOR);
        codCell.setPadding(6);
        codCell.addElement(new Paragraph("COLLECTION (COD)", subFont));
        if (order.getCodAmount() != null && order.getCodAmount().compareTo(BigDecimal.ZERO) > 0) {
            codCell.addElement(new Paragraph("COD: LKR " + order.getCodAmount(), boldFont));
            codCell.addElement(new Paragraph("Collect from recipient", textFont));
        } else {
            codCell.addElement(new Paragraph("NO CASH COLLECTION", boldFont));
            codCell.addElement(new Paragraph("Prepaid / Standard", textFont));
        }
        metricsTable.addCell(codCell);

        doc.add(metricsTable);
        doc.add(new Paragraph(" "));

        // Footer Instructions
        Paragraph footer = new Paragraph("Attach this label securely to the parcel exterior. QR code must remain unscratched and fully visible for courier mobile scanning at pickup and delivery.", subFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);
    }

    private void buildPage2FeeEstimateReceipt(Document doc, CustomerOrder order) throws Exception {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, BRAND_NAVY);
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BRAND_NAVY);
        Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
        Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, BRAND_NAVY);

        Paragraph title = new Paragraph("SwiftDrop Official Fee Estimate Receipt", headerFont);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);

        Paragraph subTitle = new Paragraph("Customer Order Reference: " + order.getTrackingNumber() + " | Page 2 of 2", subFont);
        subTitle.setAlignment(Element.ALIGN_CENTER);
        doc.add(subTitle);
        doc.add(new Paragraph(" "));

        // Summary Info
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1f, 1f});

        PdfPCell c1 = new PdfPCell();
        c1.setBorder(Rectangle.NO_BORDER);
        c1.addElement(new Paragraph("Date & Time: " + DATE_FORMATTER.format(order.getCreatedAt()), textFont));
        c1.addElement(new Paragraph("Origin District: " + order.getPickupDistrict(), textFont));
        c1.addElement(new Paragraph("Destination District: " + order.getDeliveryDistrict(), textFont));
        infoTable.addCell(c1);

        PdfPCell c2 = new PdfPCell();
        c2.setBorder(Rectangle.NO_BORDER);
        c2.addElement(new Paragraph("Tariff Rule ID: " + order.getPricingRuleVersionId(), textFont));
        c2.addElement(new Paragraph("Service Level: " + order.getOrderSpeed(), textFont));
        c2.addElement(new Paragraph("Billable Weight: " + order.getChargeableWeightKg() + " kg", textFont));
        infoTable.addCell(c2);

        doc.add(infoTable);
        doc.add(new Paragraph(" "));

        // Itemized Cost Breakdown Table
        PdfPTable breakdownTable = new PdfPTable(2);
        breakdownTable.setWidthPercentage(100);
        breakdownTable.setWidths(new float[]{3f, 1.2f});

        PdfPCell th1 = new PdfPCell(new Phrase("Description", boldFont));
        th1.setBackgroundColor(LIGHT_BG);
        th1.setPadding(6);
        PdfPCell th2 = new PdfPCell(new Phrase("Amount (LKR)", boldFont));
        th2.setBackgroundColor(LIGHT_BG);
        th2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        th2.setPadding(6);
        breakdownTable.addCell(th1);
        breakdownTable.addCell(th2);

        boolean isIntra = order.getPickupDistrict() == order.getDeliveryDistrict();
        BigDecimal baseRate = isIntra ? new BigDecimal("350.00") : new BigDecimal("550.00");
        BigDecimal weightRate = order.getEstimatedFee().subtract(baseRate).max(BigDecimal.ZERO);

        addBreakdownRow(breakdownTable, "Base Delivery Service (First 1.0 kg included)", baseRate.toString(), textFont);
        addBreakdownRow(breakdownTable, "Weight Surcharge & Speed Service Multiplier", weightRate.toString(), textFont);

        if (order.getCodAmount() != null && order.getCodAmount().compareTo(BigDecimal.ZERO) > 0) {
            addBreakdownRow(breakdownTable, "Cash On Delivery (COD) Collection Amount", order.getCodAmount().toString(), textFont);
        }

        // Total Row
        PdfPCell totalDescCell = new PdfPCell(new Phrase("TOTAL ESTIMATED DELIVERY FEE", boldFont));
        totalDescCell.setBackgroundColor(LIGHT_BG);
        totalDescCell.setPadding(8);
        PdfPCell totalValCell = new PdfPCell(new Phrase("LKR " + order.getEstimatedFee(), totalFont));
        totalValCell.setBackgroundColor(LIGHT_BG);
        totalValCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalValCell.setPadding(8);
        breakdownTable.addCell(totalDescCell);
        breakdownTable.addCell(totalValCell);

        doc.add(breakdownTable);
        doc.add(new Paragraph(" "));

        // Prohibited Items Declaration Box (FR-C1 requirement)
        PdfPTable declTable = new PdfPTable(1);
        declTable.setWidthPercentage(100);
        PdfPCell declCell = new PdfPCell();
        declCell.setBackgroundColor(LIGHT_BG);
        declCell.setBorderColor(BRAND_NAVY);
        declCell.setPadding(8);
        declCell.addElement(new Paragraph("CUSTOMER PROHIBITED-ITEMS DECLARATION (CONFIRMED)", boldFont));
        declCell.addElement(new Paragraph(
                "The sender has legally affirmed that this package does NOT contain any hazardous, toxic, flammable, explosive, illegal, or restricted items under the laws of Sri Lanka and SwiftDrop Logistics terms of carriage. Verified at digital booking time.",
                textFont));
        declTable.addCell(declCell);
        doc.add(declTable);

        doc.add(new Paragraph(" "));
        Paragraph terms = new Paragraph("Payment terms: Delivery fees are final unless weight or volumetric adjustments are measured by the courier at parcel pickup. If revised, customer approval will be requested via SMS and app.", subFont);
        terms.setAlignment(Element.ALIGN_CENTER);
        doc.add(terms);
    }

    private void buildDeliveryReceipt(Document doc, CustomerOrder order) throws Exception {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, BRAND_NAVY);
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BRAND_NAVY);
        Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
        Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BRAND_NAVY);

        Paragraph title = new Paragraph("SwiftDrop Final Proof-of-Delivery & Cash Receipt", headerFont);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);

        Paragraph subTitle = new Paragraph("Tracking: " + order.getTrackingNumber() + " | Official Tax Invoice & Collection Slip", subFont);
        subTitle.setAlignment(Element.ALIGN_CENTER);
        doc.add(subTitle);
        doc.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 1f});

        PdfPCell c1 = new PdfPCell();
        c1.setBorderColor(BORDER_COLOR);
        c1.setPadding(8);
        c1.addElement(new Paragraph("RECIPIENT ACKNOWLEDGMENT", boldFont));
        c1.addElement(new Paragraph("Delivered To: " + order.getRecipientName(), textFont));
        c1.addElement(new Paragraph("Contact Phone: " + order.getRecipientPhone(), textFont));
        c1.addElement(new Paragraph("Address: " + order.getDeliveryAddressLine1() + ", " + order.getDeliveryCity(), textFont));
        table.addCell(c1);

        PdfPCell c2 = new PdfPCell();
        c2.setBorderColor(BORDER_COLOR);
        c2.setPadding(8);
        c2.addElement(new Paragraph("FINAL CASH COLLECTION SUMMARY", boldFont));
        c2.addElement(new Paragraph("Delivery Fee: LKR " + order.getFinalFee(), textFont));
        BigDecimal cod = order.getCodAmount() != null ? order.getCodAmount() : BigDecimal.ZERO;
        c2.addElement(new Paragraph("COD Amount Collected: LKR " + cod, textFont));
        BigDecimal totalPaid = order.getFinalFee().add(cod);
        c2.addElement(new Paragraph("TOTAL PAID: LKR " + totalPaid, totalFont));
        table.addCell(c2);

        doc.add(table);
        doc.add(new Paragraph(" "));

        // Signatures
        PdfPTable sigTable = new PdfPTable(2);
        sigTable.setWidthPercentage(100);
        sigTable.setWidths(new float[]{1f, 1f});

        PdfPCell s1 = new PdfPCell();
        s1.setBorder(Rectangle.NO_BORDER);
        s1.addElement(new Paragraph("Courier Signature & Stamp: ______________________", textFont));
        sigTable.addCell(s1);

        PdfPCell s2 = new PdfPCell();
        s2.setBorder(Rectangle.NO_BORDER);
        s2.addElement(new Paragraph("Recipient Signature: ______________________", textFont));
        sigTable.addCell(s2);

        doc.add(sigTable);
    }

    private void addBreakdownRow(PdfPTable table, String desc, String amount, Font font) {
        PdfPCell c1 = new PdfPCell(new Phrase(desc, font));
        c1.setPadding(5);
        c1.setBorderColor(BORDER_COLOR);
        PdfPCell c2 = new PdfPCell(new Phrase("LKR " + amount, font));
        c2.setPadding(5);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c2.setBorderColor(BORDER_COLOR);
        table.addCell(c1);
        table.addCell(c2);
    }

    private byte[] generateQrCodeBytes(String text, int width, int height) throws Exception {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
        return baos.toByteArray();
    }
}
