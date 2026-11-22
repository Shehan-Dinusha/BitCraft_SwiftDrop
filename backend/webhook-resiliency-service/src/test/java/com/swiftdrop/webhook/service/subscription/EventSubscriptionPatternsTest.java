package com.swiftdrop.webhook.service.subscription;

import com.swiftdrop.webhook.exception.InvalidEventSubscriptionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventSubscriptionPatternsTest {

    private static final String STATUS = "parcel.status.changed";
    private static final String CREATED = "order.created";

    @Test
    void exactPatternMatchesOnlyThatEventType() {
        var patterns = EventSubscriptionPatterns.parseForRegistration(STATUS);
        assertThat(patterns.matches(STATUS)).isTrue();
        assertThat(patterns.matches(CREATED)).isFalse();
        assertThat(patterns.matches("parcel.status.changed.extra")).isFalse();
        assertThat(patterns.matches("parcel.status")).isFalse();
    }

    @Test
    void prefixWildcardMatchesWholeSegmentsOnly() {
        var patterns = EventSubscriptionPatterns.parseForRegistration("parcel.*");
        assertThat(patterns.matches(STATUS)).isTrue();
        assertThat(patterns.matches("parcel.fee.revised")).isTrue();
        // The old startsWith matching treated "parcel" as a prefix of these.
        assertThat(patterns.matches("parcelx.created")).isFalse();
        assertThat(patterns.matches("parcel")).isFalse();
        assertThat(patterns.matches(CREATED)).isFalse();
    }

    @Test
    void loneStarMatchesEveryWellFormedEventType() {
        var patterns = EventSubscriptionPatterns.parseForRegistration("*");
        assertThat(patterns.matches(STATUS)).isTrue();
        assertThat(patterns.matches(CREATED)).isTrue();
        assertThat(patterns.matches("")).isFalse();
        assertThat(patterns.matches(null)).isFalse();
    }

    @Test
    void registrationTrimsDeduplicatesAndKeepsOrder() {
        var patterns = EventSubscriptionPatterns.parseForRegistration(" order.created , parcel.status.changed,order.created ");
        assertThat(patterns.canonical()).isEqualTo("order.created,parcel.status.changed");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",                              // empty
            "   ",                           // blank
            "parcel.status.changed,",        // trailing empty token
            "parcel.status.changed,,order.created", // empty token in the middle
            "parcel.#",                      // legacy RabbitMQ wildcard
            "parcel*",                       // star inside a segment
            "*.changed",                     // leading wildcard
            "Parcel.Status.Changed",         // wrong case
            "parcel..changed",               // empty segment
            "order.cancelled",               // well formed but not a supported event
            "invoice.*",                     // wildcard matching no supported event
            "parcel status changed"          // spaces
    })
    void registrationRejectsMalformedEmptyAndUnknownTokens(String raw) {
        assertThatThrownBy(() -> EventSubscriptionPatterns.parseForRegistration(raw))
                .isInstanceOf(InvalidEventSubscriptionException.class);
    }

    @Test
    void registrationNamesEveryRejectedToken() {
        // The valid token (order.created) is not listed among the rejected ones.
        assertThatThrownBy(() -> EventSubscriptionPatterns.parseForRegistration("parcel.#,order.created,,bogus"))
                .hasMessageStartingWith("Unsupported event subscription(s): parcel.#, <empty>, bogus. ");
    }

    @Test
    void storedValueWithInvalidTokensIgnoresThemInsteadOfMatchingEverything() {
        // Rows saved before validation existed. The old matcher turned "" and "parcel" into match-alls.
        var stored = EventSubscriptionPatterns.parseStored(",parcel, parcel.#,order.created");
        assertThat(stored.patterns()).containsExactly("parcel", "order.created");
        assertThat(stored.matches(CREATED)).isTrue();
        assertThat(stored.matches(STATUS)).isFalse();
    }

    @Test
    void storedEmptyOrNullValueMatchesNothing() {
        assertThat(EventSubscriptionPatterns.parseStored("").matches(STATUS)).isFalse();
        assertThat(EventSubscriptionPatterns.parseStored(null).matches(STATUS)).isFalse();
        assertThat(EventSubscriptionPatterns.parseStored(" , ").matches(STATUS)).isFalse();
    }

    @Test
    void repeatedTokensCollapseSoTheStoredValueStaysSmall() {
        String repeated = String.join(",", java.util.Collections.nCopies(30, STATUS)) + ",order.created";
        assertThat(EventSubscriptionPatterns.parseForRegistration(repeated).canonical())
                .isEqualTo("parcel.status.changed,order.created");
    }
}
