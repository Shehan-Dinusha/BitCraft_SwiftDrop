package com.swiftdrop.analytics.controller;

import com.swiftdrop.analytics.ai.AiChatService;
import com.swiftdrop.analytics.dto.*;
import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.openapi.OpenApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "DeliveryChat", description = "Customer-only order tracking and pricing assistant")
@SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
public class AiChatController {
    private final AiChatService chat;
    public AiChatController(AiChatService chat) { this.chat = chat; }

    @PostMapping("/chat")
    @Operation(summary = "Customer-only order assistant; at most three validated tool calls per request")
    public ApiResponse<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request, HttpServletRequest servlet) {
        return ApiResponse.success(chat.chat(request, AiChatService.context(servlet)), "Assistant reply");
    }
}
