package com.swiftdrop.notification.service;

import com.swiftdrop.notification.domain.entity.NotificationTemplate;
import com.swiftdrop.notification.domain.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateRendererService {

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
     * Replaces {{paramKey}} occurrences with matching value from params map.
     */
    public String interpolate(String template, Map<String, String> params) {
        if (template == null) return "";
        if (params == null || params.isEmpty()) return template;

        String result = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
