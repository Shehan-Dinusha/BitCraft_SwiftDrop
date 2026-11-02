package com.swiftdrop.iam.config;

import com.swiftdrop.contracts.openapi.OpenApiConstants;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.List;
import java.util.Set;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenApiCustomizer accountAssociationSchema() {
        // UUID inference does not express Jakarta @Null; keep live OpenAPI aligned with validation.
        return api -> {
            for (String name : List.of("RegisterUserRequest", "CreateUserRequest")) {
                var schema = api.getComponents().getSchemas().get(name);
                if (schema != null && schema.getProperties() != null) {
                    if (schema.getProperties().get("merchantId") instanceof Schema<?> merchantId) {
                        merchantId.setType(null);
                        merchantId.setTypes(Set.of("null"));
                        merchantId.setFormat(null);
                    }
                }
            }
        };
    }

    @Bean
    public OpenAPI authIamOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("SwiftDrop Auth and IAM API")
                        .description("Authentication, identity, and role management APIs.")
                        .version("v1.0.0")
                        .contact(new Contact().name(OpenApiConstants.CONTACT_NAME).url(OpenApiConstants.CONTACT_URL))
                        .license(new License().name(OpenApiConstants.LICENSE_NAME).url(OpenApiConstants.LICENSE_URL)))
                .servers(java.util.List.of(new Server().url("http://localhost:8080").description("API Gateway")))
                .components(new Components().addSecuritySchemes(OpenApiConstants.BEARER_AUTH_SCHEME,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
