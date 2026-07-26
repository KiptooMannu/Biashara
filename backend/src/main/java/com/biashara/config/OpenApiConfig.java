package com.biashara.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI biasharaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("BIASHARA ERP API")
                        .version("1.0.0")
                        .description("""
                                AI-powered multi-tenant ERP for MSMEs.

                                Authenticate with POST /api/auth/login, then click Authorize and paste
                                the returned accessToken. GET /api/auth/demo-accounts lists the seeded
                                logins for each role.
                                """)
                        .contact(new Contact().name("BIASHARA")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .name(BEARER)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
