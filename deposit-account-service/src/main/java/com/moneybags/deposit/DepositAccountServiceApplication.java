package com.moneybags.deposit;

import com.moneybags.deposit.config.DepositAccountProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableConfigurationProperties(DepositAccountProperties.class)
@EnableScheduling
@SpringBootApplication
public class DepositAccountServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DepositAccountServiceApplication.class, args);
    }
}
