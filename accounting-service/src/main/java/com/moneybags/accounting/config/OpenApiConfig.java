package com.moneybags.accounting.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI accountingOpenApi() {
        return new OpenAPI().info(new Info().title("Moneybags Accounting Service API").version("2.4.0")
                        .description("Authoritative double-entry ledger, lifecycle clearance, and EOD controls."))
                .servers(List.of(new Server().url("http://localhost:8088").description("Direct service URL")))
                .schemaRequirement("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                        .scheme("bearer").bearerFormat("JWT"));
    }
}
