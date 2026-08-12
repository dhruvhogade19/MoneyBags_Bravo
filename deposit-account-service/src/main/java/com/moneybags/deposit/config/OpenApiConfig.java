package com.moneybags.deposit.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI depositAccountOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Moneybags Deposit Account API")
                .version("v1")
                .description("Account opening, lifecycle, ownership, limits and balance projection APIs."));
    }
}
