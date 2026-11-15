package com.swiftdrop.notification.service.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OtpEmailHtmlTest {

    private final EmailHtmlRenderer renderer = new EmailHtmlRenderer("https://swiftdrop.lk");

    @Test
    @DisplayName("Shows the code in a large box with its expiry and account-mail footer, with no parcel blocks")
    void rendersTheCodeBox() {
        String html = renderer.renderHtml("EMAIL_VERIFICATION", "Your SwiftDrop verification code",
                "Your SwiftDrop verification code is 482913. It expires in 10 minutes.", null,
                Map.of("code", "482913", "expiresInMinutes", "10"));

        assertThat(html)
                .contains("Verify your email")
                .contains("Confirm your email address")
                .contains("Your code")
                .contains("letter-spacing:0.35em")
                .contains(">482913<")
                .contains("Expires in 10 minutes")
                .contains("Never share this code with anyone")
                .doesNotContain("Tracking number")
                .doesNotContain("Track your parcel")
                .doesNotContain("you have a parcel")
                .doesNotContain("%s")
                .doesNotContain("%1$");
    }

    @Test
    @DisplayName("Password reset has its own badge and headline")
    void passwordResetStyle() {
        String html = renderer.renderHtml("PASSWORD_RESET", "Reset your SwiftDrop password", "Code 111222",
                null, Map.of("code", "111222", "expiresInMinutes", "10"));

        assertThat(html).contains("Password reset").contains("Reset your password").contains(">111222<");
    }

    @Test
    @DisplayName("A code that is not purely digits is never rendered into the HTML box")
    void refusesUnsafeCodes() {
        String html = renderer.renderHtml("EMAIL_VERIFICATION", "Subject", "Body", null,
                Map.of("code", "<script>x</script>", "expiresInMinutes", "10"));

        assertThat(html).doesNotContain("<script>").doesNotContain("Your code");
    }

    @Test
    @DisplayName("Parcel emails are unchanged by the new overload")
    void parcelEmailsKeepTheirFooter() {
        String html = renderer.renderHtml("PARCEL_CREATED", "Subject", "Body", "SD-AAAA-BBBB",
                Map.of("code", "999999"));

        assertThat(html).contains("you have a parcel with SwiftDrop")
                .contains("Track your parcel")
                .doesNotContain("999999");
    }
}
