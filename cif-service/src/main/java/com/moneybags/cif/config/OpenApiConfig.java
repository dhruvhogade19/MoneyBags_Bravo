package com.moneybags.cif.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cifServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Moneybags CIF Service API")
                        .version("v1")
                        .description("""
                                Customer Information File (CIF) Service APIs.
                                Manages customer identity, contact, employment,
                                KYC status, and controlled customer-data sharing
                                with other Moneybags services.
                                """)
                        .contact(new Contact()
                                .name("Moneybags Team"))
                        .license(new License()
                                .name("Internal Banking Project")));
    }
}