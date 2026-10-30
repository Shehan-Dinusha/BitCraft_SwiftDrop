package com.swiftdrop.order.dto;

/**
 * Shared Jakarta validation patterns and messages for order input. Messages are fixed strings so a
 * rejected value is never echoed back to the caller.
 *
 * <p>Address, name and city fields use an allow-list of letters (any script), digits, spaces and a few
 * punctuation marks, which excludes HTML and SQL metacharacters such as {@code < > = ; "}. Queries are
 * parameterised, so this is defence in depth, not the only protection.</p>
 */
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    /** Street / building lines. */
    public static final String ADDRESS = "^[\\p{L}\\p{M}\\p{N} .,/#()&'’:-]*$";
    public static final String ADDRESS_MESSAGE = "Contains characters that are not allowed";

    /** Person names. */
    public static final String NAME = "^[\\p{L}\\p{M} .'’-]*$";
    public static final String NAME_MESSAGE = "Contains characters that are not allowed";

    /** City names may contain digits, for example "Colombo 03". */
    public static final String CITY = "^[\\p{L}\\p{M}\\p{N} .'’-]*$";
    public static final String CITY_MESSAGE = "Contains characters that are not allowed";

    /** Sri Lankan 5-digit postal code. */
    public static final String POSTAL_CODE = "^\\d{5}$";
    public static final String POSTAL_CODE_MESSAGE = "Must be a 5-digit postal code";

    /** Local (0771234567) or international (+94771234567) mobile or landline number. */
    public static final String PHONE = "^(0\\d{9}|\\+94\\d{9})$";
    public static final String PHONE_MESSAGE = "Must be a valid Sri Lankan phone number";

    /** Free text such as item description or cancellation reason: anything except angle brackets. */
    public static final String FREE_TEXT = "^[^<>]*$";
    public static final String FREE_TEXT_MESSAGE = "Contains characters that are not allowed";

    /** Pricing rule version identifier, for example PRICING-2026-V1. */
    public static final String RULE_VERSION = "^[A-Za-z0-9._-]*$";
    public static final String RULE_VERSION_MESSAGE = "Contains characters that are not allowed";

    public static final String WEIGHT_MIN = "0.01";
    public static final String WEIGHT_MAX = "50";
    public static final String WEIGHT_MIN_MESSAGE = "Weight must be at least 0.01 kg";
    public static final String WEIGHT_MAX_MESSAGE = "Weight must not exceed 50 kg";
}
