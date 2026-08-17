package com.moneybags.eod;

import com.moneybags.eod.config.EodProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(EodProperties.class)
public class EodReconciliationApplication {
    public static void main(String[] args) {
        SpringApplication.run(EodReconciliationApplication.class, args);
    }
}
