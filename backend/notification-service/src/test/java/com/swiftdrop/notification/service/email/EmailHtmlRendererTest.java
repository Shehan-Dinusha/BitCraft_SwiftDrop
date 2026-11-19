package com.swiftdrop.notification.service.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmailHtmlRendererTest {

    private final EmailHtmlRenderer renderer = new EmailHtmlRenderer("https://swiftdrop.lk/");

    @Test
    @DisplayName("Renders a complete branded email with headline, message, tracking box and button")
    void rendersBrandedLayout() {
        String html = renderer.renderHtml("PARCEL_CREATED", "SwiftDrop Order Confirmed",
                "Your parcel SD-LYWN-GQZ3 has been scheduled for pickup.", "SD-LYWN-GQZ3");

        assertThat(html)
                .startsWith("<!DOCTYPE html>")
                .contains("<title>SwiftDrop Order Confirmed</title>")
                .contains("Order confirmed")
                .contains("Your order is confirmed")
                .contains("Your parcel SD-LYWN-GQZ3 has been scheduled for pickup.")
                .contains("Tracking number")
                .contains("href=\"https://swiftdrop.lk/track/SD-LYWN-GQZ3\"")
                .contains("Track your parcel")
                .contains("background:#2563eb")
                .contains("Please do not reply")
                .doesNotContain("<img")
                .doesNotContain("%s");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "PARCEL_CREATED, Order confirmed, #3730a3",
            "PARCEL_PICKED_UP, Picked up, #075985",
            "PARCEL_OUT_FOR_DELIVERY, Out for delivery, #5b21b6",
            "PARCEL_DELIVERED, Delivered, #065f46",
            "PARCEL_FAILED, Delivery attempt failed, #991b1b",
            "SOMETHING_ELSE, Update, #334155"
    })
    @DisplayName("Each status uses its own badge label and Velocity status colour")
    void statusBadges(String templateCode, String badge, String colour) {
        String html = renderer.renderHtml(templateCode, "Subject", "Body", "SD-AAAA-BBBB");

        assertThat(html).contains(">" + badge + "</span>").contains("color:" + colour);
    }

    @Test
    @DisplayName("Escapes every dynamic value so message text cannot inject HTML")
    void escapesDynamicValues() {
        String html = renderer.renderHtml("PARCEL_FAILED", "<b>Subject</b>",
                "Reason: <script>alert('x')</script> & more", "SD-AAAA-BBBB");

        assertThat(html)
                .doesNotContain("<script>")
                .doesNotContain("<b>Subject</b>")
                .contains("&lt;script&gt;")
                .contains("&amp; more");
    }

    @Test
    @DisplayName("Unsafe or missing tracking numbers produce no tracking box or link")
    void unsafeTrackingNumber() {
        String unsafe = renderer.renderHtml("PARCEL_CREATED", "Subject", "Body", "\"><a href=x>");
        String missing = renderer.renderHtml("PARCEL_CREATED", "Subject", "Body", null);

        assertThat(unsafe).doesNotContain("Track your parcel").doesNotContain("Tracking number").doesNotContain("href=x");
        assertThat(missing).doesNotContain("Track your parcel").doesNotContain("Tracking number");
        assertThat(renderer.trackingUrl("\"><a href=x>")).isNull();
    }

    @Test
    @DisplayName("Without a valid public base URL the tracking number is shown but no button")
    void noButtonWithoutBaseUrl() {
        EmailHtmlRenderer noUrl = new EmailHtmlRenderer("not a url");

        String html = noUrl.renderHtml("PARCEL_CREATED", "Subject", "Body", "SD-AAAA-BBBB");

        assertThat(html).contains("SD-AAAA-BBBB").doesNotContain("Track your parcel");
        assertThat(noUrl.trackingUrl("SD-AAAA-BBBB")).isNull();
    }

    @Test
    @DisplayName("Plain-text alternative carries the message, tracking link and notice")
    void plainTextAlternative() {
        String text = renderer.renderText("Your parcel SD-AAAA-BBBB is out for delivery.", "SD-AAAA-BBBB");

        assertThat(text)
                .startsWith("Your parcel SD-AAAA-BBBB is out for delivery.")
                .contains("Track your parcel: https://swiftdrop.lk/track/SD-AAAA-BBBB")
                .contains("please do not reply");
    }
}
