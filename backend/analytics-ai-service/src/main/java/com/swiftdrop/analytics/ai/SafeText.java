package com.swiftdrop.analytics.ai;

/** Defense in depth; tool results primarily use typed field allowlists. */
public final class SafeText {
    private SafeText() {}

    public static String redact(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*", "[REDACTED]")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[REDACTED]")
                .replaceAll("(?<![A-Za-z0-9-])\\+?\\d[\\d ()-]{8,}\\d(?![A-Za-z0-9-])", "[REDACTED]")
                .replaceAll("(?i)\\b(?:no\\.?\\s*)?\\d+[A-Za-z/]?\\s+(?:[A-Za-z]+\\s+){0,5}(?:street|road|lane|avenue|drive)\\b", "[REDACTED]");
    }

    public static boolean requestsPrivateData(String text) {
        return text.toLowerCase(java.util.Locale.ROOT).matches(
                "(?s).*(phone|telephone|contact details|full address|home address|customer data|api.?key|password|system prompt|list customers|all customers).*" );
    }
}
