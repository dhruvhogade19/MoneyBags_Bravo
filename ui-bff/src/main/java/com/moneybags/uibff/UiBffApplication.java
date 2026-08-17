package com.moneybags.uibff;

import com.moneybags.uibff.config.BffProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BffProperties.class)
public class UiBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(UiBffApplication.class, args);
    }
}
