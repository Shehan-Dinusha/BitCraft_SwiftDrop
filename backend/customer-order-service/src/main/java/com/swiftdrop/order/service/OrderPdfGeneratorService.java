package com.swiftdrop.order.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.Barcode;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.swiftdrop.contracts.enums.FeeRevisionStatus;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders the customer's A5 documents with OpenPDF:
 * <ul>
 *   <li>shipping label (page 1) and booking receipt (page 2), downloaded before pickup (FR-C4, FR-C5)</li>
 *   <li>delivery receipt, downloaded once the parcel is delivered (FR-C7)</li>
 * </ul>
 *
 * <p>The label is printed and stuck on the parcel, so it carries only what the courier needs: the
 * recipient's name, phone and address, and the sender's name and address (no sender phone, no emails).
 * The receipts are for the customer and carry no phone numbers or street addresses.</p>
 *
 * <p>Text uses the built-in Helvetica/Courier fonts (WinAnsi), so only characters from that set are
 * used in fixed copy. Names typed in Sinhala or Tamil need an embedded Unicode font to render; that
 * is a known limitation of this generator.</p>
 */
@Service
@Slf4j
public class OrderPdfGeneratorService {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH).withZone(COLOMBO);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(COLOMBO);

    // Palette: the Velocity design system's navy, slate neutrals and status colours.
    private static final Color INK = new Color(15, 23, 42);          // slate-900
    private static final Color BODY = new Color(51, 65, 85);         // slate-700
    private static final Color MUTED = new Color(100, 116, 139);     // slate-500
    private static final Color RULE = new Color(226, 232, 240);      // slate-200
    private static final Color SURFACE = new Color(248, 250, 252);   // slate-50
    private static final Color BRAND = new Color(29, 78, 216);       // blue-700
    private static final Color SUCCESS = new Color(21, 128, 61);     // green-700
    private static final Color SUCCESS_BG = new Color(240, 253, 244);// green-50
    private static final Color WHITE = Color.WHITE;

    private static final float MARGIN = 28f;
    private static final float FOOTER_SPACE = 30f;

    private final String trackingUrlPrefix;
    private final String trackingSiteLabel;

    private final BaseFont sans;
    private final BaseFont sansBold;
    private final BaseFont mono;
    private final BaseFont monoBold;

    public OrderPdfGeneratorService(
            @Value("${swiftdrop.public-tracking-base-url:https://swiftdrop.lk}") String publicTrackingBaseUrl) {
        String base = publicTrackingBaseUrl == null ? "" : publicTrackingBaseUrl.trim().replaceAll("/+$", "");
        if (!base.matches("(?i)^https?://[^\\s/]+(/\\S*)?$")) {
            throw new IllegalArgumentException("swiftdrop.public-tracking-base-url must be an http(s) URL");
        }
        this.trackingUrlPrefix = base + "/track/";
        this.trackingSiteLabel = base.replaceFirst("(?i)^https?://", "") + "/track";
        try {
            this.sans = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            this.sansBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            this.mono = BaseFont.createFont(BaseFont.COURIER, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            this.monoBold = BaseFont.createFont(BaseFont.COURIER_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new IllegalStateException("Built-in PDF fonts are unavailable", e);
        }
    }

    /** The public tracking page for an order, as encoded in the QR codes. */
    String trackingUrl(CustomerOrder order) {
        return trackingUrlPrefix + order.getTrackingNumber();
    }

    /**
     * Two A5 pages: the shipping label to print and attach (page 1), and the booking receipt with
     * the fee (page 2).
     */
    public byte[] generateShippingLabelAndReceiptPdf(CustomerOrder order) {
        return render(order, "shipping label", "Shipping label and receipt", 2, (doc, writer) -> {
            buildShippingLabel(doc, writer, order);
            doc.newPage();
            buildBookingReceipt(doc, order);
        });
    }

    /** One A5 page confirming delivery, the fee and any cash collected from the recipient. */
    public byte[] generateDeliveryReceiptPdf(CustomerOrder order) {
        return render(order, "delivery receipt", "Delivery receipt", 1,
                (doc, writer) -> buildDeliveryReceipt(doc, order));
    }

    // ─── Page 1: shipping label ───────────────────────────────────────────────
    //
    // A 100 x 150 mm (4 x 6 in) label, the size carriers' labels and pouches use, drawn at fixed
    // positions on the A5 sheet with one line of printing help underneath.
    //
    // Print rules (most senders print in black and white on a home printer):
    //  - 100% black only inside the label: no colour, no grey text, no large solid fills.
    //  - Nothing relies on colour: the service level differs by box fill AND letters, and cash on
    //    delivery has its own heavy panel.
    //  - Barcodes are vector, at a whole number of printer dots per bar (X = 1.2 pt = 5 dots at
    //    300 dpi), with quiet zones kept clear (10X for Code 128, 4 modules for QR).
    //  - Fixed zones in the order handlers need them: service, sort (district and postcode),
    //    tracking barcode, recipient, cash, return address.

    private static final Color BLACK = Color.BLACK;

    // Label colours are a second signal only: every coloured element also differs by shape or text,
    // and each is a deep 700 shade so it still prints as a dark grey with strong contrast in greyscale.
    // Barcode, QR code and all address text stay pure black.
    private static final Color STANDARD_COLOR = new Color(29, 78, 216);   // blue-700
    private static final Color EXPRESS_COLOR = new Color(146, 64, 14);    // amber-800: amber-700 printed too light in greyscale
    private static final Color SAME_DAY_COLOR = new Color(109, 40, 217);  // violet-700
    private static final Color COD_COLOR = new Color(185, 28, 28);        // red-700
    private static final float SERVICE_BAND = 4f;

    // Page and label geometry in PDF points (1 mm = 2.8346 pt), A5 = 419.53 x 595.28.
    private static final float FRAME_LEFT = 73.70f;
    private static final float FRAME_RIGHT = 345.83f;
    private static final float FRAME_BOTTOM = 147.40f;
    private static final float FRAME_TOP = 561.26f;
    private static final float PAD = 8.5f;
    private static final float CONTENT_LEFT = FRAME_LEFT + PAD;
    private static final float CONTENT_RIGHT = FRAME_RIGHT - PAD;

    // Zone boundaries, top to bottom.
    private static final float Z_HEADER = 515.9f;
    private static final float Z_SORT = 453.5f;
    private static final float Z_BARCODE = 368.5f;
    private static final float Z_RECIPIENT = 243.8f; // 44 mm: the recipient gets the most room
    private static final float Z_CASH = 192.8f;
    private static final float Z_RETURN = 164.4f;

    private static final float BAR_X = 1.2f;
    private static final float BAR_HEIGHT = 51f;

    private void buildShippingLabel(Document doc, PdfWriter writer, CustomerOrder order) throws Exception {
        PdfContentByte cb = writer.getDirectContent();
        cb.saveState();

        drawFrame(cb);
        drawHeader(cb, order);
        drawSortZone(cb, order);
        drawBarcode(cb, order);
        drawRecipient(cb, order);
        drawCashAndParcel(cb, order);
        drawReturnTo(cb, order);
        drawInfoStrip(cb, order);
        drawInstructions(cb);

        cb.restoreState();
        // Everything is drawn at fixed positions; mark the page as used so the receipt starts on page 2.
        writer.setPageEmpty(false);
    }

    private void drawFrame(PdfContentByte cb) {
        cb.setColorStroke(BLACK);
        cb.setLineWidth(1.5f);
        cb.rectangle(FRAME_LEFT, FRAME_BOTTOM, FRAME_RIGHT - FRAME_LEFT, FRAME_TOP - FRAME_BOTTOM);
        cb.stroke();
        rule(cb, Z_HEADER, 0.75f);
        rule(cb, Z_SORT, 0.75f);
        rule(cb, Z_BARCODE, 2f); // heavier: separates the sorting half from the delivery half
        rule(cb, Z_RECIPIENT, 0.75f);
        rule(cb, Z_CASH, 0.75f);
        rule(cb, Z_RETURN, 0.75f);
    }

    /** Wordmark on the left; service word and a 16 mm service icon on the right. */
    private void drawHeader(PdfContentByte cb, CustomerOrder order) {
        float top = FRAME_TOP;
        OrderSpeed speed = order.getOrderSpeed() == null ? OrderSpeed.STANDARD : order.getOrderSpeed();

        // Thin service-colour band across the top: priority is visible from across a sorting floor.
        cb.setColorFill(serviceColor(speed));
        cb.rectangle(FRAME_LEFT, top - SERVICE_BAND, FRAME_RIGHT - FRAME_LEFT, SERVICE_BAND);
        cb.fill();

        Phrase wordmark = new Phrase();
        wordmark.add(new Chunk("Swift", font(sansBold, 14, BLACK)));
        wordmark.add(new Chunk("Drop", font(sansBold, 14, STANDARD_COLOR)));
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, wordmark, CONTENT_LEFT, top - 22, 0);
        text(cb, trackingSiteLabel.replaceFirst("/track$", ""), sans, 7, CONTENT_LEFT, top - 32, Element.ALIGN_LEFT);

        float icon = mm(16) - 9; // fits under the colour band with 2 pt clearance
        float iconX = CONTENT_RIGHT - icon;
        float iconY = Z_HEADER + 3;
        drawServiceIcon(cb, speed, iconX, iconY, icon);

        float wordRight = iconX - 6;
        text(cb, speedLabel(speed).toUpperCase(Locale.ENGLISH), sansBold, 16, wordRight, top - 22, Element.ALIGN_RIGHT);
        if (positive(order.getCodAmount())) {
            float tagW = mm(12);
            float tagH = mm(6);
            float tagX = wordRight - tagW;
            float tagY = top - 42;
            cb.setColorFill(COD_COLOR);
            cb.rectangle(tagX, tagY, tagW, tagH);
            cb.fill();
            text(cb, "COD", sansBold, 9, tagX + tagW / 2, tagY + 5, Element.ALIGN_CENTER, WHITE);
        }
    }

    /** Standard: outlined "S". Express: solid "E". Same day: solid "SD" with an inset rule. */
    private void drawServiceIcon(PdfContentByte cb, OrderSpeed speed, float x, float y, float size) {
        boolean filled = speed != OrderSpeed.STANDARD;
        Color color = serviceColor(speed);
        if (filled) {
            cb.setColorFill(color);
            cb.rectangle(x, y, size, size);
            cb.fill();
        } else {
            cb.setColorStroke(color);
            cb.setLineWidth(2f);
            cb.rectangle(x + 1, y + 1, size - 2, size - 2);
            cb.stroke();
        }
        if (speed == OrderSpeed.SAME_DAY) {
            float inset = mm(1.5f);
            cb.setColorStroke(WHITE);
            cb.setLineWidth(1.5f);
            cb.rectangle(x + inset, y + inset, size - 2 * inset, size - 2 * inset);
            cb.stroke();
        }
        String letters = switch (speed) {
            case STANDARD -> "S";
            case EXPRESS -> "E";
            case SAME_DAY -> "SD";
        };
        float fontSize = letters.length() > 1 ? 20 : 36;
        float baseline = y + (size - fontSize * 0.72f) / 2;
        text(cb, letters, sansBold, fontSize, x + size / 2, baseline, Element.ALIGN_CENTER, filled ? WHITE : color);
    }

    private static Color serviceColor(OrderSpeed speed) {
        if (speed == null) {
            return STANDARD_COLOR;
        }
        return switch (speed) {
            case STANDARD -> STANDARD_COLOR;
            case EXPRESS -> EXPRESS_COLOR;
            case SAME_DAY -> SAME_DAY_COLOR;
        };
    }

    /** Sort zone: district and province on the left, postcode large on the right. */
    private void drawSortZone(PdfContentByte cb, CustomerOrder order) {
        float top = Z_HEADER;
        text(cb, "DELIVERY DISTRICT", sansBold, 6.5f, CONTENT_LEFT, top - 12, Element.ALIGN_LEFT);
        String district = districtName(order.getDeliveryDistrict()).toUpperCase(Locale.ENGLISH);
        float districtWidth = mm(62);
        float size = fit(district, sansBold, 18, 14, districtWidth);
        text(cb, district, sansBold, size, CONTENT_LEFT, top - 32, Element.ALIGN_LEFT);
        String province = province(order.getDeliveryDistrict());
        if (!province.isEmpty()) {
            text(cb, province, sans, 8, CONTENT_LEFT, top - 48, Element.ALIGN_LEFT);
        }
        text(cb, "POSTCODE", sansBold, 6.5f, CONTENT_RIGHT, top - 12, Element.ALIGN_RIGHT);
        text(cb, safe(order.getDeliveryPostalCode()), sansBold, 30, CONTENT_RIGHT, top - 44, Element.ALIGN_RIGHT);
    }

    /** Vector Code 128 of the tracking number, centred, with the digits below. */
    private void drawBarcode(PdfContentByte cb, CustomerOrder order) {
        String value = safe(order.getTrackingNumber());
        if (value.isEmpty()) {
            return;
        }
        Barcode128 code = code128(value);
        // Keep the symbol inside the frame with its 10X quiet zones; fall back to a narrower whole-dot X.
        if (code.getBarcodeSize().getWidth() > (CONTENT_RIGHT - CONTENT_LEFT) - 20 * BAR_X) {
            code.setX(0.96f);
        }
        PdfTemplate symbol = code.createTemplateWithBarcode(cb, BLACK, BLACK);
        float x = (FRAME_LEFT + FRAME_RIGHT - symbol.getWidth()) / 2;
        float y = Z_SORT - 11.5f - BAR_HEIGHT;
        cb.addTemplate(symbol, x, y);
        text(cb, value, monoBold, 16, (FRAME_LEFT + FRAME_RIGHT) / 2, Z_BARCODE + 8.5f, Element.ALIGN_CENTER);
    }

    /** The label's Code 128 of the tracking number, as drawn (bar width and height included). */
    static Barcode128 code128(String value) {
        Barcode128 code = new Barcode128();
        code.setCodeType(Barcode.CODE128);
        code.setCode(value);
        code.setX(BAR_X);
        code.setBarHeight(BAR_HEIGHT);
        code.setFont(null);
        return code;
    }

    /** Recipient on the left, QR code on the right. */
    private void drawRecipient(PdfContentByte cb, CustomerOrder order) throws Exception {
        float top = Z_BARCODE;
        float qrSize = mm(22);
        float qrX = CONTENT_RIGHT - qrSize;
        float qrY = top - 8.5f - qrSize;
        drawQr(cb, trackingUrl(order), qrX, qrY, qrSize);
        text(cb, "Scan to track", sans, 7, qrX + qrSize / 2, qrY - 9, Element.ALIGN_CENTER);

        float columnRight = qrX - 9;
        text(cb, "DELIVER TO", sansBold, 7, CONTENT_LEFT, top - 14, Element.ALIGN_LEFT);

        // Each call returns the y just below what it drew; baselines are placed from there.
        float y = top - 20;
        y = block(cb, safe(order.getRecipientName()), sansBold, 14, 11, 2 * 14 * 1.2f, CONTENT_LEFT, columnRight, y);
        // ColumnText reports the last baseline, so leave room for descenders before the next line.
        if (has(order.getRecipientPhone())) {
            text(cb, "Tel " + formatPhone(order.getRecipientPhone()), monoBold, 12, CONTENT_LEFT, y - 15, Element.ALIGN_LEFT);
            y -= 20;
        } else {
            y -= 5;
        }
        // The city line is pinned to the bottom of the zone. The street address shrinks to fit above
        // it, wrapping beside the QR code and using the full width once it is below it.
        float cityBaseline = Z_RECIPIENT + 7;
        String street = addressLines(order.getDeliveryAddressLine1(), order.getDeliveryAddressLine2());
        float qrClear = qrY - 13; // below the "Scan to track" caption
        float bottom = cityBaseline + 12;
        float addressEnd = y;
        if (!street.isEmpty() && y > bottom) {
            for (float size = 10; ; size -= 0.5f) {
                boolean fits = !Float.isNaN(flowAddress(cb, street, size, y, columnRight, qrClear, bottom, true));
                if (fits || size <= 6.5f) {
                    float end = flowAddress(cb, street, size, y, columnRight, qrClear, bottom, false);
                    addressEnd = Float.isNaN(end) ? bottom : end;
                    break;
                }
            }
        }
        // The city follows the address, but never lower than the bottom of the zone.
        text(cb, cityLine(order.getDeliveryCity(), order.getDeliveryPostalCode()), sansBold, 11,
                CONTENT_LEFT, Math.max(cityBaseline, addressEnd - 15), Element.ALIGN_LEFT);
    }

    /** Cash panel on the left (heavy and reversed when there is cash to collect); weight and piece count on the right. */
    private void drawCashAndParcel(PdfContentByte cb, CustomerOrder order) {
        float top = Z_RECIPIENT;
        float bottom = Z_CASH;
        float panelRight = CONTENT_LEFT + mm(62);
        float panelTop = top - 5;
        float panelBottom = bottom + 5;
        float panelH = panelTop - panelBottom;
        if (positive(order.getCodAmount())) {
            // Light tint for colour prints; it drops to near-white in greyscale, where the heavy border
            // and the solid square still carry the message.
            cb.setColorFill(new Color(254, 242, 242)); // red-50
            cb.rectangle(CONTENT_LEFT, panelBottom, panelRight - CONTENT_LEFT, panelH);
            cb.fill();
            cb.setColorStroke(COD_COLOR);
            cb.setLineWidth(2.5f);
            cb.rectangle(CONTENT_LEFT, panelBottom, panelRight - CONTENT_LEFT, panelH);
            cb.stroke();
            float square = panelH;
            cb.setColorFill(COD_COLOR);
            cb.rectangle(CONTENT_LEFT, panelBottom, square, square);
            cb.fill();
            text(cb, "COD", sansBold, 12, CONTENT_LEFT + square / 2, panelBottom + square / 2 - 4, Element.ALIGN_CENTER, WHITE);
            float textX = CONTENT_LEFT + square + 6;
            text(cb, "COLLECT CASH FROM RECIPIENT", sansBold, 6.5f, textX, panelTop - 10, Element.ALIGN_LEFT, COD_COLOR);
            String amount = currency(order) + " " + money(order.getCodAmount());
            float size = fit(amount, sansBold, 17, 12, panelRight - textX - 4);
            text(cb, amount, sansBold, size, textX, panelBottom + 6, Element.ALIGN_LEFT);
        } else {
            cb.setColorStroke(BLACK);
            cb.setLineWidth(0.75f);
            cb.rectangle(CONTENT_LEFT, panelBottom, panelRight - CONTENT_LEFT, panelH);
            cb.stroke();
            text(cb, "NO CASH TO COLLECT", sansBold, 10, (CONTENT_LEFT + panelRight) / 2,
                    panelBottom + panelH / 2 - 3.5f, Element.ALIGN_CENTER);
        }

        cb.setColorStroke(BLACK);
        cb.setLineWidth(0.75f);
        float divider = panelRight + 8;
        cb.moveTo(divider, bottom);
        cb.lineTo(divider, top);
        cb.stroke();
        float factsX = divider + 8;
        text(cb, "WEIGHT", sansBold, 6.5f, factsX, top - 12, Element.ALIGN_LEFT);
        text(cb, kg(order.getActualWeightKg()), sansBold, 12, factsX, top - 24, Element.ALIGN_LEFT);
        text(cb, "PIECE", sansBold, 6.5f, factsX, top - 35, Element.ALIGN_LEFT);
        text(cb, "1 of 1", sansBold, 12, factsX, top - 47, Element.ALIGN_LEFT);
    }

    /** Return address on one or two compact lines: the sender's name in bold, then the address. No phone. */
    private void drawReturnTo(PdfContentByte cb, CustomerOrder order) throws Exception {
        float top = Z_CASH;
        text(cb, "IF UNDELIVERED, RETURN TO", sansBold, 6.5f, CONTENT_LEFT, top - 9.5f, Element.ALIGN_LEFT);
        String address = addressLines(order.getPickupAddressLine1(), order.getPickupAddressLine2()) + ", "
                + placeShort(order.getPickupCity(), order.getPickupDistrict(), order.getPickupPostalCode());
        float boxTop = top - 11.5f;
        float boxBottom = Z_RETURN + 2;
        for (float size = 8; ; size -= 0.5f) {
            ColumnText probe = returnColumn(cb, order.getPickupContactName(), address, size, boxTop, boxBottom);
            if (!ColumnText.hasMoreText(probe.go(true)) || size <= 6.5f) {
                returnColumn(cb, order.getPickupContactName(), address, size, boxTop, boxBottom).go();
                return;
            }
        }
    }

    private ColumnText returnColumn(PdfContentByte cb, String name, String address, float size, float top, float bottom) {
        ColumnText ct = new ColumnText(cb);
        ct.setSimpleColumn(CONTENT_LEFT, bottom, CONTENT_RIGHT, top);
        ct.setLeading(size * 1.2f);
        Phrase phrase = new Phrase();
        if (has(name)) {
            phrase.add(new Chunk(name.trim() + ", ", font(sansBold, size, BLACK)));
        }
        phrase.add(new Chunk(address, font(sans, size, BLACK)));
        ct.addText(phrase);
        return ct;
    }

    private void drawInfoStrip(PdfContentByte cb, CustomerOrder order) {
        String info = "Booked " + date(order.getCreatedAt()) + "   |   " + dimensions(order) + " cm   |   "
                + trackingSiteLabel;
        text(cb, info, sans, 7, (FRAME_LEFT + FRAME_RIGHT) / 2, FRAME_BOTTOM + 6, Element.ALIGN_CENTER);
    }

    /** One simple line of printing help under the label. */
    private void drawInstructions(PdfContentByte cb) {
        text(cb, "Print at actual size, cut out the label and tape it flat on the parcel.", sans, 8,
                (FRAME_LEFT + FRAME_RIGHT) / 2, FRAME_BOTTOM - 16, Element.ALIGN_CENTER, BODY);
    }

    private void rule(PdfContentByte cb, float y, float width) {
        cb.setColorStroke(BLACK);
        cb.setLineWidth(width);
        cb.moveTo(FRAME_LEFT, y);
        cb.lineTo(FRAME_RIGHT, y);
        cb.stroke();
    }

    private void text(PdfContentByte cb, String value, BaseFont base, float size, float x, float y, int align) {
        text(cb, value, base, size, x, y, align, BLACK);
    }

    private void text(PdfContentByte cb, String value, BaseFont base, float size, float x, float y, int align, Color color) {
        ColumnText.showTextAligned(cb, align, new Phrase(value, font(base, size, color)), x, y, 0);
    }

    /** Largest size between {@code max} and {@code min} at which one line fits {@code width}. */
    private static float fit(String value, BaseFont base, float max, float min, float width) {
        float size = max;
        while (size > min && base.getWidthPoint(value, size) > width) {
            size -= 0.5f;
        }
        return size;
    }

    /**
     * Wrapped text in the box from {@code top} down {@code height} points, at the largest size from
     * {@code max} down to {@code min} that fits. Returns the y just below the last line drawn.
     */
    private static float block(PdfContentByte cb, String value, BaseFont base, float max, float min, float height,
                               float left, float right, float top) throws Exception {
        if (value.isEmpty() || height <= 0) {
            return top;
        }
        float size = max;
        while (true) {
            float leading = size * 1.2f;
            ColumnText probe = column(cb, value, base, size, leading, left, right, top, height);
            if (!ColumnText.hasMoreText(probe.go(true)) || size <= min) {
                ColumnText ct = column(cb, value, base, size, leading, left, right, top, height);
                ct.go();
                return ct.getYLine();
            }
            size -= 0.5f;
        }
    }

    /**
     * Flows the address beside the QR code, then continues the same text full width below it.
     * Returns the y below the last line, or NaN if it did not all fit. With {@code simulate} nothing is drawn.
     */
    private float flowAddress(PdfContentByte cb, String value, float size, float top, float narrowRight,
                                float qrClear, float bottom, boolean simulate) throws Exception {
        float leading = size * 1.2f;
        ColumnText ct = new ColumnText(cb);
        ct.setLeading(leading);
        ct.addText(new Phrase(value, font(sans, size, BLACK)));
        float continueAt = top;
        if (top > qrClear) {
            ct.setSimpleColumn(CONTENT_LEFT, Math.max(qrClear, bottom), narrowRight, top);
            if (!ColumnText.hasMoreText(ct.go(simulate))) {
                return ct.getYLine();
            }
            continueAt = ct.getYLine();
        }
        if (continueAt - leading < bottom) {
            return Float.NaN;
        }
        ct.setSimpleColumn(CONTENT_LEFT, bottom, CONTENT_RIGHT, continueAt);
        return ColumnText.hasMoreText(ct.go(simulate)) ? Float.NaN : ct.getYLine();
    }

    private static ColumnText column(PdfContentByte cb, String value, BaseFont base, float size, float leading,
                                     float left, float right, float top, float height) {
        ColumnText ct = new ColumnText(cb);
        ct.setSimpleColumn(left, top - height, right, top);
        ct.setLeading(leading);
        ct.addText(new Phrase(value, font(base, size, BLACK)));
        return ct;
    }

    /** Vector QR code (error correction Q, 4-module quiet zone included in {@code size}). */
    static BitMatrix labelQrMatrix(String text) throws Exception {
        return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
                Map.of(EncodeHintType.MARGIN, 4, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q));
    }

    private static void drawQr(PdfContentByte cb, String text, float x, float y, float size) throws Exception {
        BitMatrix matrix = labelQrMatrix(text);
        int modules = matrix.getWidth();
        float m = size / modules;
        cb.setColorFill(BLACK);
        for (int row = 0; row < modules; row++) {
            for (int col = 0; col < modules; col++) {
                if (matrix.get(col, row)) {
                    cb.rectangle(x + col * m, y + size - (row + 1) * m, m, m);
                }
            }
        }
        cb.fill();
    }

    /** Province from the district (fixed administrative map), used only as a sorting hint. */
    private static String province(SriLankanDistrict district) {
        if (district == null) {
            return "";
        }
        return switch (district) {
            case COLOMBO, GAMPAHA, KALUTARA -> "Western Province";
            case KANDY, MATALE, NUWARA_ELIYA -> "Central Province";
            case GALLE, MATARA, HAMBANTOTA -> "Southern Province";
            case JAFFNA, KILINOCHCHI, MANNAR, VAVUNIYA, MULLAITIVU -> "Northern Province";
            case BATTICALOA, AMPARA, TRINCOMALEE -> "Eastern Province";
            case KURUNEGALA, PUTTALAM -> "North Western Province";
            case ANURADHAPURA, POLONNARUWA -> "North Central Province";
            case BADULLA, MONARAGALA -> "Uva Province";
            case RATNAPURA, KEGALLE -> "Sabaragamuwa Province";
        };
    }

    /** City, district and postal code on one line, without repeating a city named after its district. */
    private static String placeShort(String city, SriLankanDistrict district, String postalCode) {
        String d = districtName(district);
        String c = safe(city);
        String place = c.isEmpty() ? d : c.equalsIgnoreCase(d) || d.isEmpty() ? c : c + ", " + d;
        return has(postalCode) ? place + "  " + postalCode.trim() : place;
    }

    private static float mm(float value) {
        return value * 72f / 25.4f;
    }

    // ─── Page 2: booking receipt ──────────────────────────────────────────────

    private void buildBookingReceipt(Document doc, CustomerOrder order) throws Exception {
        doc.add(documentHeader("Booking receipt",
                "Keep this for your records. The label is on page 1."));

        doc.add(metaTable(new String[][]{
                {"Tracking number", safe(order.getTrackingNumber())},
                {"Booked", dateTime(order.getCreatedAt())},
                {"Service", speedLabel(order.getOrderSpeed())}
        }));

        doc.add(routeTable(order));

        // Fee table: one line per charge, then the total. Cash on delivery is not a fee, so it sits
        // below the total as information.
        PdfPTable fees = table(new float[]{3f, 1.3f});
        fees.setSpacingBefore(14);
        fees.addCell(headerCell("Description", Element.ALIGN_LEFT));
        fees.addCell(headerCell("Amount", Element.ALIGN_RIGHT));

        String currency = currency(order);
        BigDecimal estimated = order.getEstimatedFee();
        fees.addCell(lineCell("Delivery fee", speedLabel(order.getOrderSpeed()) + " service, billed as "
                + kg(order.getChargeableWeightKg())));
        fees.addCell(amountCell(currency, estimated, false));

        boolean revised = order.getFeeRevisionStatus() == FeeRevisionStatus.APPROVED
                && order.getFinalFee() != null && estimated != null
                && order.getFinalFee().compareTo(estimated) != 0;
        if (revised) {
            fees.addCell(lineCell("Adjusted at pickup", "Updated after the courier measured the parcel"));
            fees.addCell(amountCell(currency, order.getFinalFee().subtract(estimated), true));
        }

        BigDecimal total = revised ? order.getFinalFee() : estimated;
        PdfPCell totalLabel = cell(Rectangle.TOP, 10, 0);
        totalLabel.setBorderColor(INK);
        totalLabel.setBorderWidthTop(1.2f);
        totalLabel.addElement(new Paragraph(revised ? "Total delivery fee" : "Estimated delivery fee",
                font(sansBold, 10, INK)));
        fees.addCell(totalLabel);
        PdfPCell totalValue = cell(Rectangle.TOP, 10, 0);
        totalValue.setBorderColor(INK);
        totalValue.setBorderWidthTop(1.2f);
        Paragraph totalAmount = new Paragraph(currency + " " + money(total), font(sansBold, 13, INK));
        totalAmount.setAlignment(Element.ALIGN_RIGHT);
        totalValue.addElement(totalAmount);
        fees.addCell(totalValue);
        doc.add(fees);

        if (positive(order.getCodAmount()) || positive(order.getDeclaredValue())) {
            PdfPTable extras = table(new float[]{3f, 1.3f});
            extras.setSpacingBefore(10);
            if (positive(order.getCodAmount())) {
                extras.addCell(lineCell("Cash to collect from the recipient", "Paid to the courier on delivery, not a fee"));
                extras.addCell(amountCell(currency, order.getCodAmount(), false));
            }
            if (positive(order.getDeclaredValue())) {
                extras.addCell(lineCell("Declared value of the contents", null));
                extras.addCell(amountCell(currency, order.getDeclaredValue(), false));
            }
            doc.add(extras);
        }

        PdfPTable notes = table(new float[]{1f});
        notes.setSpacingBefore(16);
        PdfPCell box = cell(Rectangle.BOX, 10, 12);
        box.setBorderColor(RULE);
        box.setBackgroundColor(SURFACE);
        box.addElement(label("GOOD TO KNOW"));
        box.addElement(bullet("The courier checks the weight and size at pickup. If they differ, we ask you "
                + "before the fee changes."));
        if (Boolean.TRUE.equals(order.getProhibitedItemsDeclared())) {
            box.addElement(bullet("You confirmed at booking that the parcel has no prohibited, flammable, "
                    + "explosive or toxic items."));
        }
        box.addElement(bullet("Track this parcel at " + trackingSiteLabel + " with the number above."));
        notes.addCell(box);
        doc.add(notes);
    }

    // ─── Delivery receipt ─────────────────────────────────────────────────────

    private void buildDeliveryReceipt(Document doc, CustomerOrder order) throws Exception {
        boolean delivered = order.getStatus() == PublicOrderStatus.DELIVERED;
        doc.add(documentHeader("Delivery receipt",
                delivered ? "This parcel was delivered." : "This parcel has not been delivered yet."));

        PdfPTable statusRow = table(new float[]{1f});
        statusRow.setSpacingBefore(2);
        PdfPCell banner = cell(Rectangle.BOX, 9, 12);
        banner.setBorderColor(delivered ? new Color(187, 247, 208) : RULE);
        banner.setBackgroundColor(delivered ? SUCCESS_BG : SURFACE);
        Paragraph status = new Paragraph();
        status.add(new Chunk(delivered ? "DELIVERED" : statusLabel(order.getStatus()).toUpperCase(Locale.ENGLISH),
                font(sansBold, 11, delivered ? SUCCESS : INK)));
        status.add(new Chunk("   " + (delivered ? dateTime(order.getUpdatedAt()) : "Last update "
                + dateTime(order.getUpdatedAt())), font(sans, 9, BODY)));
        banner.addElement(status);
        statusRow.addCell(banner);
        doc.add(statusRow);

        doc.add(metaTable(new String[][]{
                {"Tracking number", safe(order.getTrackingNumber())},
                {"Booked", dateTime(order.getCreatedAt())},
                {"Received by", safe(order.getRecipientName())}
        }));

        doc.add(routeTable(order));

        String currency = currency(order);
        BigDecimal fee = order.getFinalFee() != null ? order.getFinalFee() : order.getEstimatedFee();
        PdfPTable money = table(new float[]{3f, 1.3f});
        money.setSpacingBefore(14);
        money.addCell(headerCell("Payment", Element.ALIGN_LEFT));
        money.addCell(headerCell("Amount", Element.ALIGN_RIGHT));
        money.addCell(lineCell("Delivery fee", speedLabel(order.getOrderSpeed()) + " service, billed as "
                + kg(order.getChargeableWeightKg())));
        money.addCell(amountCell(currency, fee, false));
        if (positive(order.getCodAmount())) {
            money.addCell(lineCell("Cash collected from the recipient", "Cash on delivery"));
            money.addCell(amountCell(currency, order.getCodAmount(), false));
        }
        doc.add(money);

        // Signatures and the QR side by side.
        PdfPTable bottom = table(new float[]{2.4f, 1f});
        bottom.setSpacingBefore(18);
        PdfPCell signatures = cell(Rectangle.NO_BORDER, 0, 0);
        signatures.addElement(signatureLine("Recipient signature"));
        signatures.addElement(signatureLine("Courier signature"));
        bottom.addCell(signatures);
        PdfPCell qrCell = cell(Rectangle.NO_BORDER, 0, 0);
        qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        Image qr = qr(trackingUrl(order), 74);
        qr.setAlignment(Image.ALIGN_CENTER);
        qrCell.addElement(qr);
        Paragraph caption = new Paragraph("Scan to view tracking", font(sans, 6.5f, MUTED));
        caption.setAlignment(Element.ALIGN_CENTER);
        qrCell.addElement(caption);
        bottom.addCell(qrCell);
        doc.add(bottom);
    }

    // ─── Shared blocks ────────────────────────────────────────────────────────

    private PdfPTable documentHeader(String title, String subtitle) {
        PdfPTable header = table(new float[]{1f});
        PdfPCell c = cell(Rectangle.BOTTOM, 0, 0);
        c.setBorderColor(INK);
        c.setBorderWidthBottom(1.5f);
        c.setPaddingBottom(12);
        Paragraph wordmark = new Paragraph();
        wordmark.add(new Chunk("Swift", font(sansBold, 13, INK)));
        wordmark.add(new Chunk("Drop", font(sansBold, 13, BRAND)));
        c.addElement(wordmark);
        Paragraph heading = new Paragraph(title, font(sansBold, 20, INK));
        heading.setSpacingBefore(10);
        c.addElement(heading);
        Paragraph note = new Paragraph(subtitle, font(sans, 8.5f, MUTED));
        note.setSpacingBefore(3);
        c.addElement(note);
        header.addCell(c);
        return header;
    }

    private PdfPTable metaTable(String[][] rows) {
        PdfPTable meta = table(new float[]{1f, 1f, 1f});
        meta.setSpacingBefore(12);
        for (String[] row : rows) {
            PdfPCell c = cell(Rectangle.NO_BORDER, 0, 0);
            c.addElement(label(row[0].toUpperCase(Locale.ENGLISH)));
            boolean code = row[0].startsWith("Tracking");
            Paragraph value = new Paragraph(row[1], code ? font(monoBold, 10.5f, INK) : font(sansBold, 9.5f, INK));
            value.setSpacingBefore(1);
            c.addElement(value);
            meta.addCell(c);
        }
        return meta;
    }

    /** From and to as city and district only: receipts carry no street addresses. */
    private PdfPTable routeTable(CustomerOrder order) {
        PdfPTable route = table(new float[]{1f, 0.18f, 1f});
        route.setSpacingBefore(12);
        route.addCell(place("FROM", order.getPickupCity(), order.getPickupDistrict()));
        PdfPCell arrow = cell(Rectangle.TOP | Rectangle.BOTTOM, 8, 0);
        arrow.setBorderColor(RULE);
        arrow.setBackgroundColor(SURFACE);
        arrow.setHorizontalAlignment(Element.ALIGN_CENTER);
        arrow.setVerticalAlignment(Element.ALIGN_MIDDLE);
        arrow.setPhrase(new Phrase("»", font(sansBold, 16, MUTED)));
        route.addCell(arrow);
        route.addCell(place("TO", order.getDeliveryCity(), order.getDeliveryDistrict()));
        return route;
    }

    private PdfPCell place(String heading, String city, SriLankanDistrict district) {
        PdfPCell c = cell(Rectangle.TOP | Rectangle.BOTTOM, 8, 10);
        c.setBorderColor(RULE);
        c.setBackgroundColor(SURFACE);
        c.addElement(label(heading));
        Paragraph name = new Paragraph(safe(city), font(sansBold, 11, INK));
        name.setSpacingBefore(1);
        c.addElement(name);
        c.addElement(new Paragraph(districtName(district) + " district", font(sans, 8, BODY)));
        return c;
    }

    private PdfPCell headerCell(String text, int align) {
        PdfPCell c = cell(Rectangle.BOTTOM, 6, 0);
        c.setBorderColor(RULE);
        Paragraph p = new Paragraph(text.toUpperCase(Locale.ENGLISH), font(sansBold, 7, MUTED));
        p.setAlignment(align);
        c.addElement(p);
        return c;
    }

    private PdfPCell lineCell(String title, String detail) {
        PdfPCell c = cell(Rectangle.BOTTOM, 8, 0);
        c.setBorderColor(RULE);
        c.addElement(new Paragraph(title, font(sansBold, 9.5f, INK)));
        if (detail != null) {
            c.addElement(new Paragraph(detail, font(sans, 8, MUTED)));
        }
        return c;
    }

    private PdfPCell amountCell(String currency, BigDecimal amount, boolean signed) {
        PdfPCell c = cell(Rectangle.BOTTOM, 8, 0);
        c.setBorderColor(RULE);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        String sign = signed && amount != null && amount.signum() > 0 ? "+" : "";
        Paragraph p = new Paragraph(currency + " " + sign + money(amount), font(sans, 9.5f, INK));
        p.setAlignment(Element.ALIGN_RIGHT);
        c.addElement(p);
        return c;
    }

    private Paragraph bullet(String text) {
        Paragraph p = new Paragraph();
        p.add(new Chunk("•  ", font(sansBold, 8.5f, BRAND)));
        p.add(new Chunk(text, font(sans, 8.5f, BODY)));
        p.setSpacingBefore(4);
        p.setLeading(11.5f);
        p.setIndentationLeft(8);
        p.setFirstLineIndent(-8);
        return p;
    }

    private Paragraph signatureLine(String caption) {
        Paragraph p = new Paragraph();
        p.setSpacingBefore(30);
        p.add(new Chunk("_____________________________________", font(sans, 9, MUTED)));
        p.add(Chunk.NEWLINE);
        p.add(new Chunk(caption, font(sans, 7.5f, MUTED)));
        return p;
    }

    private Paragraph label(String text) {
        return new Paragraph(text, font(sansBold, 6.5f, MUTED));
    }

    // ─── Rendering plumbing ───────────────────────────────────────────────────

    @FunctionalInterface
    private interface PageBuilder {
        void build(Document doc, PdfWriter writer) throws Exception;
    }

    private byte[] render(CustomerOrder order, String kind, String title, int pages, PageBuilder builder) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A5, MARGIN, MARGIN, MARGIN, MARGIN + FOOTER_SPACE);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new Footer(order.getTrackingNumber(), pages));
            document.addTitle(title + " " + safe(order.getTrackingNumber()));
            document.addAuthor("SwiftDrop");
            document.addCreator("SwiftDrop customer-order-service");
            document.open();
            builder.build(document, writer);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate {} PDF for order {}", kind, order.getOrderId(), e);
            throw new RuntimeException("Error generating " + kind + " PDF", e);
        }
    }

    /** Thin footer on every page: tracking number on the left, page count on the right. */
    private final class Footer extends PdfPageEventHelper {
        private final String trackingNumber;
        private final int pages;

        private Footer(String trackingNumber, int pages) {
            this.trackingNumber = safe(trackingNumber);
            this.pages = pages;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            Rectangle page = document.getPageSize();
            float y = MARGIN;
            canvas.saveState();
            canvas.setColorStroke(RULE);
            canvas.setLineWidth(0.6f);
            canvas.moveTo(MARGIN, y + 12);
            canvas.lineTo(page.getWidth() - MARGIN, y + 12);
            canvas.stroke();
            canvas.restoreState();
            Font small = font(sans, 7, MUTED);
            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                    new Phrase("SwiftDrop  ·  " + trackingNumber, small), MARGIN, y, 0);
            ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                    new Phrase("Page " + writer.getPageNumber() + " of " + pages, small),
                    page.getWidth() - MARGIN, y, 0);
        }
    }

    private static PdfPTable table(float[] widths) {
        PdfPTable t = new PdfPTable(widths.length);
        t.setWidthPercentage(100);
        try {
            t.setWidths(widths);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        return t;
    }

    private static PdfPCell cell(int border, float paddingY, float paddingX) {
        PdfPCell c = new PdfPCell();
        c.setBorder(border);
        c.setPaddingTop(paddingY);
        c.setPaddingBottom(paddingY + 2);
        c.setPaddingLeft(paddingX);
        c.setPaddingRight(paddingX);
        c.setUseAscender(true);
        return c;
    }

    private static Font font(BaseFont base, float size, Color color) {
        return new Font(base, size, Font.NORMAL, color);
    }

    private Image qr(String text, float size) throws Exception {
        // Rendered large and scaled down, so the modules stay sharp when printed.
        Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.MARGIN, 1,
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 600, 600, hints);
        Image image = Image.getInstance(png(matrix));
        image.scaleAbsolute(size, size);
        return image;
    }

    private static byte[] png(BitMatrix matrix) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        }
    }

    // ─── Formatting ───────────────────────────────────────────────────────────

    private static String money(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private static String currency(CustomerOrder order) {
        return has(order.getCurrency()) ? order.getCurrency() : "LKR";
    }

    private static String kg(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString() + " kg";
    }

    private static String dimensions(CustomerOrder order) {
        return number(order.getLengthCm()) + " x " + number(order.getWidthCm()) + " x " + number(order.getHeightCm());
    }

    private static String number(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private static String dateTime(Instant instant) {
        return instant == null ? "-" : DATE_TIME.format(instant);
    }

    private static String date(Instant instant) {
        return instant == null ? "-" : DATE.format(instant);
    }

    private static String speedLabel(OrderSpeed speed) {
        if (speed == null) {
            return "Standard";
        }
        return switch (speed) {
            case STANDARD -> "Standard";
            case EXPRESS -> "Express";
            case SAME_DAY -> "Same day";
        };
    }

    private static String statusLabel(PublicOrderStatus status) {
        return status == null ? "Booked" : titleCase(status.name());
    }

    /** {@code NUWARA_ELIYA} becomes {@code Nuwara Eliya}. */
    private static String districtName(SriLankanDistrict district) {
        return district == null ? "" : titleCase(district.name());
    }

    private static String titleCase(String constant) {
        return Arrays.stream(constant.toLowerCase(Locale.ENGLISH).split("_"))
                .filter(part -> !part.isEmpty())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    /** {@code +94771234567} becomes {@code +94 77 123 4567}. */
    private static String formatPhone(String phone) {
        String digits = phone.replaceAll("\\s", "");
        if (digits.matches("^\\+94\\d{9}$")) {
            return "+94 " + digits.substring(3, 5) + " " + digits.substring(5, 8) + " " + digits.substring(8);
        }
        if (digits.matches("^0\\d{9}$")) {
            return digits.substring(0, 3) + " " + digits.substring(3, 6) + " " + digits.substring(6);
        }
        return phone;
    }

    private static String addressLines(String line1, String line2) {
        return has(line2) ? safe(line1) + ", " + line2.trim() : safe(line1);
    }

    private static String cityLine(String city, String postalCode) {
        return has(postalCode) ? safe(city) + "  " + postalCode.trim() : safe(city);
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private static boolean has(String value) {
        return value != null && !value.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
