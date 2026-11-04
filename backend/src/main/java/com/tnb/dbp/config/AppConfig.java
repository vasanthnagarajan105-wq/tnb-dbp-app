package com.tnb.dbp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {

    /** Name of the OpenAPI security scheme; referenced by protected endpoints. */
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public OpenAPI dbpOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TNB Digital Banking Platform API")
                        .version("0.1.0")
                        .description("Customer authentication APIs (Sprint 1). Synthetic data only in non-production environments."))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")));
    }
}
