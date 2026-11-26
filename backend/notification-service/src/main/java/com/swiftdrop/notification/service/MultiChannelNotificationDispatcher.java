package com.swiftdrop.notification.service;

import com.swiftdrop.notification.service.push.DevicePushService;
import com.swiftdrop.notification.service.push.PushMessages;

import com.swiftdrop.notification.client.ContactClient;
import com.swiftdrop.notification.domain.entity.NotificationDispatchLog;
import com.swiftdrop.notification.domain.entity.RecipientPreference;
import com.swiftdrop.notification.domain.repository.NotificationDispatchLogRepository;
import com.swiftdrop.notification.domain.repository.RecipientPreferenceRepository;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.dto.NotificationDispatchLogResponse;
import com.swiftdrop.notification.dto.RecipientContact;
import com.swiftdrop.notification.service.email.EmailHtmlRenderer;
import com.swiftdrop.notification.service.email.EmailSender;
import com.swiftdrop.notification.util.PiiMasker;
import com.swiftdrop.notification.util.SensitiveTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiChannelNotificationDispatcher {

    private final RecipientPreferenceRepository preferenceRepository;
    private final NotificationDispatchLogRepository logRepository;
    private final TemplateRendererService templateRenderer;
    private final ContactClient contactClient;
    private final EmailSender emailSender;
    private final EmailHtmlRenderer emailHtmlRenderer;
    private final DevicePushService devicePushService;

    /**
     * Executes omnichannel notification dispatch, verifying recipient preferences and logging the outcome.
     */
    @Transactional
    public NotificationDispatchLogResponse dispatch(DispatchNotificationRequest request) {
        log.info("Processing notification dispatch to recipient: {} channel: {} template: {}",
                request.recipientId(), request.channel(), request.templateCode());

        Optional<RecipientPreference> prefOpt = preferenceRepository.findByRecipientId(request.recipientId());
        RecipientPreference pref = prefOpt.orElse(null);

        String channel = request.channel().toUpperCase();
        // Account-security mail (one-time codes and sign-in alerts) is not optional: it ignores the
        // email-off preference. Only one-time codes are secret, so only they are redacted below.
        boolean security = SensitiveTemplates.isSecurity(request.templateCode());
        boolean sensitive = SensitiveTemplates.isSensitive(request.templateCode());
        boolean push = "PUSH".equals(channel);
        // Security mail ignores the email-off switch; it is never sent to a device at all (below).
        boolean isChannelEnabled = (security && !push) || isChannelEnabled(pref, channel);

        // Security mail goes only to the account's registered (IAM) address: never to a request-supplied
        // or preference address, which could otherwise redirect a password-reset code to someone else.
        String destination = security
                ? registeredEmail(channel, request.recipientId())
                : resolveDestination(request.destination(), pref, channel, request.recipientId());

        // Render template
        TemplateRendererService.RenderedNotification rendered = templateRenderer.renderTemplate(
                request.templateCode(),
                request.templateParams() != null ? request.templateParams() : Map.of()
        );

        String status;
        String failureReason = null;
        Instant sentAt = null;

        if (push && security) {
            // One-time codes and sign-in alerts stay email-only: a lock screen is visible to anyone nearby.
            status = "SUPPRESSED";
            failureReason = "Security messages are never sent to devices";
        } else if (!isChannelEnabled) {
            log.info("Notification suppressed by user preferences for channel: {}", channel);
            status = "SUPPRESSED";
            failureReason = "Channel " + channel + " disabled in user preferences";
        } else if (recentlyAlerted(request)) {
            // Stops a stream of wrong passwords from flooding the owner's inbox.
            log.info("Failed sign-in alert for {} already sent in the last {} minutes; not repeating",
                    request.recipientId(), FAILED_LOGIN_ALERT_WINDOW.toMinutes());
            status = "SUPPRESSED";
            failureReason = "A failed sign-in alert was already sent in the last "
                    + FAILED_LOGIN_ALERT_WINDOW.toMinutes() + " minutes";
        } else if (recognisedSignIn(request)) {
            // Signing in again from the same device and network is routine: alert only for a new one.
            log.info("Sign-in for {} from a device seen in the last {} days; no alert", request.recipientId(),
                    KNOWN_DEVICE_WINDOW.toDays());
            status = "SUPPRESSED";
            failureReason = "Sign-in from a device used in the last " + KNOWN_DEVICE_WINDOW.toDays() + " days";
        } else if (destination == null || destination.isBlank()) {
            log.warn("Notification dispatch failed: Missing destination for channel {}", channel);
            status = "FAILED";
            failureReason = "No valid " + channel + " destination address or phone provided";
        } else {
            // EMAIL goes through the configured provider (mock or Azure); PUSH is the in-app inbox.
            try {
                executeDelivery(channel, destination, request, rendered);
                status = "SENT";
                sentAt = Instant.now();
                log.info("Notification successfully dispatched to {} via {}", PiiMasker.maskDestination(destination), channel);
            } catch (RuntimeException e) {
                // Graceful degradation: a provider failure is logged as FAILED for this channel only.
                log.error("Failed to transmit notification via {}: {}", channel, e.getMessage());
                status = "FAILED";
                failureReason = e.getMessage();
            }
        }

        NotificationDispatchLog dispatchLog = NotificationDispatchLog.builder()
                .logId(UUID.randomUUID())
                .recipientId(request.recipientId())
                .channel(channel)
                .templateCode(request.templateCode())
                .destination(destination != null ? destination : "UNRESOLVED")
                .subject(rendered.subject())
                // Never persist a one-time code in the audit log.
                .content(sensitive ? SensitiveTemplates.REDACTED_CONTENT : rendered.body())
                .status(status)
                .failureReason(failureReason)
                .sentAt(sentAt)
                .referenceId(request.referenceId())
                .build();

        NotificationDispatchLog saved = logRepository.save(dispatchLog);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<NotificationDispatchLogResponse> listLogs(UUID recipientId, String channel, String status) {
        List<NotificationDispatchLog> logs;
        if (recipientId != null) {
            if (channel != null && !channel.isBlank()) {
                logs = logRepository.findByRecipientIdAndChannelOrderByCreatedAtDesc(recipientId, channel.toUpperCase());
            } else if (status != null && !status.isBlank()) {
                logs = logRepository.findByRecipientIdAndStatusOrderByCreatedAtDesc(recipientId, status.toUpperCase());
            } else {
                logs = logRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
            }
        } else if (status != null && !status.isBlank()) {
            logs = logRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        } else {
            logs = logRepository.findAllByOrderByCreatedAtDesc();
        }

        return logs.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public Optional<NotificationDispatchLogResponse> getLog(UUID logId) {
        return logRepository.findByLogId(logId).map(this::mapToResponse);
    }

    private boolean isChannelEnabled(RecipientPreference pref, String channel) {
        if (pref == null) return true; // Default enabled
        return switch (channel) {
            case "EMAIL" -> pref.isEmailEnabled();
            case "PUSH" -> pref.isPushEnabled();
            default -> true;
        };
    }

    /** At most one failed-sign-in alert per account in this window. */
    static final Duration FAILED_LOGIN_ALERT_WINDOW = Duration.ofMinutes(15);

    private boolean recentlyAlerted(DispatchNotificationRequest request) {
        if (!SensitiveTemplates.LOGIN_FAILED.equalsIgnoreCase(request.templateCode()) || request.recipientId() == null) {
            return false;
        }
        return logRepository.existsByRecipientIdAndTemplateCodeAndStatusAndCreatedAtAfter(
                request.recipientId(), SensitiveTemplates.LOGIN_FAILED, "SENT", Instant.now().minus(FAILED_LOGIN_ALERT_WINDOW));
    }

    /** A successful sign-in from a device and network seen within this window sends no alert. */
    static final Duration KNOWN_DEVICE_WINDOW = Duration.ofDays(30);

    private boolean recognisedSignIn(DispatchNotificationRequest request) {
        if (!SensitiveTemplates.LOGIN_SUCCESS.equalsIgnoreCase(request.templateCode())
                || request.recipientId() == null || request.referenceId() == null) {
            return false;
        }
        // Any earlier sign-in from this device counts (alerted or already recognised), except one whose
        // alert failed to send, so the window slides while the device stays in use.
        return logRepository.existsByRecipientIdAndTemplateCodeAndReferenceIdAndStatusNotAndCreatedAtAfter(
                request.recipientId(), SensitiveTemplates.LOGIN_SUCCESS, request.referenceId(), "FAILED",
                Instant.now().minus(KNOWN_DEVICE_WINDOW));
    }

    private String registeredEmail(String channel, UUID recipientId) {
        if (!"EMAIL".equals(channel) || contactClient == null || recipientId == null) {
            return null;
        }
        return contactClient.lookupContact(recipientId).map(RecipientContact::email).orElse(null);
    }

    private String resolveDestination(String requestDest, RecipientPreference pref, String channel, UUID recipientId) {
        if (requestDest != null && !requestDest.isBlank()) {
            return requestDest.trim();
        }
        if (pref != null) {
            String prefDestination = switch (channel) {
                case "EMAIL" -> pref.getEmailAddress();
                case "PUSH" -> pref.getRecipientId().toString();
                default -> null;
            };
            if (prefDestination != null && !prefDestination.isBlank()) {
                return prefDestination.trim();
            }
        }
        if ("PUSH".equals(channel) && recipientId != null) {
            return recipientId.toString();
        }
        if (contactClient != null && recipientId != null) {
            Optional<RecipientContact> contactOpt = contactClient.lookupContact(recipientId);
            if (contactOpt.isPresent() && "EMAIL".equals(channel)) {
                return contactOpt.get().email();
            }
        }
        return null;
    }

    private void executeDelivery(String channel, String destination, DispatchNotificationRequest request,
                                 TemplateRendererService.RenderedNotification rendered) {
        switch (channel) {
            case "EMAIL" -> {
                // Email sanity check before handing the message to the provider.
                if (!destination.contains("@")) {
                    throw new IllegalArgumentException("Invalid email destination format: " + PiiMasker.maskDestination(destination));
                }
                String trackingNumber = trackingNumberOf(request);
                emailSender.send(destination, rendered.subject(),
                        emailHtmlRenderer.renderText(request.templateCode(), rendered.body(), trackingNumber,
                                request.templateParams()),
                        emailHtmlRenderer.renderHtml(request.templateCode(), rendered.subject(), rendered.body(), trackingNumber,
                                request.templateParams()));
            }
            case "PUSH" -> {
                // The in-app inbox row is written by the event consumer; this sends the device (Web Push) copy.
                if (devicePushService == null || !devicePushService.enabled()) {
                    return;
                }
                PushMessages.PushMessage message = PushMessages.forTemplate(request.templateCode(), request.templateParams())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "No device message for template " + request.templateCode()));
                DevicePushService.PushResult result = devicePushService.push(request.recipientId(), message);
                log.info("Device push for {}: {} device(s), {} delivered, {} removed, {} failed", request.recipientId(),
                        result.devices(), result.delivered(), result.removed(), result.failed());
                if (result.delivered() == 0 && result.failed() > 0) {
                    throw new IllegalStateException("No device accepted the message");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported channel " + channel + "; use EMAIL or PUSH");
        }
    }

    private static String trackingNumberOf(DispatchNotificationRequest request) {
        if (SensitiveTemplates.isSecurity(request.templateCode())) {
            return null; // the reference of a security mail is never a tracking number
        }
        if (request.templateParams() != null) {
            String fromParams = request.templateParams().get("trackingNumber");
            if (fromParams != null && !fromParams.isBlank()) {
                return fromParams.trim();
            }
        }
        return request.referenceId();
    }

    private NotificationDispatchLogResponse mapToResponse(NotificationDispatchLog entity) {
        return new NotificationDispatchLogResponse(
                entity.getLogId(),
                entity.getRecipientId(),
                entity.getChannel(),
                entity.getTemplateCode(),
                entity.getDestination(),
                entity.getSubject(),
                entity.getContent(),
                entity.getStatus(),
                entity.getFailureReason(),
                entity.getSentAt(),
                entity.getReferenceId(),
                entity.getCreatedAt()
        );
    }
}
