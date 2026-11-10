package com.swiftdrop.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.UUID;

@Schema(description = "Delivery assistant message; omit conversationId to start a conversation")
public record AiChatRequest(
        @NotBlank @Size(max = 2000) @Schema(description = "Message, maximum 2000 characters", example = "How much to send 2 kg from Colombo to Kandy?") String message,
        @Schema(description = "Previously returned conversation UUID, owned by the authenticated caller") UUID conversationId) {}
