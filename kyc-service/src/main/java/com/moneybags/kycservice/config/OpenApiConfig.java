package com.moneybags.kycservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI kycServiceOpenAPI() {

        return new OpenAPI()
                .info(
                        new Info()
                                .title("KYC Service API")
                                .description(
                                        "KYC management, document verification, " +
                                                "final decision and CIF synchronization APIs"
                                )
                                .version("v1")
                                .contact(
                                        new Contact()
                                                .name("MoneyBags KYC Team")
                                )
                );
    }
}