package com.vault.configmanager.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 / Swagger UI Configuration for Secure Config Vault.
 * Exposes interactive documentation and live API invocation dashboard at /swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "ApiKeyAuth";

    @Bean
    public OpenAPI vaultOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Zero-Config Vault API")
                        .description("Enterprise-Grade Configuration Vault built with MongoDB, " +
                                "featuring AES-256-GCM Envelope Encryption, reactive Change Streams via SSE, " +
                                "immutable temporal versioning, GridFS binary asset storage, and granular Scoped API Keys.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Vault Core Security Engineering")
                                .email("security@vault.internal"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name("X-API-Key")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Scoped API key for Role-Based Access Control (e.g. sk-dev-key-001)")));
    }
}
