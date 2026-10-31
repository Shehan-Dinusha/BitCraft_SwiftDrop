package com.swiftdrop.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("swiftdrop.routes")
public record GatewayRouteProperties(
        String auth,
        String order,
        String pricing,
        String dispatch,
        String delivery,
        String merchant,
        String webhook,
        String notification,
        String analytics) {
}
