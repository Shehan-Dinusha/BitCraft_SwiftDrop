package com.swiftdrop.notification.service;

import com.swiftdrop.notification.domain.entity.NotificationDispatchLog;
import com.swiftdrop.notification.domain.entity.RecipientPreference;
import com.swiftdrop.notification.domain.repository.NotificationDispatchLogRepository;
import com.swiftdrop.notification.domain.repository.RecipientPreferenceRepository;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.dto.NotificationDispatchLogResponse;
import com.swiftdrop.notification.exception.GlobalExceptionHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        boolean isChannelEnabled = isChannelEnabled(pref, channel);

        // Resolve destination
        String destination = resolveDestination(request.destination(), pref, channel);

        // Render template
        TemplateRendererService.RenderedNotification rendered = templateRenderer.renderTemplate(
                request.templateCode(),
                request.templateParams() != null ? request.templateParams() : Map.of()
        );

        String status;
        String failureReason = null;
        Instant sentAt = null;

        if (!isChannelEnabled) {
            log.info("Notification suppressed by user preferences for channel: {}", channel);
            status = "SUPPRESSED";
            failureReason = "Channel " + channel + " disabled in user preferences";
        } else if (destination == null || destination.isBlank()) {
            log.warn("Notification dispatch failed: Missing destination for channel {}", channel);
            status = "FAILED";
            failureReason = "No valid " + channel + " destination address or phone provided";
        } else {
            // Mock delivery execution (SMS / Email / Push adapter)
            try {
                executeMockDelivery(channel, destination, rendered.subject(), rendered.body());
                status = "SENT";
                sentAt = Instant.now();
                log.info("Notification successfully dispatched to {} via {}", destination, channel);
            } catch (Exception e) {
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
                .content(rendered.body())
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
            case "SMS" -> pref.isSmsEnabled();
            case "EMAIL" -> pref.isEmailEnabled();
            case "PUSH" -> pref.isPushEnabled();
            default -> true;
        };
    }

    private String resolveDestination(String requestDest, RecipientPreference pref, String channel) {
        if (requestDest != null && !requestDest.isBlank()) {
            return requestDest.trim();
        }
        if (pref != null) {
            return switch (channel) {
                case "SMS" -> pref.getPhoneNumber();
                case "EMAIL" -> pref.getEmailAddress();
                case "PUSH" -> pref.getRecipientId().toString();
                default -> null;
            };
        }
        return null;
    }

    private void executeMockDelivery(String channel, String destination, String subject, String body) {
        if ("SMS".equalsIgnoreCase(channel)) {
            // E.164 phone sanity check
            if (!destination.matches("^\\+?[0-9]{9,15}$")) {
                throw new IllegalArgumentException("Invalid phone number format for SMS: " + destination);
            }
        } else if ("EMAIL".equalsIgnoreCase(channel)) {
            // Email sanity check
            if (!destination.contains("@")) {
                throw new IllegalArgumentException("Invalid email destination format: " + destination);
            }
        }
        // Transmission successful simulated
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
