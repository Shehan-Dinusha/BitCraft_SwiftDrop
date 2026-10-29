package com.swiftdrop.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "ORDER_SERVICE_URL=http://orders.test:9082",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
})
class GatewayRoutesConfigTest {
    @Autowired
    private RouteLocator routeLocator;

    @Test
    void bindsOrderRouteToConfiguredUrl() {
        var orderRoute = routeLocator.getRoutes()
                .filter(route -> route.getId().equals("customer-order-service"))
                .blockFirst();

        assertThat(orderRoute).isNotNull();
        assertThat(orderRoute.getUri().toString()).isEqualTo("http://orders.test:9082");
    }
}
