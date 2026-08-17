package com.moneybags.eod;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.*;

@Component
class RealPeerOperations implements PeerOperations {
    private final Map<String, RestClient> clients;
    private final ClientCredentialsTokenProvider tokens;
    private final long notificationCifId;

    RealPeerOperations(ClientCredentialsTokenProvider tokens,
            @Value("${moneybags.clients.payments.base-url:http://localhost:8085}") String paymentsUrl,
            @Value("${moneybags.clients.credit-card.base-url:http://localhost:8084}") String creditCardUrl,
            @Value("${moneybags.clients.deposit-account.base-url:http://localhost:8086}") String depositUrl,
            @Value("${moneybags.clients.bill-generation.base-url:http://localhost:8087}") String billingUrl,
            @Value("${moneybags.clients.accounting.base-url:http://localhost:8088}") String accountingUrl,
            @Value("${moneybags.clients.statements.base-url:http://localhost:8089}") String statementsUrl,
            @Value("${moneybags.clients.notification.base-url:http://localhost:8090}") String notificationUrl,
            @Value("${moneybags.eod.notification-cif-id:101}") long notificationCifId) {
        this.tokens = tokens; this.notificationCifId = notificationCifId;
        this.clients = Map.of(
                "payments-service", client(paymentsUrl), "credit-card-service", client(creditCardUrl),
                "deposit-account-service", client(depositUrl), "bill-generation-service", client(billingUrl),
                "accounting-service", client(accountingUrl), "statements-service", client(statementsUrl),
                "notification-service", client(notificationUrl));
    }

    @Override
    public Map<String, Object> execute(StepDefinition step, EodContext context,
                                       Map<String, Map<String, Object>> outputs) {
        try {
            Map<String, Object> result = switch (step.code()) {
                case "PAYMENTS_CUTOFF" -> post(step, context, Map.of(
                        "businessDate", context.businessDate(), "commandReference", reference(context, step)));
                case "PAYMENTS_DRAIN", "PAYMENTS_REOPEN" -> post(step, context, null);
                case "CREDIT_CARD_READINESS", "DEPOSIT_READINESS", "FIXED_DEPOSIT_READINESS" -> get(step, context);
                case "DEPOSIT_ACCRUALS" -> post(step, context, Map.of(
                        "eodRunId", context.runId(), "commandReference", reference(context, step),
                        "businessDate", context.businessDate(), "currency", context.currency()));
                case "FIXED_DEPOSIT_ACCRUALS", "FIXED_DEPOSIT_MATURITIES" -> post(step, context, Map.of(
                        "eodRunId", context.runId(), "businessDate", context.businessDate(),
                        "commandReference", reference(context, step)));
                case "BILLS_CLOSE" -> post(step, context, Map.of(
                        "eodRunId", context.runId(), "businessDate", context.businessDate(),
                        "commandReference", reference(context, step)));
                case "TRIAL_BALANCE" -> post(step, context, Map.of(
                        "businessDate", context.businessDate(), "currencyCode", context.currency(),
                        "generatedBy", context.actorId()));
                case "PAYMENTS_RECONCILIATION" -> paymentReconciliation(step, context, outputs);
                case "FIXED_DEPOSIT_RECONCILIATION" -> fixedDepositReconciliation(step, context, outputs);
                case "STATEMENTS_GENERATE" -> post(step, context, Map.of(
                        "eodRunId", context.runId(), "businessDate", context.businessDate(),
                        "commandReference", reference(context, step)));
                case "NOTIFICATIONS_SEND" -> post(step, context, Map.of(
                        "cifId", notificationCifId, "notificationType", "STATEMENT_READY",
                        "sourceReference", context.runId(), "templateVariables", Map.of(
                                "statementId", statementReference(outputs, context),
                                "statementPeriod", context.businessDate().toString())));
                case "ACCOUNTING_PERIOD_OPEN_CURRENT", "ACCOUNTING_PERIOD_CLOSE" -> period(step, context, context.businessDate());
                case "ACCOUNTING_PERIOD_OPEN_NEXT" -> period(step, context, context.businessDate().plusDays(1));
                default -> throw new PeerOperationException("UNKNOWN_STEP", "Unsupported EOD step: " + step.code(), Map.of());
            };
            validate(step, result);
            return result;
        } catch (PeerOperationException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw new PeerOperationException("UPSTREAM_HTTP_" + exception.getStatusCode().value(),
                    step.providerService() + " rejected " + step.path() + ": " + exception.getResponseBodyAsString(),
                    Map.of("status", exception.getStatusCode().value(), "service", step.providerService(),
                            "path", step.path()));
        } catch (RuntimeException exception) {
            throw new PeerOperationException("UPSTREAM_UNAVAILABLE",
                    step.providerService() + " call failed: " + Objects.toString(exception.getMessage(), exception.getClass().getSimpleName()),
                    Map.of("service", step.providerService(), "path", step.path()));
        }
    }

    private Map<String, Object> paymentReconciliation(StepDefinition step, EodContext context,
                                                       Map<String, Map<String, Object>> outputs) {
        Map<String, Object> drain = required(outputs, "PAYMENTS_DRAIN");
        return post(step, context, Map.of(
                "eodRunId", context.runId(), "stepCode", step.code(),
                "commandReference", reference(context, step), "businessDate", context.businessDate(),
                "reconciledService", "PAYMENTS-SERVICE", "currencyCode", context.currency(),
                "expectedJournalCount", longValue(drain, "postedJournalCount"),
                "expectedTotalDebit", decimalValue(drain, "postedDebitTotal")));
    }

    private Map<String, Object> fixedDepositReconciliation(StepDefinition step, EodContext context,
                                                            Map<String, Map<String, Object>> outputs) {
        Map<String, Object> accruals = required(outputs, "FIXED_DEPOSIT_ACCRUALS");
        Map<String, Object> maturities = required(outputs, "FIXED_DEPOSIT_MATURITIES");
        long count = longValue(accruals, "processed") + longValue(maturities, "processed");
        BigDecimal total = decimalValue(accruals, "totalAmount").add(decimalValue(maturities, "totalAmount"));
        return post(step, context, Map.of(
                "eodRunId", context.runId() + "-FD", "stepCode", step.code(),
                "commandReference", reference(context, step), "businessDate", context.businessDate(),
                "reconciledService", "DEPOSIT-ACCOUNT-SERVICE", "currencyCode", context.currency(),
                "expectedJournalCount", count, "expectedTotalDebit", total));
    }

    private Map<String, Object> period(StepDefinition step, EodContext context, java.time.LocalDate date) {
        String path = step.path().replace("{businessDate}", date.toString());
        return post(step, context, path, Map.of("eodRunId", context.runId(), "stepCode", step.code(),
                "commandReference", reference(context, step), "actorId", context.actorId()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(StepDefinition step, EodContext context) {
        return exchange(clients.get(step.providerService()).get().uri(step.path()), step, context, false, null);
    }

    private Map<String, Object> post(StepDefinition step, EodContext context, Object body) {
        return post(step, context, step.path(), body);
    }

    private Map<String, Object> post(StepDefinition step, EodContext context, String path, Object body) {
        RestClient.RequestBodySpec request = clients.get(step.providerService()).post().uri(path);
        return exchange(request, step, context, true, body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchange(RestClient.RequestHeadersSpec<?> request, StepDefinition step,
                                         EodContext context, boolean mutation, Object body) {
        request.header("X-Correlation-Id", context.runId()).accept(MediaType.APPLICATION_JSON);
        if (mutation) request.header("Idempotency-Key", reference(context, step));
        String token = tokens.token(); if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null && request instanceof RestClient.RequestBodySpec bodySpec)
            bodySpec.contentType(MediaType.APPLICATION_JSON).body(body);
        Map<String, Object> response = request.retrieve().body(Map.class);
        return response == null ? Map.of("status", "COMPLETED")
                : Collections.unmodifiableMap(new LinkedHashMap<>(response));
    }

    private RestClient client(String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
    }

    private void validate(StepDefinition step, Map<String, Object> result) {
        switch (step.code()) {
            case "PAYMENTS_DRAIN" -> require(result, longValue(result, "pendingPayments") == 0 && "DRAINED".equals(text(result, "status")), "PAYMENTS_NOT_DRAINED");
            case "PAYMENTS_REOPEN" -> require(result, bool(result, "newPaymentIntake") && "OPEN".equals(text(result, "status")), "PAYMENTS_NOT_REOPENED");
            case "CREDIT_CARD_READINESS" -> require(result, bool(result, "readyForEod"), "CREDIT_CARD_NOT_READY");
            case "DEPOSIT_READINESS", "FIXED_DEPOSIT_READINESS" -> require(result, bool(result, "ready"), "DEPOSIT_NOT_READY");
            case "BILLS_CLOSE" -> require(result, longValue(result, "failedCount") == 0, "BILL_CLOSE_FAILED");
            case "TRIAL_BALANCE" -> require(result, bool(result, "balanced"), "TRIAL_BALANCE_UNBALANCED");
            case "PAYMENTS_RECONCILIATION", "FIXED_DEPOSIT_RECONCILIATION" -> require(result,
                    Set.of("MATCHED", "RESOLVED").contains(text(result, "status")), "RECONCILIATION_EXCEPTION");
            case "STATEMENTS_GENERATE", "NOTIFICATIONS_SEND" -> require(result,
                    !"FAILED".equalsIgnoreCase(text(result, "status")) && longValue(result, "failedCount") == 0,
                    "DELIVERY_FAILED");
            case "ACCOUNTING_PERIOD_CLOSE" -> require(result, "CLOSED".equals(text(result, "status")), "PERIOD_NOT_CLOSED");
            case "ACCOUNTING_PERIOD_OPEN_CURRENT", "ACCOUNTING_PERIOD_OPEN_NEXT" -> require(result, "OPEN".equals(text(result, "status")), "PERIOD_NOT_OPEN");
            default -> { }
        }
    }

    private void require(Map<String, Object> result, boolean condition, String code) {
        if (!condition) throw new PeerOperationException(code, "Upstream control check failed: " + code, result);
    }

    private String reference(EodContext context, StepDefinition step) { return "EOD:" + context.runId() + ":" + step.code(); }
    private Map<String, Object> required(Map<String, Map<String, Object>> outputs, String key) {
        Map<String, Object> value = outputs.get(key);
        if (value == null) throw new PeerOperationException("MISSING_STEP_OUTPUT", "Missing output from " + key, Map.of());
        return value;
    }
    private String statementReference(Map<String, Map<String, Object>> outputs, EodContext context) {
        Map<String, Object> value = required(outputs, "STATEMENTS_GENERATE");
        Object reference = value.getOrDefault("statementRunId", value.getOrDefault("runId", context.runId()));
        return Objects.toString(reference);
    }
    private String text(Map<String, Object> map, String key) { return Objects.toString(map.get(key), ""); }
    private boolean bool(Map<String, Object> map, String key) { return Boolean.parseBoolean(text(map, key)); }
    private long longValue(Map<String, Object> map, String key) {
        Object value = map.get(key); if (value == null) return 0; if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }
    private BigDecimal decimalValue(Map<String, Object> map, String key) {
        Object value = map.get(key); return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }
}
