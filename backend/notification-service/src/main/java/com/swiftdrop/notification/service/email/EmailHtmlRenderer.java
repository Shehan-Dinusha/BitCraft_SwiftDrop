package com.swiftdrop.notification.service.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Wraps a rendered notification in the branded SwiftDrop email layout.
 *
 * <p>Email clients ignore most modern CSS, so the layout uses nested tables, inline styles,
 * web-safe fonts and no external images. Every dynamic value is HTML-escaped. Colours come
 * from the Velocity design tokens (brand {@code #2563eb} and the per-status FSM colours).
 */
@Component
public class EmailHtmlRenderer {

    private static final Pattern SAFE_TRACKING_NUMBER = Pattern.compile("[A-Za-z0-9-]{4,40}");

    /** Badge label, headline and colours (background, foreground, border) per template. */
    record StatusStyle(String badge, String headline, String bg, String fg, String border) {}

    private static final StatusStyle DEFAULT_STYLE =
            new StatusStyle("Update", "There is an update on your parcel", "#f1f5f9", "#334155", "#cbd5e1");

    private static final Map<String, StatusStyle> STYLES = Map.of(
            "PARCEL_CREATED", new StatusStyle("Order confirmed", "Your order is confirmed", "#eef2ff", "#3730a3", "#c7d2fe"),
            "PARCEL_PICKED_UP", new StatusStyle("Picked up", "Your parcel has been picked up", "#e0f2fe", "#075985", "#bae6fd"),
            "PARCEL_OUT_FOR_DELIVERY", new StatusStyle("Out for delivery", "Your parcel is out for delivery", "#f3e8ff", "#5b21b6", "#e9d5ff"),
            "PARCEL_DELIVERED", new StatusStyle("Delivered", "Your parcel has been delivered", "#d1fae5", "#065f46", "#a7f3d0"),
            "PARCEL_FAILED", new StatusStyle("Delivery attempt failed", "We could not deliver your parcel", "#fee2e2", "#991b1b", "#fecaca"));

    private final String trackingUrlPrefix;

    public EmailHtmlRenderer(@Value("${swiftdrop.public-tracking-base-url:https://swiftdrop.lk}") String publicTrackingBaseUrl) {
        String base = publicTrackingBaseUrl == null ? "" : publicTrackingBaseUrl.trim().replaceAll("/+$", "");
        this.trackingUrlPrefix = base.matches("(?i)https?://\\S+") ? base + "/track/" : null;
    }

    /** Public tracking link for the parcel, or {@code null} when it cannot be built safely. */
    public String trackingUrl(String trackingNumber) {
        if (trackingUrlPrefix == null || trackingNumber == null || !SAFE_TRACKING_NUMBER.matcher(trackingNumber).matches()) {
            return null;
        }
        return trackingUrlPrefix + URLEncoder.encode(trackingNumber, StandardCharsets.UTF_8);
    }

    /** Plain-text alternative: the message, the tracking link and the automated-message notice. */
    public String renderText(String body, String trackingNumber) {
        StringBuilder text = new StringBuilder(body == null ? "" : body.trim());
        String url = trackingUrl(trackingNumber);
        if (url != null) {
            text.append("\n\nTrack your parcel: ").append(url);
        }
        text.append("\n\n--\nSwiftDrop. This is an automated message; please do not reply.");
        return text.toString();
    }

    public String renderHtml(String templateCode, String subject, String body, String trackingNumber) {
        StatusStyle style = STYLES.getOrDefault(templateCode == null ? "" : templateCode, DEFAULT_STYLE);
        String safeSubject = esc(subject == null || subject.isBlank() ? "SwiftDrop notification" : subject);
        String safeBody = esc(body == null ? "" : body.trim()).replace("\n", "<br>");
        String url = trackingUrl(trackingNumber);
        boolean showTracking = trackingNumber != null && SAFE_TRACKING_NUMBER.matcher(trackingNumber).matches();

        String trackingBlock = !showTracking ? "" : """
                <tr><td style="padding:0 40px 8px 40px;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;">
                    <tr><td style="padding:16px 20px;">
                      <div style="font-size:12px;line-height:16px;color:#475569;text-transform:uppercase;letter-spacing:0.08em;font-weight:600;">Tracking number</div>
                      <div style="font-family:Consolas,'Courier New',monospace;font-size:20px;line-height:28px;color:#0f172a;font-weight:700;letter-spacing:0.04em;padding-top:4px;">%s</div>
                    </td></tr>
                  </table>
                </td></tr>
                """.formatted(esc(trackingNumber));

        String buttonBlock = url == null ? "" : """
                <tr><td style="padding:24px 40px 8px 40px;">
                  <table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr>
                    <td style="border-radius:8px;background:#2563eb;">
                      <a href="%s" target="_blank" style="display:inline-block;padding:13px 28px;font-size:15px;line-height:20px;font-weight:700;color:#ffffff;text-decoration:none;border-radius:8px;">Track your parcel &rarr;</a>
                    </td>
                  </tr></table>
                </td></tr>
                """.formatted(esc(url));

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="color-scheme" content="light">
                <title>%1$s</title>
                </head>
                <body style="margin:0;padding:0;background:#f1f5f9;-webkit-text-size-adjust:100%%;">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0;">%2$s</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#f1f5f9;">
                  <tr><td align="center" style="padding:32px 12px;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:600px;background:#ffffff;border:1px solid #e2e8f0;border-radius:14px;overflow:hidden;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
                      <tr><td style="background:#2563eb;padding:22px 40px;">
                        <span style="font-size:22px;line-height:28px;font-weight:800;color:#ffffff;letter-spacing:-0.01em;">Swift<span style="color:#bfdbfe;">Drop</span></span>
                        <span style="font-size:12px;line-height:28px;color:#dbeafe;padding-left:10px;">Courier &amp; Logistics</span>
                      </td></tr>
                      <tr><td style="padding:32px 40px 0 40px;">
                        <span style="display:inline-block;padding:5px 12px;border-radius:999px;background:%3$s;color:%4$s;border:1px solid %5$s;font-size:12px;line-height:16px;font-weight:700;">%6$s</span>
                      </td></tr>
                      <tr><td style="padding:16px 40px 0 40px;font-size:24px;line-height:32px;font-weight:800;color:#0f172a;">%7$s</td></tr>
                      <tr><td style="padding:12px 40px 24px 40px;font-size:15px;line-height:24px;color:#334155;">%2$s</td></tr>
                      %8$s%9$s
                      <tr><td style="padding:32px 40px 0 40px;"><div style="border-top:1px solid #e2e8f0;"></div></td></tr>
                      <tr><td style="padding:20px 40px 32px 40px;font-size:12px;line-height:18px;color:#64748b;">
                        You are receiving this email because you have a parcel with SwiftDrop. You can change
                        email notifications in your SwiftDrop notification preferences.<br><br>
                        This is an automated message. Please do not reply.<br>
                        &copy; %10$s SwiftDrop &middot; Sri Lanka
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
                </body>
                </html>
                """.formatted(safeSubject, safeBody, style.bg(), style.fg(), style.border(), esc(style.badge()),
                esc(style.headline()), trackingBlock, buttonBlock, Year.now().getValue());
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name());
    }
}
