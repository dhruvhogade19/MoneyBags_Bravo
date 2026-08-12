package com.moneybags.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
class StartupDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(StartupDiagnostics.class);
    private final Environment environment;

    StartupDiagnostics(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    void ready() {
        String port = environment.getProperty("local.server.port",
                environment.getProperty("server.port", "8080"));
        log.info("\n============================================================\n" +
                " SERVICE STARTED: api-gateway\n" +
                " PORT           : {}\n" +
                " BASE URL       : http://localhost:{}\n" +
                " HEALTH         : http://localhost:{}/actuator/health\n" +
                " JAVA           : {}\n" +
                "============================================================", port, port, port,
                System.getProperty("java.version"));
    }
}
