package com.swiftdrop.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Safe assistant reply and tool execution labels; conversations expire after 30 minutes of inactivity or restart")
public record AiChatResponse(
        @Schema(description = "Assistant reply") String reply,
        @Schema(description = "Conversation UUID for subsequent messages") UUID conversationId,
        @Schema(description = "Tool execution metadata, never arguments or raw results") List<ToolUse> toolCalls) {
    @Schema(description = "Tool execution metadata")
    public record ToolUse(@Schema(description = "Allowlisted tool name or DISALLOWED_TOOL") String name,
                          @Schema(description = "OK, INVALID_ARGUMENTS, NOT_FOUND, UNAVAILABLE, REFUSED or LIMIT_REACHED") String outcome) {}
}
