package com.moneybags.cif;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CifServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CifServiceApplication.class, args);
    }
}

// @EnableFeignClients tells Spring to scan for interfaces such as KycServiceClient and create their working implementations automatically.
//Without it, KycServiceClient is only an interface; Spring will not know how to inject and use it.