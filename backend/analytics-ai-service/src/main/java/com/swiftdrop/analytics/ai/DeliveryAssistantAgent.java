package com.swiftdrop.analytics.ai;

import com.swiftdrop.analytics.dto.*;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.List;

/** Compatibility adapter retaining the legacy payload and using the safe chat pipeline. */
@Component
public class DeliveryAssistantAgent {
    private final AiChatService chat;
    public DeliveryAssistantAgent(AiChatService chat) { this.chat = chat; }

    public AssistantQueryResponse processQuery(AssistantQueryRequest request) {
        var servlet = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String message = request.query();
        if (request.contextTrackingNumber() != null && !request.contextTrackingNumber().isBlank()) {
            message += " Tracking number: " + request.contextTrackingNumber();
        }
        var result = chat.chat(new AiChatRequest(message, null), AiChatService.context(servlet));
        var names = result.toolCalls().stream().map(AiChatResponse.ToolUse::name).toList();
        String intent = names.contains("track_parcel") ? "TRACKING_STATUS"
                : names.contains("calculate_estimate") ? "PRICING_ESTIMATE" : "GENERAL_LOGISTICS_ASSISTANCE";
        return new AssistantQueryResponse(result.reply(), intent, 0.0, List.of(), names);
    }
}
