package com.swiftdrop.analytics.ai;

import java.util.UUID;

/** Trusted identity comes from gateway headers, never from the prompt or legacy persona. */
public record ToolRequestContext(UUID userId, String role, String authorization, String correlationId) {}
