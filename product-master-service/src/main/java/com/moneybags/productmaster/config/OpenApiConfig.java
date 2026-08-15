package com.moneybags.productmaster.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI productMasterOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Moneybags Product Master API")
                .version("v1")
                .description("Deposit, fixed-deposit, and credit-card product catalogue. Public contract version is always 1."));
    }
}
