package com.swiftdrop.notification.service.email;

import com.swiftdrop.notification.util.SensitiveTemplates;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds the SwiftDrop notification email: one layout, with copy, colours and details chosen per
 * notification type.
 *
 * <ul>
 *   <li>Parcel emails show a short message, a delivery progress tracker and only the details that
 *       matter for that step. The tracking number appears once, in the details table.</li>
 *   <li>One-time-code emails (verification, password reset, sign-in) show the code once, in a code box.</li>
 *   <li>Unknown templates fall back to the rendered template text.</li>
 * </ul>
 *
 * <p>Email clients ignore most modern CSS, so the layout uses nested tables, inline styles,
 * web-safe fonts and no images. Every dynamic value is HTML-escaped. Colours come from the
 * SwiftDrop design tokens (brand {@code #2563eb} and the per-status colours).
 */
@Component
public class EmailHtmlRenderer {

    private static final Pattern SAFE_TRACKING_NUMBER = Pattern.compile("[A-Za-z0-9-]{4,40}");
    private static final Pattern SAFE_CODE = Pattern.compile("[0-9]{4,10}");
    private static final Pattern SAFE_MINUTES = Pattern.compile("[0-9]{1,3}");
    /** Invite links IAM builds: {@code <frontend>/invite#token=<43 url-safe chars>}. Anything else is dropped. */
    private static final Pattern SAFE_INVITE_URL =
            Pattern.compile("https?://[A-Za-z0-9.:-]+(/[A-Za-z0-9._~-]+)*/invite#token=[A-Za-z0-9_-]{43}");
    private static final ZoneId SRI_LANKA = ZoneId.of("Asia/Colombo");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    /** Trigger fallbacks that should not be shown as real details. */
    private static final java.util.Set<String> PLACEHOLDERS = java.util.Set.of("our courier", "your courier", "just now");

    private static final String BRAND = "#2563eb";
    private static final String[] STEPS = {"Confirmed", "Picked up", "Out for delivery", "Delivered"};

    /**
     * Per-type presentation.
     *
     * @param label     small label at the top of the card and in the badge
     * @param headline  main heading
     * @param lead      first paragraph (never contains the tracking number or code)
     * @param next      closing "what happens next" line
     * @param step      progress step reached (1-4), 0 for no tracker
     * @param failed    the last step failed
     */
    record Style(String label, String headline, String lead, String next, String accent, String tint,
                 String border, int step, boolean failed) {}

    private static final Style DEFAULT_STYLE = new Style("Update", "There is an update on your parcel", null,
            null, "#334155", "#f1f5f9", "#cbd5e1", 0, false);

    private static final Map<String, Style> STYLES = Map.ofEntries(
            Map.entry("PARCEL_CREATED", new Style("Order confirmed", "Your order is confirmed",
                    "Thank you for booking with SwiftDrop. We have received your order and will assign a courier to collect your parcel.",
                    "We will email you again when your parcel is picked up.",
                    "#3730a3", "#eef2ff", "#c7d2fe", 1, false)),
            Map.entry("PARCEL_PICKED_UP", new Style("Picked up", "Your parcel is on its way",
                    "Our courier has collected your parcel and it is now moving through the SwiftDrop network.",
                    "We will email you again when it is out for delivery.",
                    "#075985", "#e0f2fe", "#bae6fd", 2, false)),
            Map.entry("PARCEL_OUT_FOR_DELIVERY", new Style("Out for delivery", "Your parcel is out for delivery",
                    "Your parcel is with the courier and on the way to the delivery address.",
                    "Please make sure someone is available to receive it.",
                    "#5b21b6", "#f3e8ff", "#e9d5ff", 3, false)),
            Map.entry("PARCEL_DELIVERED", new Style("Delivered", "Your parcel has been delivered",
                    "Your parcel was delivered successfully.",
                    "Thank you for shipping with SwiftDrop.",
                    "#065f46", "#d1fae5", "#a7f3d0", 4, false)),
            Map.entry("PARCEL_FAILED", new Style("Delivery attempt failed", "We could not deliver your parcel",
                    "We were not able to complete the delivery of your parcel.",
                    "You can check the latest status in the SwiftDrop app.",
                    "#991b1b", "#fee2e2", "#fecaca", 4, true)),
            Map.entry("PARCEL_RETURNING", new Style("Coming back", "Your parcel is coming back to you",
                    "We could not deliver your parcel, so it is on its way back to you. The return is free.",
                    "A courier will bring it to your pickup address.",
                    "#92400e", "#fef3c7", "#fde68a", 0, false)),
            Map.entry("PARCEL_RETURNED", new Style("Returned", "Your parcel has been returned",
                    "Your parcel was handed back to you.",
                    "If you did not receive it, contact SwiftDrop.",
                    "#334155", "#f1f5f9", "#cbd5e1", 0, false)),
            Map.entry(SensitiveTemplates.EMAIL_VERIFICATION, new Style("Verify your email", "Confirm your email address",
                    "Use this code to verify the email address for your SwiftDrop account.",
                    "If you did not create a SwiftDrop account, you can ignore this email.",
                    "#3730a3", "#eef2ff", "#c7d2fe", 0, false)),
            Map.entry(SensitiveTemplates.PASSWORD_RESET, new Style("Password reset", "Reset your password",
                    "Use this code to reset your SwiftDrop password.",
                    "If you did not ask to reset your password, ignore this email. Your password will stay the same.",
                    "#92400e", "#fef3c7", "#fde68a", 0, false)),
            Map.entry(SensitiveTemplates.LOGIN_MFA, new Style("Sign-in code", "Confirm it's you signing in",
                    "Use this code to finish signing in to SwiftDrop.",
                    "If you were not trying to sign in, change your password straight away.",
                    "#3730a3", "#eef2ff", "#c7d2fe", 0, false)),
            Map.entry(SensitiveTemplates.STAFF_INVITE, new Style("Account invite", "Welcome to the SwiftDrop team",
                    "A SwiftDrop account has been created for you. Choose your password to sign in for the first time. "
                            + "Couriers then add their vehicle details.",
                    "If you were not expecting this email, you can ignore it. Nobody can use the account until the link is opened.",
                    "#1d4ed8", "#eff6ff", "#bfdbfe", 0, false)),
            Map.entry("MERCHANT_APPROVED", new Style("Business approved", "Your business account is approved",
                    "Your business is approved to ship with SwiftDrop. Sign in to your merchant portal to create an API key "
                            + "and connect your system.",
                    "Your orders are collected from the pickup address in your application.",
                    "#065f46", "#d1fae5", "#a7f3d0", 0, false)),
            Map.entry("MERCHANT_REJECTED", new Style("Application needs changes", "We could not approve your business yet",
                    "Your district team reviewed your application and could not approve it as it stands.",
                    "Sign in to your merchant portal, correct the details and submit the application again.",
                    "#92400e", "#fef3c7", "#fde68a", 0, false)),
            Map.entry("COURIER_ASSIGNED", new Style("New delivery", "You have a new delivery",
                    "A parcel has been assigned to you. Please collect it from the pickup address.",
                    "You can see the full pickup and delivery details in the SwiftDrop courier app.",
                    "#1d4ed8", "#eff6ff", "#bfdbfe", 0, false)),
            Map.entry(SensitiveTemplates.LOGIN_SUCCESS, new Style("New sign-in", "New sign-in to your account",
                    "Your SwiftDrop account was just signed in to.",
                    "If this was you, no action is needed. If you don't recognise it, reset your password straight away.",
                    "#065f46", "#d1fae5", "#a7f3d0", 0, false)),
            Map.entry(SensitiveTemplates.LOGIN_FAILED, new Style("Failed sign-in attempt", "Someone tried to sign in to your account",
                    "We stopped a sign-in to your SwiftDrop account because the password was wrong.",
                    "If this was you, you can ignore this email. If not, change your password to keep your account safe.",
                    "#991b1b", "#fee2e2", "#fecaca", 0, false)));

    private static final String PARCEL_REASON = "You are receiving this email because you have a parcel with SwiftDrop. "
            + "You can turn off parcel emails in your SwiftDrop notification settings.";
    private static final String ACCOUNT_REASON = "This security email was sent because of a request made with your "
            + "SwiftDrop account. Never share this code with anyone, including SwiftDrop staff.";
    private static final String INVITE_REASON = "This security email was sent because SwiftDrop operations created an "
            + "account for you. The link signs you in, so never share it with anyone, including SwiftDrop staff.";
    private static final String MERCHANT_REASON = "You are receiving this email because you applied for a SwiftDrop "
            + "business account.";
    private static final String SIGN_IN_REASON = "Sign-in alerts help keep your SwiftDrop account safe. They are always "
            + "sent and cannot be turned off.";
    private static final String COURIER_REASON = "You are receiving this email because you are a SwiftDrop courier. "
            + "You can turn off delivery emails in your SwiftDrop notification settings.";

    /** Plain-text alternative: the template text and the automated-message notice. */
    public String renderText(String body, String trackingNumber) {
        return (body == null ? "" : body.trim())
                + "\n\n--\nSwiftDrop Courier & Logistics. This is an automated message; please do not reply.";
    }

    public String renderHtml(String templateCode, String subject, String body, String trackingNumber) {
        return renderHtml(templateCode, subject, body, trackingNumber, Map.of());
    }

    public String renderHtml(String templateCode, String subject, String body, String trackingNumber,
                             Map<String, String> params) {
        String code = templateCode == null ? "" : templateCode.trim().toUpperCase(Locale.ROOT);
        Map<String, String> p = params == null ? Map.of() : params;
        boolean security = SensitiveTemplates.isSecurity(code);
        boolean oneTimeCode = SensitiveTemplates.isSensitive(code);
        Style known = STYLES.get(code);
        Style style = known == null ? DEFAULT_STYLE : known;

        String safeSubject = esc(subject == null || subject.isBlank() ? "SwiftDrop notification" : subject);
        // Known types use their own copy so the tracking number or code is not repeated; others keep the template text.
        String lead = known != null && known.lead() != null ? known.lead() : (body == null ? "" : body.trim());
        String tracking = trackingNumber != null && SAFE_TRACKING_NUMBER.matcher(trackingNumber).matches() ? trackingNumber : null;

        StringBuilder content = new StringBuilder();
        content.append(row("padding:28px 40px 0 40px;", badge(style)));
        content.append(row("padding:14px 40px 0 40px;font-size:24px;line-height:32px;font-weight:800;color:#0f172a;",
                esc(style.headline())));
        content.append(row("padding:10px 40px 0 40px;font-size:15px;line-height:24px;color:#334155;",
                esc(lead).replace("\n", "<br>")));
        if (SensitiveTemplates.STAFF_INVITE.equals(code)) {
            content.append(inviteButton(p.get("inviteUrl"), p.get("expiresInHours")));
        } else if (oneTimeCode) {
            content.append(codeBox(p.get("code"), p.get("expiresInMinutes")));
        } else {
            if (style.step() > 0) {
                content.append(progress(style));
            }
            content.append(details(code, tracking, p));
        }
        if (style.next() != null) {
            content.append(row("padding:20px 40px 0 40px;font-size:14px;line-height:22px;color:#475569;", esc(style.next())));
        }

        String preheader = esc(preheader(code, style, tracking));
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
                      <tr><td style="padding:20px 40px;border-bottom:1px solid #e2e8f0;">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"><tr>
                          <td style="vertical-align:middle;">
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr>
                              <td style="width:36px;height:36px;background:%3$s;border-radius:9px;text-align:center;vertical-align:middle;font-size:17px;line-height:36px;font-weight:800;color:#ffffff;">SD</td>
                              <td style="padding-left:10px;vertical-align:middle;">
                                <div style="font-size:17px;line-height:20px;font-weight:800;color:#0f172a;">SwiftDrop</div>
                                <div style="font-size:11px;line-height:14px;color:#64748b;">Courier &amp; Logistics</div>
                              </td>
                            </tr></table>
                          </td>
                          <td align="right" style="vertical-align:middle;font-size:12px;line-height:16px;color:#64748b;">%4$s</td>
                        </tr></table>
                      </td></tr>
                      <tr><td style="height:4px;line-height:4px;font-size:0;background:%5$s;">&nbsp;</td></tr>
                      %6$s
                      <tr><td style="padding:32px 40px 0 40px;"><div style="border-top:1px solid #e2e8f0;"></div></td></tr>
                      <tr><td style="padding:18px 40px 30px 40px;font-size:12px;line-height:18px;color:#64748b;">
                        %7$s<br><br>
                        This mailbox is not monitored. Please do not reply to this email.<br>
                        &copy; %8$s SwiftDrop &middot; Courier &amp; Logistics &middot; Sri Lanka
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
                </body>
                </html>
                """.formatted(safeSubject, preheader, BRAND, esc(security ? "Account security" : "COURIER_ASSIGNED".equals(code) ? "Delivery job"
                        : code.startsWith("MERCHANT_") ? "Business account" : "Parcel update"),
                style.accent(), content, esc(reason(code, security, oneTimeCode)), Year.now().getValue());
    }

    private static String reason(String code, boolean security, boolean oneTimeCode) {
        if (SensitiveTemplates.STAFF_INVITE.equals(code)) {
            return INVITE_REASON;
        }
        if (oneTimeCode) {
            return ACCOUNT_REASON;
        }
        if (security) {
            return SIGN_IN_REASON;
        }
        if (code.startsWith("MERCHANT_")) {
            return MERCHANT_REASON;
        }
        return "COURIER_ASSIGNED".equals(code) ? COURIER_REASON : PARCEL_REASON;
    }

    private static String preheader(String code, Style style, String tracking) {
        if (SensitiveTemplates.isSensitive(code) || tracking == null) {
            return style.headline();
        }
        return style.label() + ": " + tracking;
    }

    private static String row(String style, String html) {
        return "<tr><td style=\"" + style + "\">" + html + "</td></tr>\n";
    }

    private static String badge(Style style) {
        return "<span style=\"display:inline-block;padding:5px 12px;border-radius:999px;background:" + style.tint()
                + ";color:" + style.accent() + ";border:1px solid " + style.border()
                + ";font-size:12px;line-height:16px;font-weight:700;\">" + esc(style.label()) + "</span>";
    }

    /**
     * Four-segment progress bar with a label under each segment: completed steps in brand blue,
     * the current step in the type colour (red when delivery failed), upcoming steps in grey.
     * Plain table cells only, so every mail client renders it.
     */
    private static String progress(Style style) {
        StringBuilder cells = new StringBuilder();
        for (int i = 1; i <= STEPS.length; i++) {
            boolean current = i == style.step();
            boolean done = i < style.step();
            String bar = current ? (style.failed() ? "#dc2626" : style.accent()) : done ? BRAND : "#e2e8f0";
            String textColour = current ? (style.failed() ? "#dc2626" : "#0f172a") : done ? "#334155" : "#94a3b8";
            String mark = current && style.failed() ? "&#10005; " : (done || current) ? "&#10003; " : "";
            String text = current && style.failed() ? "Delivery failed" : STEPS[i - 1];
            String gap = i == STEPS.length ? "" : "padding-right:6px;";
            cells.append("<td width=\"25%\" valign=\"top\" style=\"").append(gap).append("\">")
                    .append("<div style=\"height:6px;line-height:6px;font-size:0;border-radius:3px;background:").append(bar).append(";\">&nbsp;</div>")
                    .append("<div style=\"padding-top:8px;font-size:12px;line-height:16px;color:").append(textColour)
                    .append(";font-weight:").append(current ? "700" : "500").append(";\">")
                    .append(mark).append(esc(text)).append("</div></td>");
        }
        return "<tr><td style=\"padding:24px 40px 0 40px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>"
                + cells + "</tr></table></td></tr>\n";
    }

    /** Only the details that matter for this step; the tracking number appears here and nowhere else. */
    private static String details(String code, String tracking, Map<String, String> p) {
        List<String[]> rows = new ArrayList<>();
        if (tracking != null) {
            rows.add(new String[]{"Tracking number", tracking, "mono"});
        }
        switch (code) {
            case "PARCEL_CREATED" -> {
                String from = district(p.get("pickupDistrict"));
                String to = district(p.get("deliveryDistrict"));
                if (from != null && to != null) {
                    rows.add(new String[]{"Route", from + " → " + to, null});
                }
                String speed = title(p.get("speed"));
                if (speed != null) {
                    rows.add(new String[]{"Service", speed, null});
                }
            }
            case "PARCEL_PICKED_UP", "PARCEL_OUT_FOR_DELIVERY" -> {
                String courier = clean(p.get("courierName"));
                if (courier != null && !PLACEHOLDERS.contains(courier.toLowerCase(Locale.ROOT))) {
                    rows.add(new String[]{"Courier", courier, null});
                }
            }
            case "PARCEL_DELIVERED" -> {
                String when = when(p.get("deliveredAt"));
                if (when != null) {
                    rows.add(new String[]{"Delivered on", when, null});
                }
            }
            case "COURIER_ASSIGNED" -> {
                String from = district(p.get("pickupDistrict"));
                String to = district(p.get("deliveryDistrict"));
                if (from != null && to != null) {
                    rows.add(new String[]{"Route", from + " → " + to, null});
                }
                String minutes = clean(p.get("estimatedPickupMinutes"));
                if (minutes != null && SAFE_MINUTES.matcher(minutes).matches() && !"0".equals(minutes)) {
                    rows.add(new String[]{"Pickup expected", "In about " + minutes + " minutes", null});
                }
            }
            case SensitiveTemplates.LOGIN_SUCCESS, SensitiveTemplates.LOGIN_FAILED -> {
                String when = clean(p.get("occurredAt"));
                if (when != null) {
                    rows.add(new String[]{"When", when, null});
                }
                String device = clean(p.get("device"));
                if (device != null) {
                    rows.add(new String[]{"Device", device.substring(0, 1).toUpperCase(Locale.ROOT) + device.substring(1), null});
                }
                String ip = clean(p.get("ipAddress"));
                if (ip != null) {
                    rows.add(new String[]{"IP address", ip, null});
                }
            }
            case "MERCHANT_APPROVED", "MERCHANT_REJECTED" -> {
                String business = clean(p.get("businessName"));
                if (business != null) {
                    rows.add(new String[]{"Business", business, null});
                }
                String district = clean(p.get("district"));
                if (district != null) {
                    rows.add(new String[]{"District", district, null});
                }
                String reason = clean(p.get("reason"));
                if ("MERCHANT_REJECTED".equals(code) && reason != null) {
                    rows.add(new String[]{"Reason", reason, null});
                }
            }
            case "PARCEL_RETURNED" -> {
                String when = clean(p.get("returnedAt"));
                if (when != null) {
                    rows.add(new String[]{"Returned", when, null});
                }
            }
            case "PARCEL_FAILED" -> {
                String reason = clean(p.get("failureReason"));
                if (reason != null) {
                    rows.add(new String[]{"Reason", reason.substring(0, 1).toUpperCase(Locale.ROOT) + reason.substring(1), null});
                }
            }
            default -> {
                // Other templates show the tracking number only.
            }
        }
        if (rows.isEmpty()) {
            return "";
        }
        StringBuilder table = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            String border = i == 0 ? "" : "border-top:1px solid #e2e8f0;";
            String valueFont = "mono".equals(r[2])
                    ? "font-family:Consolas,'Courier New',monospace;font-size:15px;letter-spacing:0.04em;font-weight:700;"
                    : "font-size:14px;font-weight:600;";
            table.append("<tr><td style=\"padding:12px 18px;").append(border)
                    .append("font-size:13px;line-height:18px;color:#64748b;width:38%;\">").append(esc(r[0])).append("</td>")
                    .append("<td style=\"padding:12px 18px;").append(border).append(valueFont)
                    .append("line-height:18px;color:#0f172a;\">").append(esc(r[1])).append("</td></tr>");
        }
        return "<tr><td style=\"padding:24px 40px 0 40px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;\">"
                + table + "</table></td></tr>\n";
    }

    private static String codeBox(String code, String minutes) {
        if (code == null || !SAFE_CODE.matcher(code).matches()) {
            return "";
        }
        String expiry = minutes != null && SAFE_MINUTES.matcher(minutes).matches()
                ? "<div style=\"font-size:13px;line-height:18px;color:#64748b;padding-top:6px;\">This code expires in "
                + esc(minutes) + " minutes</div>"
                : "";
        return "<tr><td style=\"padding:24px 40px 0 40px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;\"><tr><td align=\"center\" style=\"padding:22px;\">"
                + "<div style=\"font-size:12px;line-height:16px;color:#475569;text-transform:uppercase;letter-spacing:0.08em;font-weight:600;\">Your code</div>"
                + "<div style=\"font-family:Consolas,'Courier New',monospace;font-size:34px;line-height:44px;color:#0f172a;font-weight:800;letter-spacing:0.35em;padding-top:6px;\">"
                + esc(code) + "</div>" + expiry + "</td></tr></table></td></tr>\n";
    }

    /** The invite button, the same link as text for clients that block buttons, and the expiry. */
    private static String inviteButton(String url, String hours) {
        if (url == null || !SAFE_INVITE_URL.matcher(url).matches()) {
            return "";
        }
        String expiry = hours != null && SAFE_MINUTES.matcher(hours).matches()
                ? "The link works once and expires in " + esc(hours) + " hours."
                : "The link works once.";
        return "<tr><td align=\"center\" style=\"padding:26px 40px 0 40px;\">"
                + "<a href=\"" + esc(url) + "\" style=\"display:inline-block;padding:14px 28px;border-radius:10px;background:"
                + BRAND + ";color:#ffffff;font-size:15px;line-height:20px;font-weight:700;text-decoration:none;\">"
                + "Set up my account</a>"
                + "<div style=\"font-size:13px;line-height:18px;color:#64748b;padding-top:12px;\">" + expiry + "</div>"
                + "</td></tr>\n"
                + row("padding:16px 40px 0 40px;font-size:12px;line-height:18px;color:#64748b;word-break:break-all;",
                "Button not working? Copy this link into your browser:<br>" + esc(url));
    }

    /** {@code NUWARA_ELIYA} -> {@code Nuwara Eliya}. */
    private static String district(String value) {
        return title(value);
    }

    private static String title(String value) {
        String v = clean(value);
        if (v == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String word : v.replace('_', ' ').toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!word.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    /** ISO instants are shown in Sri Lanka time; any other text is shown as given. */
    private static String when(String value) {
        String v = clean(value);
        if (v == null || PLACEHOLDERS.contains(v.toLowerCase(Locale.ROOT))) {
            return null;
        }
        try {
            return WHEN.format(Instant.parse(v).atZone(SRI_LANKA)).replace("AM", "a.m.").replace("PM", "p.m.");
        } catch (DateTimeParseException e) {
            return v;
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name());
    }
}
