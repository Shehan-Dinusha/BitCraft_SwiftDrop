package com.swiftdrop.contracts.logging;

/**
 * Makes caller-controlled values safe to write into a log line.
 *
 * <p>Request paths, exception messages, validation details, file names and identifiers can all
 * carry text chosen by the caller. A carriage return or line feed in such a value would let a
 * caller start a new, forged log entry (log injection, CWE-117). {@link #clean(Object)} escapes
 * both characters so the value always stays on its own line and remains readable.
 *
 * <p>Use it on every caller-controlled argument of a log statement; it is not needed for
 * constants, enums or the {@code Throwable} argument (stack traces are rendered by the logger).
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    /**
     * Returns {@code String.valueOf(value)} with every {@code \r} and {@code \n} escaped as the
     * two-character sequences {@code \\r} and {@code \\n}. {@code null} becomes {@code "null"}.
     */
    public static String clean(Object value) {
        String text = String.valueOf(value);
        return text.replace("\r", "\\r").replace("\n", "\\n");
    }
}
