package com.swiftdrop.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingContactMaskerTest {

    @Test
    @DisplayName("A name becomes initials, each followed by a dot")
    void multipleWordsBecomeInitials() {
        assertThat(TrackingContactMasker.maskName("Nimal Perera")).isEqualTo("N. P.");
        assertThat(TrackingContactMasker.maskName("kamal de silva")).isEqualTo("K. D. S.");
    }

    @Test
    @DisplayName("A single-word name gives one initial")
    void oneWordGivesOneInitial() {
        assertThat(TrackingContactMasker.maskName("Nimal")).isEqualTo("N.");
    }

    @Test
    @DisplayName("Extra spaces around and between words are ignored")
    void extraSpacesAreIgnored() {
        assertThat(TrackingContactMasker.maskName("  Nimal    Perera  ")).isEqualTo("N. P.");
        assertThat(TrackingContactMasker.maskName("Nimal\tPerera")).isEqualTo("N. P.");
    }

    @Test
    @DisplayName("Sinhala and Tamil names keep their first character")
    void nonLatinNamesKeepFirstCharacter() {
        assertThat(TrackingContactMasker.maskName("නිමල් පෙරේරා")).isEqualTo("න. ප.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   \t "})
    @DisplayName("A blank or missing name gives null")
    void blankNameGivesNull(String name) {
        assertThat(TrackingContactMasker.maskName(name)).isNull();
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
            "0771234567, 07X***4567",
            "+94771234567, 07X***4567",
            "0714445556, 07X***5556",
            "077 123 4567, 07X***4567",
            "077-123-4567, 07X***4567"
    })
    @DisplayName("A full Sri Lankan number keeps its first two and last four digits")
    void fullNumberIsMasked(String phone, String expected) {
        assertThat(TrackingContactMasker.maskPhone(phone)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "077123", "+9477123", "12345678901234"})
    @DisplayName("A short, blank or malformed number gives ***")
    void shortOrBlankNumberGivesStars(String phone) {
        assertThat(TrackingContactMasker.maskPhone(phone)).isEqualTo("***");
    }

    @Test
    @DisplayName("The masked values never contain the raw name or number")
    void maskedValuesHideTheOriginal() {
        assertThat(TrackingContactMasker.maskName("Secret Sender")).doesNotContain("ecret", "ender");
        assertThat(TrackingContactMasker.maskPhone("0771112223")).doesNotContain("77111", "1112");
    }
}
