package com.swiftdrop.merchant.config;

import com.swiftdrop.contracts.openapi.OpenApiConstants;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Security scheme for B2B routes the gateway authenticates with the X-Merchant-Api-Key header. */
    public static final String API_KEY_SCHEME = "apiKeyAuth";

    @Bean
    public OpenAPI merchantB2bOpenAPI() {
        return new OpenAPI().info(new Info().title("SwiftDrop Merchant B2B API")
                .description("Merchant onboarding, API key, and order ingestion APIs.").version("v1.0.0"))
                .servers(java.util.List.of(new Server().url("/").description("API Gateway (same origin as this Swagger UI)")))
                .addSecurityItem(new SecurityRequirement().addList(OpenApiConstants.BEARER_AUTH_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(OpenApiConstants.BEARER_AUTH_SCHEME,
                                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes(API_KEY_SCHEME,
                                new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                                        .name(JwtHeaderConstants.X_MERCHANT_API_KEY)));
    }
}
