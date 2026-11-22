package com.swiftdrop.contracts.phone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SriLankanPhoneTest {

    @ParameterizedTest
    @ValueSource(strings = {"+94771234567", "0771234567", "771234567"})
    void acceptsTheThreeFormsAndStoresOne(String value) {
        assertTrue(SriLankanPhone.isValid(value));
        assertEquals("+94771234567", SriLankanPhone.toE164(value));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "", " ", "77123456", "7712345678", "077123456", "07712345678", "+9477123456", "+947712345678",
            "94771234567", "0094771234567", "+940771234567", "+94 77 123 4567", "077-123-4567", "(077)1234567",
            "+1771234567", "077123456a", "000000000", "٠٧٧١٢٣٤٥٦٧"})
    void rejectsEverythingElse(String value) {
        assertFalse(SriLankanPhone.isValid(value));
        assertThrows(IllegalArgumentException.class, () -> SriLankanPhone.toE164(value));
    }

    @Test
    void optionalNumberMayBeEmpty() {
        assertNull(SriLankanPhone.toE164OrNull(null));
        assertNull(SriLankanPhone.toE164OrNull(" "));
        assertEquals("+94112345678", SriLankanPhone.toE164OrNull("0112345678"));
    }

    @Test
    void oldStoredNumbersStayUsable() {
        assertEquals("+94112345678", SriLankanPhone.storedToE164("+94 11 234 5678"));
        assertEquals("not a number", SriLankanPhone.storedToE164("not a number"));
        assertNull(SriLankanPhone.storedToE164(null));
    }
}
