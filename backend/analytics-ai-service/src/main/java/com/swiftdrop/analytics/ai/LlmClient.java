package com.swiftdrop.analytics.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/** Provider boundary. Messages follow the Chat Completions tool-call protocol. */
public interface LlmClient {
    Turn complete(List<Map<String, Object>> messages);

    record ToolCall(String id, String name, JsonNode arguments) {}
    record Turn(String reply, List<ToolCall> toolCalls) {
        public static Turn answer(String text) { return new Turn(text, List.of()); }
    }
}
