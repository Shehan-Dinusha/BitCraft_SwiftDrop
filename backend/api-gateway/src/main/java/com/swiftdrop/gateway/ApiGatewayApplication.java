package com.swiftdrop.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * SwiftDrop API Gateway — stateless reactive edge service.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>JWT validation and claim extraction into downstream headers</li>
 *   <li>Request routing to internal microservices</li>
 *   <li>Distributed rate limiting via Redis</li>
 *   <li>CORS policy enforcement</li>
 *   <li>Correlation ID injection for distributed tracing</li>
 *   <li>WebSocket proxy for real-time delivery tracking</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
