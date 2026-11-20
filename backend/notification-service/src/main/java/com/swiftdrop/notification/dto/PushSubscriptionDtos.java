package com.swiftdrop.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the device (Web Push) endpoints. */
public final class PushSubscriptionDtos {

    private PushSubscriptionDtos() {
    }

    @Schema(name = "PushPublicKeyResponse", description = "What a browser needs to subscribe to device notifications")
    public record PushPublicKeyResponse(
            @Schema(description = "False when the server has no VAPID keys: device notifications are unavailable")
            boolean enabled,
            @Schema(description = "VAPID public key (base64url, uncompressed P-256 point) for pushManager.subscribe; null when disabled",
                    example = "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U")
            String publicKey) {
    }

    /** Matches the browser's {@code PushSubscription.toJSON()}; other fields such as expirationTime are ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "PushSubscriptionRequest", description = "This device's push subscription, as returned by PushSubscription.toJSON()")
    public record PushSubscriptionRequest(
            @NotBlank @Size(max = 2048)
            @Schema(description = "Push service URL of this device", example = "https://fcm.googleapis.com/fcm/send/dpH5lCsTSSM:APA91bH")
            String endpoint,
            @NotNull @Valid
            @Schema(description = "The device's encryption keys")
            Keys keys) {

        @Schema(name = "PushSubscriptionKeys")
        public record Keys(
                @NotBlank @Size(max = 128)
                @Schema(description = "P-256 public key of the browser (base64url)")
                String p256dh,
                @NotBlank @Size(max = 64)
                @Schema(description = "Authentication secret (base64url, 16 bytes)")
                String auth) {
        }
    }

    @Schema(name = "PushUnsubscribeRequest", description = "The device to stop sending notifications to")
    public record PushUnsubscribeRequest(
            @NotBlank @Size(max = 2048)
            @Schema(description = "Push service URL of the device, from PushSubscription.endpoint")
            String endpoint) {
    }
}
