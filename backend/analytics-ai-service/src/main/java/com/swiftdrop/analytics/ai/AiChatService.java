package com.swiftdrop.analytics.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.analytics.ai.prompts.SystemPromptTemplates;
import com.swiftdrop.analytics.ai.tools.ToolExecutor;
import com.swiftdrop.analytics.dto.*;
import com.swiftdrop.contracts.security.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class AiChatService {
    private final LlmClient llm;
    private final ToolExecutor tools;
    private final ChatSessionStore sessions;
    private final ObjectMapper mapper;

    public AiChatService(LlmClient llm, ToolExecutor tools, ChatSessionStore sessions, ObjectMapper mapper) {
        this.llm = llm; this.tools = tools; this.sessions = sessions; this.mapper = mapper;
    }

    public AiChatResponse chat(AiChatRequest request, ToolRequestContext context) {
        if (!"CUSTOMER".equals(context.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The order assistant is available to customers only");
        }
        if (request.message() == null || request.message().isBlank() || request.message().length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must contain 1 to 2000 characters");
        }
        var session = sessions.acquire(context.userId(), request.conversationId());
        synchronized (session) {
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", SystemPromptTemplates.DEFAULT));
            messages.addAll(session.history);
            String input = SafeText.redact(request.message());
            messages.add(Map.of("role", "user", "content", input));
            List<AiChatResponse.ToolUse> uses = new ArrayList<>();
            String reply;
            if (SafeText.requestsPrivateData(request.message())) {
                tools.audit("chat", context, "REFUSED", 0);
                reply = "I cannot disclose private customer information or credentials. I can track a parcel or estimate delivery costs.";
            } else {
                reply = run(messages, uses, context);
            }
            reply = SafeText.redact(reply);
            session.history.add(Map.of("role", "user", "content", input));
            session.history.add(Map.of("role", "assistant", "content", reply));
            while (session.history.size() > 16) session.history.subList(0, 2).clear();
            return new AiChatResponse(reply, session.id, List.copyOf(uses));
        }
    }

    private String run(List<Map<String, Object>> messages, List<AiChatResponse.ToolUse> uses, ToolRequestContext context) {
        for (int round = 0; round < 4; round++) {
            LlmClient.Turn turn;
            try { turn = llm.complete(List.copyOf(messages)); }
            catch (RuntimeException ex) {
                tools.audit("chat", context, "PROVIDER_UNAVAILABLE", 0);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI provider unavailable; please try again later");
            }
            if (turn.toolCalls().isEmpty()) {
                if (uses.isEmpty()) tools.audit("chat", context, "NO_TOOL", 0);
                return turn.reply();
            }
            List<Map<String, Object>> protocolCalls = turn.toolCalls().stream().map(call -> Map.<String, Object>of(
                    "id", call.id(), "type", "function", "function", Map.of("name", call.name(), "arguments",
                            call.arguments() == null ? "null" : call.arguments().toString()))).toList();
            messages.add(Map.of("role", "assistant", "content", "", "tool_calls", protocolCalls));
            for (var call : turn.toolCalls()) {
                String safeName = Set.of("track_parcel", "calculate_estimate").contains(call.name()) ? call.name() : "DISALLOWED_TOOL";
                if (uses.size() >= 3) {
                    tools.audit(safeName, context, "LIMIT_REACHED", 0);
                    return "I reached the limit of three tool calls. Please narrow your question.";
                }
                var result = tools.execute(call, context);
                String outcome = (String) result.get("outcome");
                uses.add(new AiChatResponse.ToolUse(safeName, outcome));
                if ("REFUSED".equals(outcome)) return "I can only track a parcel or calculate a delivery estimate.";
                try {
                    messages.add(Map.of("role", "tool", "tool_call_id", call.id(), "content", mapper.writeValueAsString(result)));
                } catch (Exception ex) { throw new IllegalStateException("Cannot encode safe tool result"); }
            }
        }
        tools.audit("chat", context, "LIMIT_REACHED", 0);
        return "I reached the limit of three tool calls. Please narrow your question.";
    }

    public static ToolRequestContext context(HttpServletRequest request) {
        CallerContext caller;
        try { caller = CallerContext.from(request::getHeader); }
        catch (CallerContextException ex) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verified gateway identity required"); }
        String correlation = request.getHeader(JwtHeaderConstants.X_CORRELATION_ID);
        if (correlation == null || !correlation.matches("[A-Za-z0-9._-]{1,100}")) correlation = UUID.randomUUID().toString();
        return new ToolRequestContext(caller.userId(), caller.role().name(), request.getHeader("Authorization"), correlation);
    }
}
