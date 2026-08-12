package com.moneybags.deposit.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

@Component
public class StartupDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(StartupDiagnostics.class);
    private final Environment environment;
    private final DataSource dataSource;

    public StartupDiagnostics(Environment environment, DataSource dataSource) {
        this.environment = environment;
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        String port = environment.getProperty("local.server.port",
                environment.getProperty("server.port", "8086"));
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData database = connection.getMetaData();
            log.info("\n============================================================\n" +
                    " SERVICE STARTED : deposit-account-service\n" +
                    " PORT            : {}\n" +
                    " SWAGGER         : http://localhost:{}/swagger-ui.html\n" +
                    " OPENAPI JSON    : http://localhost:{}/v3/api-docs\n" +
                    " HEALTH          : http://localhost:{}/actuator/health\n" +
                    " DATABASE        : CONNECTED\n" +
                    " DATABASE PRODUCT: {} {}\n" +
                    " DATABASE URL    : {}\n" +
                    " DATABASE USER   : {}\n" +
                    " JAVA            : {}\n" +
                    "============================================================", port, port, port, port,
                    database.getDatabaseProductName(), database.getDatabaseProductVersion(),
                    database.getURL(), database.getUserName(), System.getProperty("java.version"));
        } catch (SQLException exception) {
            log.error("\n============================================================\n" +
                    " SERVICE STARTED : deposit-account-service\n" +
                    " PORT            : {}\n" +
                    " DATABASE        : NOT CONNECTED\n" +
                    " REASON          : {}\n" +
                    "============================================================", port, exception.getMessage());
        }
    }
}
