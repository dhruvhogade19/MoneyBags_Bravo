package com.moneybags.eod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class RealPeerOperationsTest {
    private HttpServer server;
    private final ObjectMapper json = new ObjectMapper();
    private final List<CapturedRequest> requests = new ArrayList<>();
    private final Map<String, String> responseOverrides = new ConcurrentHashMap<>();

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
        RealPeerOperations peers = new RealPeerOperations(tokens, RestClient.builder(), RestClient.builder(),
                baseUrl, baseUrl, baseUrl, baseUrl,
                baseUrl, baseUrl, baseUrl, 101);
        EodContext context = new EodContext("run-123", LocalDate.of(2026, 8, 13), "eod-operator", "INR", null);

        peers.execute(new StepDefinition("ACCOUNTING_PERIOD_OPEN_CURRENT", 1, "accounting-service", "POST",
                "/internal/v1/accounting-periods/{businessDate}/open"), context, Map.of());
        Map<String, Object> nextPeriod = peers.execute(new StepDefinition(
                "ACCOUNTING_PERIOD_OPEN_NEXT", 17, "accounting-service", "POST",
                "/internal/v1/accounting-periods/{businessDate}/open"), context, Map.of());
        peers.execute(new StepDefinition("PAYMENTS_REOPEN", 18, "payments-service", "POST",
                "/internal/v1/payments/eod/reopen"), context,
                Map.of("ACCOUNTING_PERIOD_OPEN_NEXT", nextPeriod));

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
                .contains("\"stepCode\":\"ACCOUNTING_PERIOD_OPEN_CURRENT\"")
                .contains("\"executionEpoch\":1");
        assertThat(requests.get(2).body())
                .contains("\"commandReference\":\"EOD:run-123:PAYMENTS_BARRIER:EPOCH:1\"")
                .contains("\"businessDate\":\"2026-08-13\"")
                .contains("\"nextBusinessDate\":\"2026-08-14\"")
                .contains("\"currencyCode\":\"INR\"");
    }

    @Test
    void routesPersistedPublicPaymentEodPathsThroughTheInternalServiceContract() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        ClientCredentialsTokenProvider tokens = new ClientCredentialsTokenProvider(false, "", "", "", "") {
            @Override String token() { return "eod-service-token"; }
        };
        RealPeerOperations peers = new RealPeerOperations(tokens, RestClient.builder(), RestClient.builder(),
                baseUrl, baseUrl, baseUrl, baseUrl,
                baseUrl, baseUrl, baseUrl, 101);
        EodContext context = new EodContext("run-123", LocalDate.of(2026, 8, 13), "eod-operator", "INR", null);

        peers.execute(new StepDefinition("PAYMENTS_REOPEN", 18, "payments-service", "POST",
                "/api/v1/payments/operations/eod/reopen"), context, Map.of());

        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/internal/v1/payments/eod/reopen");
            assertThat(request.authorization()).isEqualTo("Bearer eod-service-token");
            assertThat(request.idempotencyKey()).isEqualTo("EOD:run-123:PAYMENTS_REOPEN:EPOCH:1");
            assertThat(request.body())
                    .contains("\"commandReference\":\"EOD:run-123:PAYMENTS_BARRIER:EPOCH:1\"")
                    .contains("\"businessDate\":\"2026-08-13\"")
                    .contains("\"nextBusinessDate\":\"2026-08-13\"");
        });
    }

    @Test
    void scopesFixedDepositReconciliationToTheParentEodJournalCorrelation() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        ClientCredentialsTokenProvider tokens = new ClientCredentialsTokenProvider(false, "", "", "", "");
        RealPeerOperations peers = new RealPeerOperations(tokens, RestClient.builder(), RestClient.builder(),
                baseUrl, baseUrl, baseUrl, baseUrl,
                baseUrl, baseUrl, baseUrl, 101);
        EodContext context = new EodContext("run-123", LocalDate.of(2026, 8, 13), "eod-operator", "INR", null);

        peers.execute(new StepDefinition("FIXED_DEPOSIT_RECONCILIATION", 13, "accounting-service", "POST",
                        "/internal/v1/eod/reconciliation/runs"), context,
                Map.of(
                        "FIXED_DEPOSIT_ACCRUALS", Map.of("processed", 2, "totalAmount", "20.50"),
                        "FIXED_DEPOSIT_MATURITIES", Map.of("processed", 1, "totalAmount", "300.00")));

        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/internal/v1/eod/reconciliation/runs");
            assertThat(request.correlationId()).isEqualTo("run-123");
            assertThat(request.idempotencyKey()).isEqualTo(
                    "EOD:run-123:FIXED_DEPOSIT_RECONCILIATION:JOURNAL-CORRELATED-V2:EPOCH:1");
            assertThat(request.body())
                    .contains("\"eodRunId\":\"run-123\"")
                    .contains("\"commandReference\":\"EOD:run-123:FIXED_DEPOSIT_RECONCILIATION\"")
                    .contains("\"journalCorrelationId\":\"run-123\"")
                    .contains("\"expectedJournalCount\":3")
                    .contains("\"expectedTotalDebit\":320.50")
                    .contains("\"executionEpoch\":1");
        });
    }

    @Test
    void prefersAuthoritativePostedFixedDepositMetricsOverLocalProcessingTotals() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        RealPeerOperations peers = new RealPeerOperations(
                new ClientCredentialsTokenProvider(false, "", "", "", ""),
                RestClient.builder(), RestClient.builder(), baseUrl, baseUrl, baseUrl, baseUrl,
                baseUrl, baseUrl, baseUrl, 101);
        EodContext context = new EodContext("run-123", LocalDate.of(2026, 8, 13),
                "eod-operator", "INR", null);

        peers.execute(new StepDefinition("FIXED_DEPOSIT_RECONCILIATION", 12, "accounting-service", "POST",
                        "/internal/v1/eod/reconciliation/runs"), context,
                Map.of(
                        "FIXED_DEPOSIT_ACCRUALS", Map.of("processed", 99, "totalAmount", "999.00",
                                "postedJournalCount", 2, "postedDebitTotal", "20.50"),
                        "FIXED_DEPOSIT_MATURITIES", Map.of("processed", 88, "totalAmount", "888.00",
                                "postedJournalCount", 1, "postedDebitTotal", "300.00")));

        assertThat(requests).singleElement().satisfies(request -> assertThat(request.body())
                .contains("\"expectedJournalCount\":3")
                .contains("\"expectedTotalDebit\":320.50"));
    }

    @Test
    void requiresCurrencyScopedAuthoritativePaymentDrainMetrics() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        RealPeerOperations peers = new RealPeerOperations(
                new ClientCredentialsTokenProvider(false, "", "", "", ""),
                RestClient.builder(), RestClient.builder(), baseUrl, baseUrl, baseUrl, baseUrl,
                baseUrl, baseUrl, baseUrl, 101);
        EodContext context = new EodContext("run-123", LocalDate.of(2026, 8, 13),
                "eod-operator", "INR", null, 2, "run-123:PAYMENTS_RECONCILIATION");

        peers.execute(new StepDefinition("PAYMENTS_RECONCILIATION", 11, "accounting-service", "POST",
                        "/internal/v1/eod/reconciliation/runs"), context,
                Map.of("PAYMENTS_DRAIN", Map.of("currencyCode", "INR", "postedJournalCount", 4,
                        "postedDebitTotal", "425.75")));

        assertThat(requests).singleElement().satisfies(request -> assertThat(request.body())
                .contains("\"currencyCode\":\"INR\"")
                .contains("\"expectedJournalCount\":4")
                .contains("\"expectedTotalDebit\":425.75")
                .contains("\"executionEpoch\":2"));

        PeerOperationException failure = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> peers.execute(new StepDefinition("PAYMENTS_RECONCILIATION", 11,
                                "accounting-service", "POST", "/internal/v1/eod/reconciliation/runs"), context,
                        Map.of("PAYMENTS_DRAIN", Map.of("currencyCode", "USD", "postedJournalCount", 4,
                                "postedDebitTotal", "425.75"))), PeerOperationException.class);
        assertThat(failure.code()).isEqualTo("STEP_OUTPUT_CURRENCY_MISMATCH");
    }

    @Test
    void adaptsPersistedPublicDepositContractsToInternalM2mRoutesAndUsesFreshEpochKeys() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        ClientCredentialsTokenProvider tokens = new ClientCredentialsTokenProvider(false, "", "", "", "") {
            @Override String token() { return "eod-service-token"; }
        };
        RealPeerOperations peers = new RealPeerOperations(tokens, RestClient.builder(), RestClient.builder(),
                baseUrl, baseUrl, baseUrl, baseUrl, baseUrl, baseUrl, baseUrl, 101);
        EodContext context = new EodContext("run-123", LocalDate.of(2026, 8, 13), "eod-operator",
                "INR", null, 2, "run-123:FIXED_DEPOSIT_ACCRUALS");

        peers.execute(new StepDefinition("FIXED_DEPOSIT_ACCRUALS", 7, "deposit-account-service", "POST",
                "/api/deposit-accounts/operations/eod/fixed-deposit-accruals"), context, Map.of());

        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/internal/v1/deposit-accounts/eod/fixed-deposit-accruals");
            assertThat(request.authorization()).isEqualTo("Bearer eod-service-token");
            assertThat(request.idempotencyKey())
                    .isEqualTo("EOD:run-123:FIXED_DEPOSIT_ACCRUALS:EPOCH:2");
        });
    }

    @Test
    void rejectsStructuredFixedDepositPostingFailures() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        responseOverrides.put("/internal/v1/deposit-accounts/eod/fixed-deposit-accruals",
                "{\"processed\":1,\"postedJournalCount\":0,\"postedDebitTotal\":0,"
                        + "\"failures\":[{\"code\":\"ACCOUNTING_POST_FAILED\"}]}");
        RealPeerOperations peers = new RealPeerOperations(
                new ClientCredentialsTokenProvider(false, "", "", "", ""),
                RestClient.builder(), RestClient.builder(), baseUrl, baseUrl, baseUrl, baseUrl,
                baseUrl, baseUrl, baseUrl, 101);
        EodContext context = new EodContext("run-123", LocalDate.of(2026, 8, 13),
                "eod-operator", "INR", null);

        PeerOperationException failure = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> peers.execute(new StepDefinition("FIXED_DEPOSIT_ACCRUALS", 7,
                                "deposit-account-service", "POST",
                                "/internal/v1/deposit-accounts/eod/fixed-deposit-accruals"), context, Map.of()),
                PeerOperationException.class);

        assertThat(failure.code()).isEqualTo("FIXED_DEPOSIT_POSTING_FAILED");
    }

    @Test
    void usesOneScopedOwnerForCutoffDrainAndReopen() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        RealPeerOperations peers = new RealPeerOperations(
                new ClientCredentialsTokenProvider(false, "", "", "", ""),
                RestClient.builder(), RestClient.builder(), baseUrl, baseUrl, baseUrl, baseUrl,
                baseUrl, baseUrl, baseUrl, 101);
        EodContext context = new EodContext("run-123", LocalDate.of(2026, 8, 13),
                "eod-operator", "INR", null, 2, "run-123:PAYMENTS_CUTOFF");

        Map<String, Object> cutoff = peers.execute(new StepDefinition("PAYMENTS_CUTOFF", 2,
                "payments-service", "POST", "/internal/v1/payments/eod/cutoff"), context, Map.of());
        Map<String, Object> drain = peers.execute(new StepDefinition("PAYMENTS_DRAIN", 3,
                "payments-service", "POST", "/internal/v1/payments/eod/drain"), context,
                Map.of("PAYMENTS_CUTOFF", cutoff));
        peers.execute(new StepDefinition("PAYMENTS_REOPEN", 15, "payments-service", "POST",
                        "/internal/v1/payments/eod/reopen"), context,
                Map.of("PAYMENTS_CUTOFF", cutoff, "PAYMENTS_DRAIN", drain));

        assertThat(requests).hasSize(3).allSatisfy(request -> assertThat(request.body())
                .contains("\"businessDate\":\"2026-08-13\"")
                .contains("\"currencyCode\":\"INR\"")
                .contains("\"commandReference\":\"EOD:run-123:PAYMENTS_BARRIER:EPOCH:2\""));
        assertThat(requests.get(0).body()).doesNotContain("nextBusinessDate");
        assertThat(requests.get(1).body()).doesNotContain("nextBusinessDate");
        assertThat(requests.get(2).body()).contains("\"nextBusinessDate\":\"2026-08-13\"");
    }

    @Test
    void rejectsAReopenResponseThatDoesNotMatchTheRolledBusinessDate() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        responseOverrides.put("/internal/v1/payments/eod/reopen",
                "{\"status\":\"OPEN\",\"pendingPayments\":0,\"newPaymentIntake\":true,"
                        + "\"businessDate\":\"2026-08-13\",\"currencyCode\":\"INR\","
                        + "\"commandReference\":\"EOD:run-123:PAYMENTS_BARRIER:EPOCH:1\"}");
        RealPeerOperations peers = new RealPeerOperations(
                new ClientCredentialsTokenProvider(false, "", "", "", ""),
                RestClient.builder(), RestClient.builder(), baseUrl, baseUrl, baseUrl, baseUrl,
                baseUrl, baseUrl, baseUrl, 101);
        EodContext context = new EodContext("run-123", LocalDate.of(2026, 8, 13),
                "eod-operator", "INR", null);

        PeerOperationException failure = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> peers.execute(new StepDefinition("PAYMENTS_REOPEN", 15, "payments-service",
                                "POST", "/internal/v1/payments/eod/reopen"), context,
                        Map.of("ACCOUNTING_PERIOD_OPEN_NEXT", Map.of("status", "OPEN"))),
                PeerOperationException.class);

        assertThat(failure.code()).isEqualTo("PAYMENTS_BUSINESS_DATE_MISMATCH");
    }

    @Test
    void rejectsADrainThatWasReopenedOrBelongsToAnotherFence() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        responseOverrides.put("/internal/v1/payments/eod/drain",
                "{\"status\":\"DRAINED\",\"pendingPayments\":0,\"newPaymentIntake\":true,"
                        + "\"businessDate\":\"2026-08-13\",\"currencyCode\":\"INR\","
                        + "\"commandReference\":\"EOD:run-123:PAYMENTS_BARRIER:EPOCH:1\"}");
        RealPeerOperations peers = new RealPeerOperations(
                new ClientCredentialsTokenProvider(false, "", "", "", ""),
                RestClient.builder(), RestClient.builder(), baseUrl, baseUrl, baseUrl, baseUrl,
                baseUrl, baseUrl, baseUrl, 101);
        EodContext context = new EodContext("run-123", LocalDate.of(2026, 8, 13),
                "eod-operator", "INR", null);

        PeerOperationException failure = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> peers.execute(new StepDefinition("PAYMENTS_DRAIN", 3, "payments-service", "POST",
                        "/internal/v1/payments/eod/drain"), context, Map.of()), PeerOperationException.class);

        assertThat(failure.code()).isEqualTo("PAYMENTS_NOT_DRAINED");
    }

    @Test
    void rejectsStructuredCasaAccrualFailures() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        responseOverrides.put("/internal/v1/deposit-accounts/eod/accruals",
                "{\"processed\":1,\"failedCount\":1,\"failures\":[{\"code\":\"POST_FAILED\"}]}");
        RealPeerOperations peers = new RealPeerOperations(
                new ClientCredentialsTokenProvider(false, "", "", "", ""),
                RestClient.builder(), RestClient.builder(), baseUrl, baseUrl, baseUrl, baseUrl,
                baseUrl, baseUrl, baseUrl, 101);

        PeerOperationException failure = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> peers.execute(new StepDefinition("DEPOSIT_ACCRUALS", 6, "deposit-account-service", "POST",
                                "/internal/v1/deposit-accounts/eod/accruals"),
                        new EodContext("run-123", LocalDate.of(2026, 8, 13), "operator", "INR", null),
                        Map.of()), PeerOperationException.class);

        assertThat(failure.code()).isEqualTo("DEPOSIT_ACCRUAL_FAILED");
    }

    @Test
    void reassertsTheOwnedPaymentFenceImmediatelyBeforePeriodClose() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        RealPeerOperations peers = new RealPeerOperations(
                new ClientCredentialsTokenProvider(false, "", "", "", ""),
                RestClient.builder(), RestClient.builder(), baseUrl, baseUrl, baseUrl, baseUrl,
                baseUrl, baseUrl, baseUrl, 101);
        EodContext context = new EodContext("run-123", LocalDate.of(2026, 8, 13),
                "eod-operator", "INR", null, 2, "run-123:ACCOUNTING_PERIOD_CLOSE");
        Map<String, Object> drain = Map.of(
                "status", "DRAINED", "pendingPayments", 0, "newPaymentIntake", false,
                "businessDate", "2026-08-13", "currencyCode", "INR",
                "commandReference", "EOD:run-123:PAYMENTS_BARRIER:EPOCH:1");

        peers.execute(new StepDefinition("ACCOUNTING_PERIOD_CLOSE", 13, "accounting-service", "POST",
                        "/internal/v1/accounting-periods/{businessDate}/close"), context,
                Map.of("PAYMENTS_DRAIN", drain));

        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).path()).isEqualTo("/internal/v1/payments/eod/drain");
        assertThat(requests.get(0).body())
                .contains("\"commandReference\":\"EOD:run-123:PAYMENTS_BARRIER:EPOCH:1\"");
        assertThat(requests.get(0).idempotencyKey())
                .isEqualTo("EOD:run-123:ACCOUNTING_PERIOD_CLOSE:PERIOD-CLOSE-FENCE:EPOCH:2");
        assertThat(requests.get(1).path()).isEqualTo(
                "/internal/v1/accounting-periods/2026-08-13/close");
    }

    @Test
    void callsPersistedFixedDepositReadinessPathWithServiceAuthorization() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        ClientCredentialsTokenProvider tokens = new ClientCredentialsTokenProvider(false, "", "", "", "") {
            @Override String token() { return "eod-service-token"; }
        };
        RealPeerOperations peers = new RealPeerOperations(tokens, RestClient.builder(), RestClient.builder(),
                baseUrl, baseUrl, baseUrl, baseUrl,
                baseUrl, baseUrl, baseUrl, 101);
        EodContext context = new EodContext("run-123", LocalDate.of(2026, 8, 13), "eod-operator", "INR",
                "Bearer operator-token");

        peers.execute(new StepDefinition("FIXED_DEPOSIT_READINESS", 7, "deposit-account-service", "GET",
                "/internal/v1/deposit-accounts/eod/fixed-deposit-readiness"), context, Map.of());

        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.path()).isEqualTo("/internal/v1/deposit-accounts/eod/fixed-deposit-readiness");
            assertThat(request.authorization()).isEqualTo("Bearer eod-service-token");
            assertThat(request.idempotencyKey()).isNull();
        });
    }

    @Test
    void rejectsUnknownPersistedStepsInsteadOfSilentlyCompletingThem() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        ClientCredentialsTokenProvider tokens = new ClientCredentialsTokenProvider(false, "", "", "", "");
        RealPeerOperations peers = new RealPeerOperations(tokens, RestClient.builder(), RestClient.builder(),
                baseUrl, baseUrl, baseUrl, baseUrl,
                baseUrl, baseUrl, baseUrl, 101);
        EodContext context = new EodContext("run-123", LocalDate.of(2026, 8, 13), "eod-operator", "INR", null);

        PeerOperationException failure = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> peers.execute(new StepDefinition("LEGACY_FINANCIAL_STEP", 99, "accounting-service", "POST",
                        "/legacy/financial-step"), context, Map.of()), PeerOperationException.class);

        assertThat(failure.code()).isEqualTo("UNKNOWN_STEP");
        assertThat(requests).isEmpty();
    }

    @Test
    void selectsServiceDiscoveryOnlyForKnownEurekaServiceIds() {
        assertThat(RealPeerOperations.usesServiceDiscovery("http://accounting-service")).isTrue();
        assertThat(RealPeerOperations.usesServiceDiscovery("http://payments-service/internal/v1")).isTrue();
        assertThat(RealPeerOperations.usesServiceDiscovery("http://localhost:8088")).isFalse();
        assertThat(RealPeerOperations.usesServiceDiscovery("http://127.0.0.1:8088")).isFalse();
        assertThat(RealPeerOperations.usesServiceDiscovery("https://accounting.example.com")).isFalse();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new CapturedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body,
                exchange.getRequestHeaders().getFirst("X-Correlation-Id"),
                exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                exchange.getRequestHeaders().getFirst("Authorization")));
        String response = responseOverrides.get(exchange.getRequestURI().getPath());
        if (response != null) {
            // Explicit test response.
        } else if (exchange.getRequestURI().getPath().endsWith("/cutoff")) {
            response = paymentResponse(body, "CUT_OFF", false, 0);
        } else if (exchange.getRequestURI().getPath().endsWith("/drain")) {
            response = paymentResponse(body, "DRAINED", false, 0);
        } else if (exchange.getRequestURI().getPath().endsWith("/reopen")) {
            response = paymentResponse(body, "OPEN", true, 0);
        } else if (exchange.getRequestURI().getPath().endsWith("/reconciliation/runs")) {
            response = "{\"status\":\"MATCHED\"}";
        } else if (exchange.getRequestURI().getPath().endsWith("/fixed-deposit-readiness")) {
            response = "{\"ready\":true}";
        } else if (exchange.getRequestURI().getPath().endsWith("/close")) {
            response = "{\"status\":\"CLOSED\"}";
        } else {
            response = "{\"status\":\"OPEN\"}";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String paymentResponse(String requestBody, String status, boolean intake, long pending)
            throws IOException {
        JsonNode request = json.readTree(requestBody);
        String businessDate = request.hasNonNull("nextBusinessDate")
                ? request.path("nextBusinessDate").asText()
                : request.path("businessDate").asText();
        return json.writeValueAsString(Map.of(
                "status", status,
                "pendingPayments", pending,
                "newPaymentIntake", intake,
                "businessDate", businessDate,
                "currencyCode", request.path("currencyCode").asText(),
                "commandReference", request.path("commandReference").asText()));
    }

    private record CapturedRequest(String method, String path, String body, String correlationId,
                                   String idempotencyKey, String authorization) { }
}
