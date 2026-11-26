package com.swiftdrop.notification.service.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class EmailHtmlRendererTest {

    private static final String TRK = "SD-WA9Q-25CC";

    private final EmailHtmlRenderer renderer = new EmailHtmlRenderer();

    private static int count(String html, String text) {
        Matcher m = Pattern.compile(Pattern.quote(text)).matcher(html);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** The visible email, without the hidden inbox-preview text at the top of the body. */
    private static String visible(String html) {
        int preview = html.indexOf("display:none");
        return html.substring(html.indexOf("</div>", preview));
    }

    @Test
    @DisplayName("Order confirmation: branded layout, tracking number shown once, route and service, no button")
    void orderConfirmation() {
        String html = renderer.renderHtml("PARCEL_CREATED", "SwiftDrop Order Confirmed",
                "Your parcel " + TRK + " has been scheduled for pickup. Track live on SwiftDrop.", TRK,
                Map.of("trackingNumber", TRK, "pickupDistrict", "COLOMBO", "deliveryDistrict", "NUWARA_ELIYA",
                        "speed", "EXPRESS"));

        assertThat(html)
                .startsWith("<!DOCTYPE html>")
                .contains("<title>SwiftDrop Order Confirmed</title>")
                .contains(">SwiftDrop<")
                .contains("Order confirmed")
                .contains("Your order is confirmed")
                .contains("Tracking number")
                .contains("Colombo → Nuwara Eliya")
                .contains(">Express<")
                .contains("Parcel update")
                .contains("Please do not reply")
                .doesNotContain("Track your parcel")
                .doesNotContain("Track live")
                .doesNotContain("href=")
                .doesNotContain("<img")
                .doesNotContain("%s");
        assertThat(count(visible(html), TRK)).as("tracking number appears once in the visible email").isEqualTo(1);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "PARCEL_CREATED, Order confirmed, Your order is confirmed, #3730a3",
            "PARCEL_PICKED_UP, Picked up, Your parcel is on its way, #075985",
            "PARCEL_OUT_FOR_DELIVERY, Out for delivery, Your parcel is out for delivery, #5b21b6",
            "PARCEL_DELIVERED, Delivered, Your parcel has been delivered, #065f46",
            "PARCEL_FAILED, Delivery attempt failed, We could not deliver your parcel, #991b1b",
            "SOMETHING_ELSE, Update, There is an update on your parcel, #334155"
    })
    @DisplayName("Each type has its own badge, headline and accent colour")
    void perTypeStyle(String templateCode, String badge, String headline, String colour) {
        String html = renderer.renderHtml(templateCode, "Subject", "Body", TRK, Map.of());

        assertThat(html).contains(">" + badge + "</span>").contains(headline).contains("color:" + colour);
    }

    @Test
    @DisplayName("Each type shows only its relevant details")
    void relevantDetailsOnly() {
        String pickedUp = renderer.renderHtml("PARCEL_PICKED_UP", "s", "b", TRK, Map.of("courierName", "Kamal Perera"));
        String delivered = renderer.renderHtml("PARCEL_DELIVERED", "s", "b", TRK, Map.of("deliveredAt", "2026-11-14T10:15:00Z"));
        String failed = renderer.renderHtml("PARCEL_FAILED", "s", "b", TRK, Map.of("failureReason", "Recipient not available"));

        assertThat(pickedUp).contains(">Courier<").contains("Kamal Perera").doesNotContain("Delivered on").doesNotContain(">Reason<");
        // 10:15 UTC is 3:45 p.m. in Sri Lanka.
        assertThat(delivered).contains("Delivered on").contains("14 Nov 2026, 3:45 p.m.").doesNotContain(">Courier<");
        assertThat(failed).contains(">Reason<").contains("Recipient not available").contains("Delivery failed");
    }

    @Test
    @DisplayName("Recipient emails show who sent the parcel and why the recipient gets them, never account settings")
    void recipientEmails() {
        String created = renderer.renderHtml("RECIPIENT_PARCEL_CREATED", "s", "b", TRK, Map.of(
                "senderName", "Nimal Perera", "pickupDistrict", "COLOMBO", "deliveryDistrict", "KANDY",
                "deliverBy", "Saturday, 28 Nov"));
        String failed = renderer.renderHtml("RECIPIENT_FAILED", "s", "b", TRK, Map.of(
                "failureReason", "the recipient was not available", "nextStep", "It goes back to the sender."));

        assertThat(created).contains("A parcel is on its way to you", ">From<", "Nimal Perera", "Expected by",
                        "Saturday, 28 Nov", "nothing to pay", "the sender of this parcel entered your email address")
                .doesNotContain("notification settings");
        assertThat(failed).contains("It goes back to the sender.").doesNotContain("Please contact the sender if you have questions.");
    }

    @Test
    @DisplayName("The sender is told a courier is coming, with the fee ready in cash")
    void pickupAssigned() {
        String html = renderer.renderHtml("PARCEL_PICKUP_ASSIGNED", "s", "b", TRK, Map.of());

        assertThat(html).contains("A courier is coming for your parcel", "delivery fee ready in cash", TRK);
    }

    @Test
    @DisplayName("Trigger placeholders such as 'our courier' or 'just now' are not shown as details")
    void hidesPlaceholders() {
        String out = renderer.renderHtml("PARCEL_OUT_FOR_DELIVERY", "s", "b", TRK, Map.of("courierName", "our courier"));
        String delivered = renderer.renderHtml("PARCEL_DELIVERED", "s", "b", TRK, Map.of("deliveredAt", "just now"));

        assertThat(out).doesNotContain(">Courier<");
        assertThat(delivered).doesNotContain("Delivered on");
    }

    @Test
    @DisplayName("The progress bar marks done steps, the current step and a failed final step")
    void progressBar() {
        String outForDelivery = renderer.renderHtml("PARCEL_OUT_FOR_DELIVERY", "s", "b", TRK, Map.of());
        String failed = renderer.renderHtml("PARCEL_FAILED", "s", "b", TRK, Map.of());

        assertThat(outForDelivery).contains("&#10003; Confirmed").contains("&#10003; Picked up")
                .contains("&#10003; Out for delivery").contains(">Delivered</div>");
        assertThat(failed).contains("&#10005; Delivery failed").contains("background:#dc2626");
    }

    @Test
    @DisplayName("Escapes every dynamic value so event data cannot inject HTML")
    void escapesDynamicValues() {
        String html = renderer.renderHtml("PARCEL_FAILED", "<b>Subject</b>", "body", TRK,
                Map.of("failureReason", "<script>alert('x')</script> & more"));

        assertThat(html)
                .doesNotContain("<script>")
                .doesNotContain("<b>Subject</b>")
                .contains("&lt;script&gt;")
                .contains("&amp; more");
    }

    @Test
    @DisplayName("Unsafe or missing tracking numbers are left out")
    void unsafeTrackingNumber() {
        String unsafe = renderer.renderHtml("PARCEL_CREATED", "Subject", "Body", "\"><a href=x>", Map.of());
        String missing = renderer.renderHtml("PARCEL_CREATED", "Subject", "Body", null, Map.of());

        assertThat(unsafe).doesNotContain("Tracking number").doesNotContain("href=x");
        assertThat(missing).doesNotContain("Tracking number");
    }

    @Test
    @DisplayName("Unknown templates fall back to the template text")
    void unknownTemplateUsesBody() {
        String html = renderer.renderHtml("SOMETHING_ELSE", "Subject", "A custom message.", null, Map.of());

        assertThat(html).contains("A custom message.");
    }

    @Test
    @DisplayName("Plain-text alternative carries the message and the notice, with no tracking link")
    void plainTextAlternative() {
        String text = renderer.renderText("Your parcel " + TRK + " is out for delivery.", TRK);

        assertThat(text)
                .startsWith("Your parcel " + TRK + " is out for delivery.")
                .contains("please do not reply")
                .doesNotContain("http");
    }

    @Test
    @DisplayName("Courier assignment email: courier copy, route and pickup time, no progress bar")
    void courierAssignedEmail() {
        String html = renderer.renderHtml("COURIER_ASSIGNED", "New SwiftDrop delivery assigned", "body", TRK,
                Map.of("pickupDistrict", "Colombo", "deliveryDistrict", "Kandy", "estimatedPickupMinutes", "18"));

        assertThat(html).contains("You have a new delivery").contains("Delivery job")
                .contains("Colombo → Kandy").contains("In about 18 minutes")
                .contains("you are a SwiftDrop courier")
                .doesNotContain("Out for delivery</div>");
        assertThat(count(visible(html), TRK)).isEqualTo(1);
    }

    @Test
    @DisplayName("Sign-in alerts show when, device and masked IP, with the security footer")
    void signInAlerts() {
        Map<String, String> p = Map.of("occurredAt", "19 Nov 2026, 9:26 p.m.", "device", "Chrome on Windows",
                "ipAddress", "203.94.x.x");
        String success = renderer.renderHtml("LOGIN_SUCCESS", "New sign-in", "body", null, p);
        String failed = renderer.renderHtml("LOGIN_FAILED", "Failed sign-in", "body", null, p);

        assertThat(success).contains("New sign-in to your account").contains("Account security")
                .contains(">When<").contains("19 Nov 2026, 9:26 p.m.").contains("Chrome on Windows")
                .contains("203.94.x.x").contains("always sent and cannot be turned off")
                .doesNotContain("Your code").doesNotContain("Tracking number");
        assertThat(failed).contains("Someone tried to sign in to your account").contains("password was wrong")
                .contains("color:#991b1b");
    }
}
