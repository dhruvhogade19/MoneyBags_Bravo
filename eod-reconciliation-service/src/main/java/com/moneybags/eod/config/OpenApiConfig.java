package com.moneybags.eod.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI eodOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Moneybags EOD / Reconciliation Orchestrator API")
                .version("1.0.0")
                .description("Ordered, idempotent business-date closure orchestration."));
    }
}
