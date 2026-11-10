package com.swiftdrop.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Base64;

/**
 * Minimal, unverified JWS header used only to discover the signing algorithm
 * and key id before signature verification. Never trust these values for
 * authorization decisions until the token has been verified.
 */
public record JwsHeader(String algorithm, String keyId) {

    public static JwsHeader decode(String token, ObjectMapper objectMapper) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token is empty");
        }
        int firstDot = token.indexOf('.');
        if (firstDot <= 0) {
            throw new IllegalArgumentException("Token is not a compact JWS");
        }
        try {
            byte[] headerBytes = Base64.getUrlDecoder().decode(token.substring(0, firstDot));
            JsonNode header = objectMapper.readTree(headerBytes);
            return new JwsHeader(textOrNull(header, "alg"), textOrNull(header, "kid"));
        } catch (IllegalArgumentException | IOException exception) {
            throw new IllegalArgumentException("Malformed JWS header", exception);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
