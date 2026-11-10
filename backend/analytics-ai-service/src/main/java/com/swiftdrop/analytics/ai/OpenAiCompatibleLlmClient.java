package com.swiftdrop.analytics.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.analytics.ai.tools.ToolExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.*;

/** Supports Azure OpenAI v1 and OpenAI Chat Completions. */
public class OpenAiCompatibleLlmClient implements LlmClient {
    private final JsonHttpClient http;
    private final ObjectMapper mapper;
    private final String endpoint;
    private final String key;
    private final String model;

    public OpenAiCompatibleLlmClient(ObjectMapper mapper, String endpoint, String key, String model) {
        this.mapper = mapper;
        this.http = new JsonHttpClient(mapper, Duration.ofSeconds(20));
        this.endpoint = endpoint.replaceAll("/+$", "") + "/chat/completions";
        this.key = key;
        this.model = model;
    }

    @Override
    public Turn complete(List<Map<String, Object>> messages) {
        try {
            var root = http.request("POST", endpoint, Map.of("Authorization", "Bearer " + key),
                    Map.of("model", model, "messages", messages, "tools", ToolExecutor.definitions(),
                            "tool_choice", "auto", "parallel_tool_calls", false, "max_completion_tokens", 600));
            var choice = root.path("choices").path(0);
            if (!Set.of("stop", "tool_calls").contains(choice.path("finish_reason").asText())) throw new IllegalArgumentException();
            var message = choice.path("message");
            List<ToolCall> calls = new ArrayList<>();
            var rawCalls = message.path("tool_calls");
            if (rawCalls.size() > 3) throw new IllegalArgumentException();
            Set<String> ids = new HashSet<>();
            for (var call : rawCalls) {
                String id = call.path("id").asText();
                if (!id.matches("[A-Za-z0-9_-]{1,128}") || !ids.add(id)) throw new IllegalArgumentException();
                calls.add(new ToolCall(id, call.path("function").path("name").asText(),
                        mapper.readTree(call.path("function").path("arguments").asText())));
            }
            String reply = message.path("content").asText("");
            if (calls.isEmpty() && reply.isBlank()) throw new IllegalArgumentException();
            return new Turn(reply, List.copyOf(calls));
        } catch (Exception ex) {
            // Provider responses/exceptions can contain credentials, prompts or model text.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI provider unavailable; please try again later");
        }
    }
}
