package com.swiftdrop.notification.service;

import com.swiftdrop.notification.domain.entity.NotificationTemplate;
import com.swiftdrop.notification.domain.repository.NotificationTemplateRepository;
import com.swiftdrop.notification.util.PiiMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateRendererService {

    private static final Pattern UNKNOWN_PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{[^}]+\\}\\}");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b");
    private static final Pattern SRI_LANKAN_PHONE_PATTERN = Pattern.compile("\\b(?:(?:\\+?94)|0)?7\\d{8}\\b");

    private static final Set<String> FORBIDDEN_SECRET_KEYS = Set.of(
            "password", "passwd", "pin", "cvv", "cvc", "secret", "authtoken", "auth_token", "jwt", "privatekey", "private_key"
    );

    private static final Set<String> FORBIDDEN_CARD_KEYS = Set.of(
            "cardnumber", "card_number", "creditcard", "credit_card", "debitcard", "debit_card", "pan"
    );

    private static final Set<String> FORBIDDEN_ADDRESS_KEYS = Set.of(
            "fulladdress", "full_address", "streetaddress", "street_address", "addressline", "address_line",
            "addressline1", "address_line1", "addressline2", "address_line2"
    );

    private static final Set<String> PHONE_KEYS = Set.of(
            "phone", "phonenumber", "phone_number", "mobile", "mobilenumber", "mobile_number",
            "contact", "contactnumber", "contact_number", "recipientphone", "recipient_phone",
            "senderphone", "sender_phone"
    );

    private final NotificationTemplateRepository templateRepository;

    public record RenderedNotification(String subject, String body, String channel) {}

    /**
     * Resolves the template by code, then interpolates parameters into subject and body.
     */
    public RenderedNotification renderTemplate(String templateCode, Map<String, String> params) {
        Optional<NotificationTemplate> templateOpt = templateRepository.findByTemplateCodeAndActiveTrue(templateCode);

        String subjectTmpl;
        String bodyTmpl;
        String channel;

        if (templateOpt.isPresent()) {
            NotificationTemplate tmpl = templateOpt.get();
            subjectTmpl = tmpl.getSubjectTemplate() != null ? tmpl.getSubjectTemplate() : "SwiftDrop Notification";
            bodyTmpl = tmpl.getBodyTemplate();
            channel = tmpl.getChannel();
        } else {
            // Default fallbacks for operational resilience
            channel = "SMS";
            switch (templateCode) {
                case "PARCEL_OUT_FOR_DELIVERY" -> {
                    subjectTmpl = "SwiftDrop Out for Delivery";
                    bodyTmpl = "Parcel {{trackingNumber}} is out for delivery with {{courierName}}. Delivery expected shortly.";
                }
                case "PARCEL_DELIVERED" -> {
                    channel = "EMAIL";
                    subjectTmpl = "Parcel Delivered - SwiftDrop";
                    bodyTmpl = "Your parcel {{trackingNumber}} was successfully delivered at {{deliveredAt}}. Thank you for choosing SwiftDrop.";
                }
                case "PARCEL_PICKED_UP" -> {
                    subjectTmpl = "SwiftDrop Parcel Picked Up";
                    bodyTmpl = "Courier {{courierName}} has picked up parcel {{trackingNumber}}. In-transit to distribution hub.";
                }
                case "PARCEL_FAILED" -> {
                    subjectTmpl = "SwiftDrop Delivery Exception";
                    bodyTmpl = "Delivery attempt failed for parcel {{trackingNumber}}: {{failureReason}}.";
                }
                default -> {
                    subjectTmpl = "SwiftDrop Notification";
                    bodyTmpl = "Status update for parcel {{trackingNumber}}: {{status}}.";
                }
            }
        }

        String renderedSubject = interpolate(subjectTmpl, params);
        String renderedBody = interpolate(bodyTmpl, params);

        return new RenderedNotification(renderedSubject, renderedBody, channel);
    }

    /**
     * Replaces {{paramKey}} occurrences with matching safe value from params map.
     * Guarantees that:
     * 1. Passwords, card numbers, and full addresses are never output.
     * 2. Phone numbers are masked (07X***5678).
     * 3. Unknown placeholders are replaced with empty string.
     */
    public String interpolate(String template, Map<String, String> params) {
        if (template == null) return "";

        String result = template;
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String rawKey = entry.getKey();
                String normalizedKey = rawKey != null ? rawKey.trim().toLowerCase().replaceAll("[_-]", "") : "";
                String value = entry.getValue() != null ? entry.getValue() : "";

                // Never expose passwords or secrets
                if (FORBIDDEN_SECRET_KEYS.contains(normalizedKey) || normalizedKey.contains("password") || normalizedKey.contains("secret")) {
                    value = "";
                }
                // Never expose payment cards
                else if (FORBIDDEN_CARD_KEYS.contains(normalizedKey) || normalizedKey.contains("cardnumber") || normalizedKey.contains("creditcard")) {
                    value = "";
                }
                // Never expose full street address
                else if (FORBIDDEN_ADDRESS_KEYS.contains(normalizedKey) || normalizedKey.contains("fulladdress") || normalizedKey.contains("streetaddress")) {
                    value = "";
                }
                // Always mask phone numbers
                else if (PHONE_KEYS.contains(normalizedKey) || normalizedKey.contains("phone")) {
                    value = PiiMasker.maskPhone(value);
                }

                String placeholder = "{{" + rawKey + "}}";
                result = result.replace(placeholder, value);
            }
        }

        // Unknown / unmapped placeholders render empty
        result = UNKNOWN_PLACEHOLDER_PATTERN.matcher(result).replaceAll("");

        // Post-processing safety filter: redact leaked payment card numbers
        result = CREDIT_CARD_PATTERN.matcher(result).replaceAll("");

        // Post-processing safety filter: mask any full phone numbers that may have slipped into general fields
        result = maskFullPhonesInText(result);

        return result;
    }

    private String maskFullPhonesInText(String text) {
        Matcher matcher = SRI_LANKAN_PHONE_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String match = matcher.group();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(PiiMasker.maskPhone(match)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
