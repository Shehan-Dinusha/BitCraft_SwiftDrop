package com.swiftdrop.delivery.config;

import com.swiftdrop.contracts.openapi.OpenApiConstants;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI deliveryExecutionOpenAPI() {
        return new OpenAPI().info(new Info().title("SwiftDrop Delivery Execution API")
                .description("Delivery execution and tracking APIs.").version("v1.0.0"))
                .servers(java.util.List.of(new Server().url("http://localhost:8080").description("API Gateway")))
                .components(new Components().addSecuritySchemes(OpenApiConstants.BEARER_AUTH_SCHEME,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
