package com.moneybags.eod;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RealPeerOperationsTest {
    private HttpServer server;
    private final List<CapturedRequest> requests = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void opensAccountingPeriodsAndReopensPaymentsWithStableIntegrationHeaders() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        ClientCredentialsTokenProvider tokens = new ClientCredentialsTokenProvider(false, "", "", "", "");
        RealPeerOperations peers = new RealPeerOperations(tokens, baseUrl, baseUrl, baseUrl, baseUrl,
                baseUrl, baseUrl, baseUrl, 101);
        EodContext context = new EodContext("run-123", LocalDate.of(2026, 8, 13), "eod-operator", "INR");

        peers.execute(new StepDefinition("ACCOUNTING_PERIOD_OPEN_CURRENT", 1, "accounting-service", "POST",
                "/internal/v1/accounting-periods/{businessDate}/open"), context, Map.of());
        peers.execute(new StepDefinition("ACCOUNTING_PERIOD_OPEN_NEXT", 17, "accounting-service", "POST",
                "/internal/v1/accounting-periods/{businessDate}/open"), context, Map.of());
        peers.execute(new StepDefinition("PAYMENTS_REOPEN", 18, "payments-service", "POST",
                "/internal/v1/payments/eod/reopen"), context, Map.of());

        assertThat(requests).extracting(CapturedRequest::path).containsExactly(
                "/internal/v1/accounting-periods/2026-08-13/open",
                "/internal/v1/accounting-periods/2026-08-14/open",
                "/internal/v1/payments/eod/reopen");
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.correlationId()).isEqualTo("run-123");
            assertThat(request.idempotencyKey()).startsWith("EOD:run-123:");
        });
        assertThat(requests.get(0).body()).contains("\"eodRunId\":\"run-123\"")
                .contains("\"actorId\":\"eod-operator\"")
                .contains("\"stepCode\":\"ACCOUNTING_PERIOD_OPEN_CURRENT\"");
        assertThat(requests.get(2).body()).isEmpty();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new CapturedRequest(exchange.getRequestURI().getPath(), body,
                exchange.getRequestHeaders().getFirst("X-Correlation-Id"),
                exchange.getRequestHeaders().getFirst("Idempotency-Key")));
        String response = exchange.getRequestURI().getPath().endsWith("/reopen")
                ? "{\"status\":\"OPEN\",\"newPaymentIntake\":true}"
                : "{\"status\":\"OPEN\"}";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record CapturedRequest(String path, String body, String correlationId, String idempotencyKey) { }
}
