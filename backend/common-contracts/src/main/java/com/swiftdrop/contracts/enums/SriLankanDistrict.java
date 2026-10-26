package com.swiftdrop.contracts.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Locale;

/**
 * The 25 administrative districts of Sri Lanka used for pricing zones,
 * dispatch routing, and public tracking.
 */
@Schema(description = "Administrative district of Sri Lanka.", example = "COLOMBO")
public enum SriLankanDistrict {
    COLOMBO("Colombo"),
    GAMPAHA("Gampaha"),
    KALUTARA("Kalutara"),
    KANDY("Kandy"),
    MATALE("Matale"),
    NUWARA_ELIYA("Nuwara Eliya"),
    GALLE("Galle"),
    MATARA("Matara"),
    HAMBANTOTA("Hambantota"),
    JAFFNA("Jaffna"),
    KILINOCHCHI("Kilinochchi"),
    MANNAR("Mannar"),
    VAVUNIYA("Vavuniya"),
    MULLAITIVU("Mullaitivu"),
    BATTICALOA("Batticaloa"),
    AMPARA("Ampara"),
    TRINCOMALEE("Trincomalee"),
    KURUNEGALA("Kurunegala"),
    PUTTALAM("Puttalam"),
    ANURADHAPURA("Anuradhapura"),
    POLONNARUWA("Polonnaruwa"),
    BADULLA("Badulla"),
    MONARAGALA("Monaragala"),
    RATNAPURA("Ratnapura"),
    KEGALLE("Kegalle");

    private final String displayName;

    SriLankanDistrict(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @JsonCreator
    public static SriLankanDistrict fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalised = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        for (SriLankanDistrict district : values()) {
            if (district.name().equals(normalised)) {
                return district;
            }
        }
        return null;
    }
}
