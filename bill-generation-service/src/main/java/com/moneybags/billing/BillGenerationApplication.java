package com.moneybags.billing;

import com.moneybags.billing.integration.NotificationClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.hibernate.type.NumericBooleanConverter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.ChronoUnit;
import java.util.*;

@SpringBootApplication
public class BillGenerationApplication {
    public static void main(String[] args) {
        SpringApplication.run(BillGenerationApplication.class, args);
    }

    @Entity(name = "Bill")
    @Table(name = "BILL", uniqueConstraints = @UniqueConstraint(name = "UQ_BILL_ACCOUNT_PERIOD", columnNames = {"ACCOUNT_ID", "BILLING_PERIOD"}))
    public static class Bill {
        @Id
        @Column(name = "BILL_ID", length = 36)
        String id;
        @Column(name = "ACCOUNT_ID", nullable = false, length = 64)
        String accountId;
        @Column(name = "CIF_ID")
        Long cifId;
        @Column(name = "PRODUCT_CODE", nullable = false)
        String productCode;
        @Column(name = "BILLING_PERIOD", nullable = false)
        String billingPeriod;
        @Column(name = "PERIOD_START")
        LocalDate periodStart;
        @Column(name = "PERIOD_END")
        LocalDate periodEnd;
        @Column(name = "BUSINESS_DATE", nullable = false)
        LocalDate businessDate;
        @Column(name = "STATUS", nullable = false)
        String status;
        @Column(name = "PREVIOUS_BALANCE", nullable = false, precision = 19, scale = 4)
        BigDecimal previousBalance;
        @Column(name = "TOTAL_DUE", nullable = false, precision = 19, scale = 4)
        BigDecimal totalDue;
        @Column(name = "PAID_AMOUNT", nullable = false, precision = 19, scale = 4)
        BigDecimal paidAmount;
        @Column(name = "MINIMUM_DUE", nullable = false, precision = 19, scale = 4)
        BigDecimal minimumDue;
        @Column(name = "PAYMENT_DUE_DATE", nullable = false)
        LocalDate paymentDueDate;
        @Column(name = "CURRENCY_CODE", nullable = false, columnDefinition = "CHAR(3)")
        String currency;
        @Column(name = "VERSION_NO", nullable = false)
        long version;
        @Column(name = "GENERATED_AT", nullable = false)
        OffsetDateTime generatedAt;
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "SAVE_TO_HISTORY", nullable = false, columnDefinition = "NUMBER(1)")
        boolean savedToHistory;

        protected Bill() {
        }

        Bill(String id, String accountId, Long cifId, String productCode, String period, LocalDate date, String currency, BigDecimal previous, BigDecimal total, BigDecimal minimum, LocalDate due) {
            this(id, accountId, cifId, productCode, period,
                    YearMonth.parse(period).atDay(1), YearMonth.parse(period).atEndOfMonth(), date,
                    currency, previous, total, minimum, due, true);
        }

        Bill(String id, String accountId, Long cifId, String productCode, String period,
             LocalDate periodStart, LocalDate periodEnd, LocalDate date, String currency,
             BigDecimal previous, BigDecimal total, BigDecimal minimum, LocalDate due,
             boolean savedToHistory) {
            this.id = id;
            this.accountId = accountId;
            this.cifId = cifId;
            this.productCode = productCode;
            this.billingPeriod = period;
            this.periodStart = periodStart;
            this.periodEnd = periodEnd;
            this.businessDate = date;
            this.currency = currency;
            this.previousBalance = previous;
            this.totalDue = total;
            this.paidAmount = BigDecimal.ZERO;
            this.minimumDue = minimum;
            this.paymentDueDate = due;
            this.status = "GENERATED";
            this.version = 0;
            this.generatedAt = OffsetDateTime.now(ZoneOffset.UTC);
            this.savedToHistory = savedToHistory;
        }
    }

    @Entity(name = "BillLine")
    @Table(name = "BILL_LINE")
    public static class BillLine {
        @Id
        @Column(name = "LINE_ID")
        String id;
        @Column(name = "BILL_ID")
        String billId;
        @Column(name = "LINE_TYPE")
        String type;
        @Column(name = "SOURCE_REFERENCE")
        String reference;
        @Column(name = "DESCRIPTION")
        String description;
        @Column(name = "AMOUNT", precision = 19, scale = 4)
        BigDecimal amount;
        @Column(name = "OCCURRED_AT")
        OffsetDateTime occurredAt;

        protected BillLine() {
        }

        BillLine(String billId, Activity a) {
            this.id = UUID.randomUUID().toString();
            this.billId = billId;
            this.type = a.type;
            this.reference = a.reference;
            this.description = a.description;
            this.amount = a.amount;
            this.occurredAt = a.occurredAt;
        }
    }

    @Entity(name = "BillSnapshot")
    @Table(name = "BILL_CALCULATION_SNAPSHOT")
    public static class BillSnapshot {
        @Id
        @Column(name = "SNAPSHOT_ID")
        String id;
        @Column(name = "BILL_ID")
        String billId;
        @Column(name = "PRODUCT_VERSION")
        String productVersion;
        @Column(name = "INTEREST_POLICY_VERSION")
        String policyVersion;
        @Column(name = "RATE_SNAPSHOT")
        BigDecimal rate;
        @Lob
        @Column(name = "FEE_RULES_JSON")
        String feesJson;
        @Column(name = "CALCULATED_AT")
        OffsetDateTime calculatedAt;

        protected BillSnapshot() {
        }

        BillSnapshot(String billId, Product p, String fees) {
            id = UUID.randomUUID().toString();
            this.billId = billId;
            productVersion = p.version;
            policyVersion = p.policyVersion;
            rate = p.annualRate;
            feesJson = fees;
            calculatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @Entity(name = "BillHistory")
    @Table(name = "BILL_STATUS_HISTORY")
    public static class BillHistory {
        @Id
        @Column(name = "HISTORY_ID")
        String id;
        @Column(name = "BILL_ID")
        String billId;
        @Column(name = "FROM_STATUS")
        String fromStatus;
        @Column(name = "TO_STATUS")
        String toStatus;
        @Column(name = "REASON_CODE")
        String reason;
        @Column(name = "CHANGED_AT")
        OffsetDateTime changedAt;

        protected BillHistory() {
        }

        BillHistory(String billId) {
            id = UUID.randomUUID().toString();
            this.billId = billId;
            toStatus = "GENERATED";
            reason = "BILL_GENERATED";
            changedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
        BillHistory(String billId, String fromStatus, String toStatus, String reason) {
            id = UUID.randomUUID().toString(); this.billId = billId; this.fromStatus = fromStatus;
            this.toStatus = toStatus; this.reason = reason; changedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @Entity(name = "BillPaymentAllocation")
    @Table(name = "BILL_PAYMENT_ALLOCATION", uniqueConstraints = @UniqueConstraint(name = "UQ_BILL_ALLOCATION_PAYMENT", columnNames = "PAYMENT_ID"))
    public static class BillPaymentAllocation {
        @Id @Column(name = "ALLOCATION_ID") String id;
        @Column(name = "BILL_ID", nullable = false) String billId;
        @Column(name = "PAYMENT_ID", nullable = false) String paymentId;
        @Column(name = "JOURNAL_NUMBER", nullable = false) String journalNumber;
        @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 4) BigDecimal amount;
        @Column(name = "CURRENCY_CODE", nullable = false, columnDefinition = "CHAR(3)") String currency;
        @Column(name = "SETTLED_AT", nullable = false) OffsetDateTime settledAt;
        @Column(name = "CREATED_AT", nullable = false) OffsetDateTime createdAt;
        protected BillPaymentAllocation() {}
        BillPaymentAllocation(String billId, PaymentSettlementRequest request) {
            id=UUID.randomUUID().toString(); this.billId=billId; paymentId=request.paymentId; journalNumber=request.journalNumber;
            amount=request.amount.setScale(4, RoundingMode.HALF_UP); currency=request.currency; settledAt=request.settledAt; createdAt=OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @Entity(name = "Idempotency")
    @Table(name = "IDEMPOTENCY_RECORD")
    public static class Idempotency {
        @Id
        @Column(name = "RECORD_ID")
        String id;
        @Column(name = "IDEMPOTENCY_SCOPE")
        String scope;
        @Column(name = "KEY_HASH")
        String keyHash;
        @Column(name = "REQUEST_HASH")
        String requestHash;
        /**
         * Existing Oracle deployments require a non-null lifecycle status
         * for idempotency records. A record is saved only after the bill has
         * been persisted successfully.
         */
        @Column(name = "PROCESSING_STATUS", nullable = false)
        String processingStatus;
        @Column(name = "RESOURCE_ID")
        String resourceId;
        @Column(name = "CREATED_AT")
        OffsetDateTime createdAt;

        protected Idempotency() {
        }

        Idempotency(String scope, String key, String request, String resource) {
            id = UUID.randomUUID().toString();
            this.scope = scope;
            keyHash = key;
            requestHash = request;
            processingStatus = "COMPLETED";
            resourceId = resource;
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @Entity(name = "Audit")
    @Table(name = "AUDIT_LOG")
    public static class Audit {
        @Id
        @Column(name = "AUDIT_ID")
        String id;
        @Column(name = "AGGREGATE_ID")
        String aggregateId;
        @Column(name = "ACTION")
        String action;
        @Column(name = "OUTCOME")
        String outcome;
        @Column(name = "CORRELATION_ID")
        String correlationId;
        @Column(name = "OCCURRED_AT")
        OffsetDateTime occurredAt;

        protected Audit() {
        }

        Audit(String id) { this(id, "BILL_GENERATED"); }
        Audit(String id, String action) {
            this.id = UUID.randomUUID().toString();
            aggregateId = id;
            this.action = action;
            outcome = "SUCCESS";
            correlationId = Optional.ofNullable(MDC.get("correlationId")).orElse("system");
            occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public record GenerateRequest(@NotBlank String accountId, @NotBlank String billingPeriod,
                                  @NotNull LocalDate businessDate) {
    }

    public record BillLineResponse(String lineType, String sourceReference, String description, BigDecimal amount,
                                   OffsetDateTime occurredAt) {
    }

    public record BillResponse(String billId, String accountId, String billingPeriod, String status,
                               BigDecimal previousBalance, BigDecimal totalAmountDue, BigDecimal minimumAmountDue,
                               BigDecimal paidAmount, BigDecimal outstandingAmount, LocalDate paymentDueDate,
                               String currency, List<BillLineResponse> lines, String productCode,
                               LocalDate periodStart, LocalDate periodEnd, OffsetDateTime generatedAt,
                               boolean savedToHistory, String pdfPassword) {
    }

    public record CustomerStatementRequest(@NotBlank String accountId, @NotNull LocalDate startDate,
                                           @NotNull LocalDate endDate, boolean saveToHistory) {
    }

    public record AdminStatementRequest(@NotNull @Positive Long cifId, @NotBlank String accountId,
                                        @NotNull LocalDate startDate, @NotNull LocalDate endDate) {
    }

    public record StatementPreview(String accountId, String productCode, LocalDate periodStart,
                                   LocalDate periodEnd, BigDecimal openingBalance, BigDecimal paymentsReceived,
                                   BigDecimal newPurchases, BigDecimal fees, BigDecimal taxes,
                                   BigDecimal financeCharges, BigDecimal minimumAmountDue,
                                   BigDecimal totalAmountDue, LocalDate paymentDueDate, String currency,
                                   List<BillLineResponse> lines, boolean duplicate, String existingBillId) {
    }

    public record BillSummaryResponse(String billId, String accountId, String billingPeriod, BigDecimal totalAmountDue,
                                      BigDecimal minimumAmountDue, BigDecimal paidAmount, BigDecimal outstandingAmount,
                                      String status, LocalDate paymentDueDate) {
    }

    public record BillPage(List<BillResponse> content, int page, int size, long totalElements) {
    }

    public record CloseRequest(@NotBlank String eodRunId, @NotNull LocalDate businessDate,
                               @NotBlank String commandReference) {
    }

    public record CloseResponse(int billsProcessed, int failedCount, List<String> pendingBillReferences) {
    }

    public record PaymentSettlementRequest(@NotBlank String paymentId, @NotBlank String journalNumber,
                                           @NotNull @jakarta.validation.constraints.DecimalMin(value = "0.0001") BigDecimal amount,
                                           @NotBlank @jakarta.validation.constraints.Pattern(regexp = "[A-Z]{3}") String currency,
                                           @NotNull OffsetDateTime settledAt) {}
    public record ClosureEligibilityResponse(String accountId, boolean eligible, List<String> blockingBillIds) {}

    public record Problem(String code, String message, int status, String correlationId) {
    }

    record Product(String code, String currency, String version, String policyVersion, BigDecimal annualRate,
                   BigDecimal minimumPercentage, BigDecimal minimumAmount, int dueDays, List<Fee> fees) {
    }

    record Fee(String type, BigDecimal amount, BigDecimal percentage, String frequency, boolean active) {
    }

    record Card(String accountId, long cifId, String productCode, String status, BigDecimal sanctionedLimit,
                BigDecimal availableLimit, BigDecimal outstanding, OffsetDateTime openedAt) {
    }

    record Activity(String type, String reference, String description, BigDecimal amount, OffsetDateTime occurredAt) {
    }

    record BillingInputs(Product product, Card card, List<Activity> activities) {
    }

    record CalculatedStatement(Product product, Card card, LocalDate periodStart, LocalDate periodEnd,
                               BigDecimal openingBalance, BigDecimal totalDue, BigDecimal minimumDue,
                               LocalDate paymentDueDate, List<Activity> lines) {
    }

    interface UpstreamGateway {
        BillingInputs fetch(String accountId, LocalDate from, LocalDate to);

        void postCalculatedCharges(String billId, String accountId, String productCode,
                                   LocalDate businessDate, String currency, List<Activity> charges);
    }

    @Component
    @ConditionalOnProperty(name = "moneybags.billing.stub-upstream-clients", havingValue = "true", matchIfMissing = true)
    static class StubUpstreamGateway implements UpstreamGateway {
        public BillingInputs fetch(String accountId, LocalDate from, LocalDate to) {
            Product p = new Product("CC-PLAT-001", "INR", "V1", "V1", new BigDecimal("42.000000"), new BigDecimal("5.0000"), new BigDecimal("500.00"), 15, List.of(new Fee("ANNUAL_MEMBERSHIP", new BigDecimal("499.00"), BigDecimal.ZERO, "ANNUALLY", true), new Fee("LATE_PAYMENT", new BigDecimal("750.00"), BigDecimal.ZERO, "ONE_TIME", true)));
            OffsetDateTime when = from.plusDays(2).atStartOfDay().atOffset(ZoneOffset.UTC);
            return new BillingInputs(p, new Card(accountId, 101L, p.code, "ACTIVE", new BigDecimal("100000.00"),
                    new BigDecimal("90000.00"), new BigDecimal("10000.00"), when.minusYears(1)),
                    List.of(new Activity("PURCHASE", "PUR-202608-001", "Seeded card purchase", new BigDecimal("5000.00"), when), new Activity("PAYMENT", "PAY-202608-001", "Seeded card payment", new BigDecimal("-2000.00"), when.plusDays(3))));
        }

        public void postCalculatedCharges(String billId, String accountId, String productCode,
                                          LocalDate date, String currency, List<Activity> charges) {
        }
    }

    @Component
    @ConditionalOnProperty(name = "moneybags.billing.stub-upstream-clients", havingValue = "false")
    static class HttpUpstreamGateway implements UpstreamGateway {
        private final RestClient product;
        private final RestClient card;
        private final RestClient accounting;

        HttpUpstreamGateway(@org.springframework.beans.factory.annotation.Qualifier("billingProductRestClient") RestClient product,
                            @org.springframework.beans.factory.annotation.Qualifier("billingCreditCardRestClient") RestClient card,
                            @org.springframework.beans.factory.annotation.Qualifier("billingAccountingRestClient") RestClient accounting) {
            this.product = product; this.card = card; this.accounting = accounting;
        }

        @SuppressWarnings("unchecked")
        public BillingInputs fetch(String accountId, LocalDate from, LocalDate to) {
            String rawAccountId = accountId.startsWith("CC-") ? accountId.substring(3) : accountId;
            String accountReference = accountId.startsWith("CC-") ? accountId : "CC-" + accountId;
            Map<String, Object> c = card.get().uri("/internal/v1/credit-card-accounts/{id}/billing-details", rawAccountId).retrieve().body(Map.class);
            if (c == null)
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CARD_UNAVAILABLE", "Credit Card returned no account");
            Object cifValue = c.get("cifId");
            if (cifValue == null)
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CARD_CIF_UNAVAILABLE", "Credit Card account response did not include cifId");
            long cifId;
            try {
                cifId = Long.parseLong(cifValue.toString());
            } catch (NumberFormatException ex) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CARD_CIF_INVALID", "Credit Card returned an invalid cifId");
            }
            String productCode = (String) c.get("productCode");
            Map<String, Object> p = product.get().uri("/internal/v1/products/{code}/billing-details", productCode).retrieve().body(Map.class);
            if (p == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_UNAVAILABLE", "Product Master returned no product details");
            Map<String, Object> ir = (Map<String, Object>) p.get("interestRule");
            Map<String, Object> cr = (Map<String, Object>) p.get("creditCardRule");
            List<Map<String, Object>> fs = (List<Map<String, Object>>) p.getOrDefault("fees", List.of());
            List<Fee> fees = fs.stream().map(f -> new Fee((String) f.get("feeType"), decimal(f.get("feeAmount")),
                    decimal(f.get("feePercentage")), (String) f.get("frequency"), Boolean.TRUE.equals(f.get("active")))).toList();
            Map<String, Object> page = accounting.get().uri(b -> b.path("/internal/v1/ledger-entries").queryParam("accountReference", accountReference).queryParam("from", from).queryParam("to", to).queryParam("size", 500).build()).retrieve().body(Map.class);
            List<Map<String, Object>> entries = page == null ? List.of() : (List<Map<String, Object>>) page.getOrDefault("content", List.of());
            List<Activity> acts = entries.stream().map(e -> new Activity(
                    activityType(String.valueOf(e.getOrDefault("eventType", "PURCHASE"))),
                    String.valueOf(e.getOrDefault("journalNumber", UUID.randomUUID())),
                    String.valueOf(e.getOrDefault("narration", "Ledger entry")),
                    decimal(e.get("debitAmount")).subtract(decimal(e.get("creditAmount"))),
                    offsetDateTime(e.get("occurredAt")))).toList();
            return new BillingInputs(new Product(productCode, (String) p.get("currencyCode"), (String) ir.getOrDefault("policyVersion", "V1"), (String) ir.getOrDefault("policyVersion", "V1"), decimal(c.get("purchaseInterestRate")), decimal(cr.get("minimumPaymentPercentage")), decimal(cr.get("minimumPaymentAmount")), ((Number) cr.get("paymentDueDays")).intValue(), fees),
                    new Card(accountReference, cifId, productCode, (String) c.get("status"),
                            decimal(c.get("sanctionedLimit")), decimal(c.get("availableLimit")),
                            decimal(c.get("outstandingAmount")), offsetDateTime(c.get("openedAt"))), acts);
        }

        @SuppressWarnings("unchecked")
        public void postCalculatedCharges(String billId, String accountId, String productCode,
                                          LocalDate date, String currency, List<Activity> charges) {
            if (charges.isEmpty()) return;
            String correlationId = Optional.ofNullable(MDC.get("correlationId")).orElseGet(() -> UUID.randomUUID().toString());
            Map<String, Object> journal = accounting.post().uri("/internal/v1/bill-postings")
                    .header("Idempotency-Key", "BILL-CHARGES:" + billId)
                    .header("X-Correlation-Id", correlationId)
                    .body(Map.of("billId", billId, "accountId", accountId, "productCode", productCode,
                            "billingPeriodStart", date.withDayOfMonth(1),
                            "billingPeriodEnd", date.withDayOfMonth(date.lengthOfMonth()), "businessDate", date,
                            "occurredAt", OffsetDateTime.now(ZoneOffset.UTC), "currencyCode", currency,
                            "components", charges.stream().map(c -> Map.of("componentType", c.type(),
                                    "amount", c.amount(), "description", c.description())).toList()))
                    .retrieve().body(Map.class);
            if (journal == null || journal.get("journalNumber") == null) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ACCOUNTING_POSTING_INCOMPLETE",
                        "Accounting did not return a journal number for the bill charges");
            }
            String rawAccountId = accountId.startsWith("CC-") ? accountId.substring(3) : accountId;
            BigDecimal totalCharges = charges.stream().map(Activity::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            card.post().uri("/internal/v1/credit-card-accounts/{id}/billing-charges", rawAccountId)
                    .header("Idempotency-Key", billId)
                    .body(Map.of("billId", billId, "journalNumber", journal.get("journalNumber").toString(),
                            "amount", totalCharges, "currency", currency))
                    .retrieve().toBodilessEntity();
        }

        private static String activityType(String eventType) {
            String normalized = eventType == null ? "PURCHASE" : eventType.toUpperCase(Locale.ROOT);
            if (normalized.contains("REPAYMENT") || normalized.contains("PAYMENT")) return "PAYMENT";
            if (normalized.contains("REFUND")) return "REFUND";
            if (normalized.contains("REVERSAL")) return "REVERSAL";
            return "PURCHASE";
        }

        private static OffsetDateTime offsetDateTime(Object value) {
            if (value == null) return OffsetDateTime.now(ZoneOffset.UTC);
            try { return OffsetDateTime.parse(value.toString()); }
            catch (DateTimeException ignored) { return OffsetDateTime.now(ZoneOffset.UTC); }
        }

        private static BigDecimal decimal(Object n) {
            return n == null ? BigDecimal.ZERO : new BigDecimal(n.toString());
        }
    }

    @Service
    public static class BillingService {
        @PersistenceContext
        EntityManager em;
        private final UpstreamGateway upstream;
        private final ObjectMapper json;
        private final NotificationClient notifications;

        public BillingService(UpstreamGateway upstream, ObjectMapper json, NotificationClient notifications) {
            this.upstream = upstream;
            this.json = json;
            this.notifications = notifications;
        }

        @Transactional
        public BillResponse generate(String key, GenerateRequest request) {
            if (!request.billingPeriod.matches("\\d{4}-\\d{2}"))
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BILLING_PERIOD", "billingPeriod must be YYYY-MM");
            String accountReference = canonicalCardAccountReference(request.accountId);
            YearMonth ym = YearMonth.parse(request.billingPeriod);
            return generateCalculated(key, accountReference, request.billingPeriod, ym.atDay(1), ym.atEndOfMonth(),
                    request.businessDate, true, null);
        }

        @Transactional(readOnly = true)
        public StatementPreview previewForCustomer(long cifId, CustomerStatementRequest request) {
            validateStatementPeriod(request.startDate, request.endDate);
            String accountReference = canonicalCardAccountReference(request.accountId);
            String period = statementPeriod(request.startDate, request.endDate);
            List<Bill> duplicates = duplicateBills(accountReference, period);
            CalculatedStatement calculated = calculate(accountReference, request.startDate, request.endDate,
                    request.endDate);
            requireOwnership(calculated, cifId);
            return toPreview(calculated, !duplicates.isEmpty(), duplicates.isEmpty() ? null : duplicates.getFirst().id);
        }

        @Transactional
        public BillResponse generateForCustomer(String key, long cifId, CustomerStatementRequest request) {
            validateStatementPeriod(request.startDate, request.endDate);
            String accountReference = canonicalCardAccountReference(request.accountId);
            CalculatedStatement calculated = calculate(accountReference, request.startDate, request.endDate,
                    request.endDate);
            requireOwnership(calculated, cifId);
            return persistCalculated(key, accountReference, statementPeriod(request.startDate, request.endDate),
                    request.endDate, request.saveToHistory, calculated);
        }

        @Transactional(readOnly = true)
        public StatementPreview previewForAdmin(AdminStatementRequest request) {
            validateStatementPeriod(request.startDate, request.endDate);
            String accountReference = canonicalCardAccountReference(request.accountId);
            String period = statementPeriod(request.startDate, request.endDate);
            List<Bill> duplicates = duplicateBills(accountReference, period);
            CalculatedStatement calculated = calculate(accountReference, request.startDate, request.endDate,
                    request.endDate);
            requireOwnership(calculated, request.cifId);
            return toPreview(calculated, !duplicates.isEmpty(), duplicates.isEmpty() ? null : duplicates.getFirst().id);
        }

        @Transactional
        public BillResponse generateForAdmin(String key, AdminStatementRequest request) {
            validateStatementPeriod(request.startDate, request.endDate);
            String accountReference = canonicalCardAccountReference(request.accountId);
            CalculatedStatement calculated = calculate(accountReference, request.startDate, request.endDate,
                    request.endDate);
            requireOwnership(calculated, request.cifId);
            // Operations-generated statements are regulated records and are always retained.
            return persistCalculated(key, accountReference, statementPeriod(request.startDate, request.endDate),
                    request.endDate, true, calculated);
        }

        private BillResponse generateCalculated(String key, String accountReference, String period,
                                                 LocalDate start, LocalDate end, LocalDate businessDate,
                                                 boolean saveToHistory, Long requiredCifId) {
            CalculatedStatement calculated = calculate(accountReference, start, end, businessDate);
            if (requiredCifId != null) requireOwnership(calculated, requiredCifId);
            return persistCalculated(key, accountReference, period, businessDate, saveToHistory, calculated);
        }

        private BillResponse persistCalculated(String key, String accountReference, String period,
                                               LocalDate businessDate, boolean saveToHistory,
                                               CalculatedStatement calculated) {
            String keyHash = sha(key), requestHash = sha(accountReference + "|" + period + "|" + businessDate + "|" + saveToHistory);
            List<Idempotency> known = em.createQuery("select i from Idempotency i where i.scope=:s and i.keyHash=:k", Idempotency.class).setParameter("s", "BILL_GENERATE").setParameter("k", keyHash).getResultList();
            if (!known.isEmpty()) {
                if (!known.getFirst().requestHash.equals(requestHash))
                    throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key was used with a different request");
                return get(known.getFirst().resourceId);
            }
            List<Bill> duplicates = duplicateBills(accountReference, period);
            if (!duplicates.isEmpty())
                throw new ApiException(HttpStatus.CONFLICT, "BILL_ALREADY_EXISTS", "A bill already exists for this account and period");
            String billId = UUID.randomUUID().toString();
            Bill bill = new Bill(billId, accountReference, calculated.card.cifId, calculated.product.code, period,
                    calculated.periodStart, calculated.periodEnd, businessDate, calculated.product.currency,
                    calculated.openingBalance, calculated.totalDue, calculated.minimumDue,
                    calculated.paymentDueDate, saveToHistory);
            em.persist(bill);
            calculated.lines.forEach(l -> em.persist(new BillLine(billId, l)));
            em.persist(new BillSnapshot(billId, calculated.product, write(calculated.product.fees)));
            em.persist(new BillHistory(billId));
            em.persist(new Idempotency("BILL_GENERATE", keyHash, requestHash, billId));
            em.persist(new Audit(billId));
            em.flush();
            upstream.postCalculatedCharges(billId, accountReference, calculated.product.code, businessDate,
                    calculated.product.currency, calculated.lines.stream()
                            .filter(l -> Set.of("INTEREST", "LATE_FEE", "ANNUAL_FEE", "PENALTY", "TAX")
                                    .contains(l.type)).toList());
            notifications.sendBillGenerated(calculated.card.cifId, billId, period,
                    calculated.product.currency, calculated.totalDue, bill.paymentDueDate);
            return toResponse(bill, calculated.lines);
        }

        private CalculatedStatement calculate(String accountReference, LocalDate start, LocalDate end,
                                              LocalDate businessDate) {
            BillingInputs inputs = upstream.fetch(accountReference, start, end);
            if (!"ACTIVE".equals(inputs.card.status))
                throw new ApiException(HttpStatus.CONFLICT, "CARD_NOT_ACTIVE", "Credit card is not active");
            List<Bill> prior = em.createQuery("select b from Bill b where b.accountId=:a and b.businessDate<:d order by b.businessDate desc", Bill.class)
                    .setParameter("a", accountReference).setParameter("d", start).setMaxResults(1).getResultList();
            BigDecimal currentActivity = inputs.activities.stream().map(Activity::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal previous = prior.isEmpty()
                    ? inputs.card.outstanding.subtract(currentActivity).max(BigDecimal.ZERO)
                    : prior.getFirst().totalDue.subtract(prior.getFirst().paidAmount).max(BigDecimal.ZERO);
            String period = statementPeriod(start, end);
            List<Activity> lines = new ArrayList<>();
            lines.add(new Activity("PREVIOUS_BALANCE", "OPENING-" + period, "Previous statement balance", previous,
                    start.atStartOfDay().atOffset(ZoneOffset.UTC)));
            lines.addAll(inputs.activities);
            BigDecimal net = previous.add(inputs.activities.stream().map(a -> a.amount).reduce(BigDecimal.ZERO, BigDecimal::add));
            long days = ChronoUnit.DAYS.between(start, end) + 1;
            BigDecimal interest = previous.max(BigDecimal.ZERO).multiply(inputs.product.annualRate)
                    .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(days))
                    .divide(new BigDecimal("365"), 4, RoundingMode.HALF_UP);
            if (interest.signum() > 0)
                lines.add(new Activity("INTEREST", "INTEREST-" + period, "Daily-balance finance charge", interest,
                        end.atStartOfDay().atOffset(ZoneOffset.UTC)));
            net = net.add(interest);
            if (!prior.isEmpty() && prior.getFirst().paymentDueDate.isBefore(businessDate) && prior.getFirst().totalDue.signum() > 0) {
                Fee late = inputs.product.fees.stream().filter(f -> f.active && "LATE_PAYMENT".equals(f.type)).findFirst().orElse(null);
                if (late != null) {
                    net = net.add(late.amount);
                    lines.add(new Activity("LATE_FEE", "LATE-" + period, "Late-payment fee", late.amount,
                            end.atStartOfDay().atOffset(ZoneOffset.UTC)));
                }
            }
            Fee membership = inputs.product.fees.stream()
                    .filter(f -> f.active && "ANNUAL_MEMBERSHIP".equals(f.type) && "ANNUALLY".equals(f.frequency)
                            && inputs.card.openedAt != null && inputs.card.openedAt.getMonth() == start.getMonth())
                    .findFirst().orElse(null);
            if (membership != null && membership.amount.signum() > 0) {
                net = net.add(membership.amount);
                lines.add(new Activity("ANNUAL_FEE", "ANNUAL-" + period, "Annual membership fee", membership.amount,
                        end.atStartOfDay().atOffset(ZoneOffset.UTC)));
            }
            BigDecimal taxableCharges = lines.stream()
                    .filter(l -> Set.of("INTEREST", "LATE_FEE", "ANNUAL_FEE", "PENALTY").contains(l.type))
                    .map(Activity::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            Fee taxRule = inputs.product.fees.stream().filter(f -> f.active && "TAX".equals(f.type)).findFirst().orElse(null);
            if (taxRule != null) {
                BigDecimal tax = taxRule.amount.add(taxableCharges.multiply(taxRule.percentage)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP);
                if (tax.signum() > 0) {
                    net = net.add(tax);
                    lines.add(new Activity("TAX", "TAX-" + period, "Tax on finance charges and fees", tax,
                            end.atStartOfDay().atOffset(ZoneOffset.UTC)));
                }
            }
            net = net.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal minimum = net.multiply(inputs.product.minimumPercentage).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                    .max(inputs.product.minimumAmount).min(net);
            return new CalculatedStatement(inputs.product, inputs.card, start, end, previous, net, minimum,
                    end.plusDays(inputs.product.dueDays), List.copyOf(lines));
        }

        @Transactional(readOnly = true)
        public BillResponse get(String id) {
            Bill b = em.find(Bill.class, id);
            if (b == null) throw new ApiException(HttpStatus.NOT_FOUND, "BILL_NOT_FOUND", "Bill was not found");
            return toResponse(b, em.createQuery("select l from BillLine l where l.billId=:b order by l.occurredAt", BillLine.class).setParameter("b", id).getResultList().stream().map(l -> new Activity(l.type, l.reference, l.description, l.amount, l.occurredAt)).toList());
        }

        @Transactional(readOnly = true)
        public BillResponse getForCustomer(String id, long cifId) {
            List<Bill> owned = em.createQuery("select b from Bill b where b.id=:id and b.cifId=:cif", Bill.class)
                    .setParameter("id", id).setParameter("cif", cifId).getResultList();
            if (owned.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "BILL_NOT_FOUND", "Bill was not found");
            return get(id);
        }

        @Transactional
        public BillSummaryResponse settlePayment(String billId, PaymentSettlementRequest request) {
            Bill bill = em.find(Bill.class, billId);
            if (bill == null) throw new ApiException(HttpStatus.NOT_FOUND, "BILL_NOT_FOUND", "Bill was not found");
            if (!bill.currency.trim().equals(request.currency)) throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENCY_MISMATCH", "Settlement currency does not match bill currency");
            List<BillPaymentAllocation> existing = em.createQuery("select a from BillPaymentAllocation a where a.paymentId=:p", BillPaymentAllocation.class).setParameter("p", request.paymentId).getResultList();
            if (!existing.isEmpty()) {
                BillPaymentAllocation allocation = existing.getFirst();
                if (!allocation.billId.equals(billId) || allocation.amount.compareTo(request.amount) != 0 || !allocation.journalNumber.equals(request.journalNumber)) {
                    throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_SETTLEMENT_CONFLICT", "paymentId was already used for a different settlement");
                }
                return summary(bill);
            }
            BigDecimal outstanding = bill.totalDue.subtract(bill.paidAmount);
            if (request.amount.compareTo(outstanding) > 0) throw new ApiException(HttpStatus.CONFLICT, "OVERPAYMENT_NOT_SUPPORTED", "Settlement amount exceeds the outstanding bill amount");
            String before = bill.status;
            bill.paidAmount = bill.paidAmount.add(request.amount).setScale(4, RoundingMode.HALF_UP);
            bill.status = bill.paidAmount.compareTo(bill.totalDue) >= 0 ? "PAID" : "PARTIALLY_PAID";
            em.persist(new BillPaymentAllocation(billId, request));
            if (!before.equals(bill.status)) em.persist(new BillHistory(billId, before, bill.status, "PAYMENT_SETTLED"));
            em.persist(new Audit(billId, "BILL_PAYMENT_SETTLED"));
            return summary(bill);
        }

        @Transactional(readOnly = true)
        public BillPage search(String account, String period, String status, int page, int size) {
            String q = "select b from Bill b where (:a is null or b.accountId=:a) and (:p is null or b.billingPeriod=:p) and (:s is null or b.status=:s) order by b.generatedAt desc";
            List<Bill> all = em.createQuery(q, Bill.class).setParameter("a", account).setParameter("p", period).setParameter("s", status).getResultList();
            int from = Math.min(page * size, all.size()), to = Math.min(from + size, all.size());
            return new BillPage(all.subList(from, to).stream().map(b -> get(b.id)).toList(), page, size, all.size());
        }

        @Transactional(readOnly = true)
        public BillPage searchForCustomer(long cifId, String account, String period, String status, int page, int size) {
            String canonicalAccount = account == null || account.isBlank() ? null : canonicalCardAccountReference(account);
            String q = "select b from Bill b where b.cifId=:cif and b.savedToHistory=true and (:a is null or b.accountId=:a) and (:p is null or b.billingPeriod=:p) and (:s is null or b.status=:s) order by b.generatedAt desc";
            List<Bill> all = em.createQuery(q, Bill.class).setParameter("cif", cifId).setParameter("a", canonicalAccount)
                    .setParameter("p", period).setParameter("s", status).getResultList();
            int from = Math.min(page * size, all.size()), to = Math.min(from + size, all.size());
            return new BillPage(all.subList(from, to).stream().map(b -> get(b.id)).toList(), page, size, all.size());
        }

        @Transactional(readOnly = true)
        public CloseResponse close(CloseRequest request) {
            List<Bill> overdue = em.createQuery("select b from Bill b where b.paymentDueDate < :d and b.status in ('GENERATED', 'PARTIALLY_PAID')", Bill.class).setParameter("d", request.businessDate).getResultList();
            overdue.forEach(b -> { String before = b.status; b.status = "OVERDUE"; em.persist(new BillHistory(b.id, before, "OVERDUE", "PAYMENT_DUE_DATE_PASSED")); });
            Long count = em.createQuery("select count(b) from Bill b where b.businessDate=:d", Long.class).setParameter("d", request.businessDate).getSingleResult();
            return new CloseResponse(count.intValue(), 0, List.of());
        }

        @Transactional(readOnly = true)
        public ClosureEligibilityResponse closureEligibility(String accountId) {
            List<Bill> blocking = em.createQuery("select b from Bill b where b.accountId=:a and b.status <> 'PAID' order by b.billingPeriod", Bill.class).setParameter("a", accountId).getResultList();
            return new ClosureEligibilityResponse(accountId, blocking.isEmpty(), blocking.stream().map(b -> b.id).toList());
        }

        private BillResponse toResponse(Bill b, List<Activity> lines) {
            BigDecimal outstanding = b.totalDue.subtract(b.paidAmount).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
            LocalDate start = b.periodStart != null ? b.periodStart : periodStart(b.billingPeriod, b.businessDate);
            LocalDate end = b.periodEnd != null ? b.periodEnd : periodEnd(b.billingPeriod, b.businessDate);
            return new BillResponse(b.id, b.accountId, b.billingPeriod, b.status, b.previousBalance, b.totalDue,
                    b.minimumDue, b.paidAmount, outstanding, b.paymentDueDate, b.currency,
                    lines.stream().map(l -> new BillLineResponse(l.type, l.reference, l.description, l.amount, l.occurredAt)).toList(),
                    b.productCode, start, end, b.generatedAt, b.savedToHistory, pdfPassword(b.accountId, start));
        }

        private BillSummaryResponse summary(Bill b) {
            BigDecimal outstanding = b.totalDue.subtract(b.paidAmount).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
            return new BillSummaryResponse(b.id, b.accountId, b.billingPeriod, b.totalDue, b.minimumDue, b.paidAmount, outstanding, b.status, b.paymentDueDate);
        }

        private String write(Object value) {
            try {
                return json.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }

        private static String sha(String value) {
            try {
                byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(bytes);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private List<Bill> duplicateBills(String accountReference, String period) {
            return em.createQuery("select b from Bill b where b.accountId=:a and b.billingPeriod=:p", Bill.class)
                    .setParameter("a", accountReference).setParameter("p", period).getResultList();
        }

        private static void requireOwnership(CalculatedStatement calculated, long cifId) {
            if (calculated.card.cifId != cifId)
                throw new ApiException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "The selected card account was not found");
        }

        private static void validateStatementPeriod(LocalDate start, LocalDate end) {
            if (start.isAfter(end))
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATEMENT_PERIOD", "Start date must be on or before end date");
            if (end.isAfter(LocalDate.now()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "FUTURE_STATEMENT_PERIOD", "Billing statements cannot include future dates");
            if (ChronoUnit.DAYS.between(start, end) + 1 > 366)
                throw new ApiException(HttpStatus.BAD_REQUEST, "STATEMENT_PERIOD_TOO_LONG", "Billing period cannot exceed 366 days");
        }

        private static String statementPeriod(LocalDate start, LocalDate end) {
            YearMonth month = YearMonth.from(start);
            return start.equals(month.atDay(1)) && end.equals(month.atEndOfMonth())
                    ? month.toString() : start + "_" + end;
        }

        private StatementPreview toPreview(CalculatedStatement value, boolean duplicate, String existingBillId) {
            BigDecimal payments = value.lines.stream().filter(line -> "PAYMENT".equals(line.type))
                    .map(line -> line.amount.abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal fees = lineTotal(value.lines, "LATE_FEE", "ANNUAL_FEE", "PENALTY", "FEE");
            BigDecimal taxes = lineTotal(value.lines, "TAX");
            BigDecimal finance = lineTotal(value.lines, "INTEREST", "FINANCE_CHARGE");
            BigDecimal purchases = value.lines.stream()
                    .filter(line -> "PURCHASE".equals(line.type))
                    .map(line -> line.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new StatementPreview(value.card.accountId, value.product.code, value.periodStart, value.periodEnd,
                    value.openingBalance, payments, purchases, fees, taxes, finance, value.minimumDue,
                    value.totalDue, value.paymentDueDate, value.product.currency,
                    value.lines.stream().map(line -> new BillLineResponse(line.type, line.reference, line.description, line.amount, line.occurredAt)).toList(),
                    duplicate, existingBillId);
        }

        private static BigDecimal lineTotal(List<Activity> lines, String... types) {
            Set<String> wanted = Set.of(types);
            return lines.stream().filter(line -> wanted.contains(line.type)).map(line -> line.amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        private static LocalDate periodStart(String period, LocalDate fallback) {
            try { return YearMonth.parse(period).atDay(1); }
            catch (Exception ignored) { return fallback.withDayOfMonth(1); }
        }

        private static LocalDate periodEnd(String period, LocalDate fallback) {
            try { return YearMonth.parse(period).atEndOfMonth(); }
            catch (Exception ignored) { return fallback; }
        }

        private static String pdfPassword(String accountId, LocalDate start) {
            String digits = accountId.replaceAll("\\D", "");
            String suffix = digits.length() >= 4 ? digits.substring(digits.length() - 4) : String.format("%04d", Math.abs(accountId.hashCode()) % 10000);
            return "MB" + suffix + String.format("%04d%02d", start.getYear(), start.getMonthValue());
        }

        private static String canonicalCardAccountReference(String accountId) {
            return accountId.startsWith("CC-") ? accountId : "CC-" + accountId;
        }
    }

    public static class ApiException extends RuntimeException {
        final HttpStatus status;
        final String code;

        public ApiException(HttpStatus s, String c, String m) {
            super(m);
            status = s;
            code = c;
        }
    }

    @RestControllerAdvice
    static class Errors {
        @ExceptionHandler(ApiException.class)
        ResponseEntity<Problem> api(ApiException e) {
            return problem(e.status, e.code, e.getMessage());
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        ResponseEntity<Problem> validation() {
            return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        ResponseEntity<Problem> conflict() {
            return problem(HttpStatus.CONFLICT, "CONSTRAINT_CONFLICT", "The request conflicts with existing data");
        }

        @ExceptionHandler(RestClientException.class)
        ResponseEntity<Problem> peer(RestClientException e) {
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_SERVICE_UNAVAILABLE",
                    "A required MoneyBags service is temporarily unavailable");
        }

        private ResponseEntity<Problem> problem(HttpStatus s, String code, String m) {
            return ResponseEntity.status(s).body(new Problem(code, m, s.value(), MDC.get("correlationId")));
        }
    }

    @Component
    static class CorrelationIdFilter extends OncePerRequestFilter {
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
            String id = Optional.ofNullable(req.getHeader("X-Correlation-Id")).filter(v -> v.matches("[A-Za-z0-9._:-]{1,64}")).orElseGet(() -> UUID.randomUUID().toString());
            MDC.put("correlationId", id);
            res.setHeader("X-Correlation-Id", id);
            try {
                chain.doFilter(req, res);
            } finally {
                MDC.remove("correlationId");
            }
        }
    }

    @Configuration
    @EnableMethodSecurity
    static class Security {
        @Bean
        @ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "false", matchIfMissing = true)
        SecurityFilterChain local(HttpSecurity http) throws Exception {
            return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
        }

        @Bean
        @ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "true")
        SecurityFilterChain secured(HttpSecurity http) throws Exception {
            return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a
                    .requestMatchers("/actuator/health/**").permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").hasRole("BANK_ADMIN")
                    .requestMatchers("/internal/**").hasAuthority("SCOPE_billing:service")
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/bills/**")
                    .hasAnyAuthority("SCOPE_billing:read", "SCOPE_billing:admin")
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/bills", "/api/v1/bills/preview")
                    .hasAuthority("SCOPE_billing:read")
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/bills/admin/**")
                    .hasAuthority("SCOPE_billing:admin")
                    .anyRequest().denyAll())
                    .oauth2ResourceServer(o -> o.jwt(j -> { })).build();
        }
    }
}
