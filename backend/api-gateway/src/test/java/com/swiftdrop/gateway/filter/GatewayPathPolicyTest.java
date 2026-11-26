package com.swiftdrop.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayPathPolicyTest {

    @Test
    @DisplayName("A parcel recipient stops its emails without signing in; the rest of notifications needs a JWT")
    void recipientEmailStopIsPublic() {
        assertThat(GatewayPathPolicy.isPublicPath("/api/v1/notifications/email-stops")).isTrue();
        assertThat(GatewayPathPolicy.isPublicPath("/api/v1/notifications/me")).isFalse();
        assertThat(GatewayPathPolicy.isPublicPath("/api/v1/notifications/logs")).isFalse();
        assertThat(GatewayPathPolicy.isPublicPath("/api/v1/notifications/preferences")).isFalse();
    }
}
