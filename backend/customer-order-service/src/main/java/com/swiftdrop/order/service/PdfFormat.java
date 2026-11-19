package com.swiftdrop.order.service;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Text formatting for order PDFs: money, weights, dates, addresses and labels. */
final class PdfFormat {

    static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH).withZone(COLOMBO);
    static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(COLOMBO);

    private PdfFormat() {
    }

    static String money(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }

    static String currency(CustomerOrder order) {
        return has(order.getCurrency()) ? order.getCurrency() : "LKR";
    }

    static String kg(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString() + " kg";
    }

    static String dimensions(CustomerOrder order) {
        return number(order.getLengthCm()) + " x " + number(order.getWidthCm()) + " x " + number(order.getHeightCm());
    }

    static String number(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    static String dateTime(Instant instant) {
        return instant == null ? "-" : DATE_TIME.format(instant);
    }

    static String date(Instant instant) {
        return instant == null ? "-" : DATE.format(instant);
    }

    static String speedLabel(OrderSpeed speed) {
        if (speed == null) {
            return "Standard";
        }
        return switch (speed) {
            case STANDARD -> "Standard";
            case EXPRESS -> "Express";
            case SAME_DAY -> "Same day";
        };
    }

    static String statusLabel(PublicOrderStatus status) {
        return status == null ? "Booked" : titleCase(status.name());
    }

    /** {@code NUWARA_ELIYA} becomes {@code Nuwara Eliya}. */
    static String districtName(SriLankanDistrict district) {
        return district == null ? "" : titleCase(district.name());
    }

    static String titleCase(String constant) {
        return Arrays.stream(constant.toLowerCase(Locale.ENGLISH).split("_"))
                .filter(part -> !part.isEmpty())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    /** {@code +94771234567} becomes {@code +94 77 123 4567}. */
    static String formatPhone(String phone) {
        String digits = phone.replaceAll("\\s", "");
        if (digits.matches("^\\+94\\d{9}$")) {
            return "+94 " + digits.substring(3, 5) + " " + digits.substring(5, 8) + " " + digits.substring(8);
        }
        if (digits.matches("^0\\d{9}$")) {
            return digits.substring(0, 3) + " " + digits.substring(3, 6) + " " + digits.substring(6);
        }
        return phone;
    }

    static String addressLines(String line1, String line2) {
        return has(line2) ? safe(line1) + ", " + line2.trim() : safe(line1);
    }

    static String cityLine(String city, String postalCode) {
        return has(postalCode) ? safe(city) + "  " + postalCode.trim() : safe(city);
    }

    static boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    static boolean has(String value) {
        return value != null && !value.isBlank();
    }

    /** Province from the district (fixed administrative map), used only as a sorting hint. */
    static String province(SriLankanDistrict district) {
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
    static String placeShort(String city, SriLankanDistrict district, String postalCode) {
        String d = districtName(district);
        String c = safe(city);
        String place = c.isEmpty() ? d : c.equalsIgnoreCase(d) || d.isEmpty() ? c : c + ", " + d;
        return has(postalCode) ? place + "  " + postalCode.trim() : place;
    }
}
