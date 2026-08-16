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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
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
        @Column(name = "ACCOUNT_ID", nullable = false)
        String accountId;
        @Column(name = "PRODUCT_CODE", nullable = false)
        String productCode;
        @Column(name = "BILLING_PERIOD", nullable = false)
        String billingPeriod;
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

        protected Bill() {
        }

        Bill(String id, String accountId, String productCode, String period, LocalDate date, String currency, BigDecimal previous, BigDecimal total, BigDecimal minimum, LocalDate due) {
            this.id = id;
            this.accountId = accountId;
            this.productCode = productCode;
            this.billingPeriod = period;
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
                               String currency, List<BillLineResponse> lines) {
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

    record Fee(String type, BigDecimal amount, String frequency, boolean active) {
    }

    record Card(String accountId, long cifId, String productCode, String status, BigDecimal outstanding) {
    }

    record Activity(String type, String reference, String description, BigDecimal amount, OffsetDateTime occurredAt) {
    }

    record BillingInputs(Product product, Card card, List<Activity> activities) {
    }

    interface UpstreamGateway {
        BillingInputs fetch(String accountId, LocalDate from, LocalDate to);

        void postCalculatedCharges(String billId, String accountId, LocalDate businessDate, String currency, List<Activity> charges);
    }

    @Component
    @ConditionalOnProperty(name = "moneybags.billing.stub-upstream-clients", havingValue = "true", matchIfMissing = true)
    static class StubUpstreamGateway implements UpstreamGateway {
        public BillingInputs fetch(String accountId, LocalDate from, LocalDate to) {
            Product p = new Product("CC-PLAT-001", "INR", "V1", "V1", new BigDecimal("42.000000"), new BigDecimal("5.0000"), new BigDecimal("500.00"), 15, List.of(new Fee("ANNUAL_MEMBERSHIP", new BigDecimal("499.00"), "ANNUALLY", true), new Fee("LATE_PAYMENT", new BigDecimal("750.00"), "ONE_TIME", true)));
            OffsetDateTime when = from.plusDays(2).atStartOfDay().atOffset(ZoneOffset.UTC);
            return new BillingInputs(p, new Card(accountId, 101L, p.code, "ACTIVE", new BigDecimal("10000.00")), List.of(new Activity("PURCHASE", "PUR-202608-001", "Seeded card purchase", new BigDecimal("5000.00"), when), new Activity("PAYMENT", "PAY-202608-001", "Seeded card payment", new BigDecimal("-2000.00"), when.plusDays(3))));
        }

        public void postCalculatedCharges(String billId, String accountId, LocalDate date, String currency, List<Activity> charges) {
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
            Map<String, Object> c = card.get().uri("/internal/v1/credit-card-accounts/{id}/billing-details", accountId).retrieve().body(Map.class);
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
            List<Fee> fees = fs.stream().map(f -> new Fee((String) f.get("feeType"), decimal(f.get("feeAmount")), (String) f.get("frequency"), Boolean.TRUE.equals(f.get("active")))).toList();
            Map<String, Object> page = accounting.get().uri(b -> b.path("/internal/v1/ledger-entries").queryParam("accountReference", "CC-" + accountId).queryParam("from", from).queryParam("to", to).queryParam("size", 500).build()).retrieve().body(Map.class);
            List<Map<String, Object>> entries = page == null ? List.of() : (List<Map<String, Object>>) page.getOrDefault("content", List.of());
            List<Activity> acts = entries.stream().map(e -> new Activity(String.valueOf(e.getOrDefault("eventType", "PURCHASE")), String.valueOf(e.getOrDefault("journalNumber", UUID.randomUUID())), String.valueOf(e.getOrDefault("narration", "Ledger entry")), decimal(e.get("debitAmount")).subtract(decimal(e.get("creditAmount"))).abs(), OffsetDateTime.now(ZoneOffset.UTC))).toList();
            return new BillingInputs(new Product(productCode, (String) p.get("currencyCode"), (String) ir.getOrDefault("policyVersion", "V1"), (String) ir.getOrDefault("policyVersion", "V1"), decimal(c.get("purchaseInterestRate")), decimal(cr.get("minimumPaymentPercentage")), decimal(cr.get("minimumPaymentAmount")), ((Number) cr.get("paymentDueDays")).intValue(), fees), new Card(accountId, cifId, productCode, (String) c.get("status"), decimal(c.get("outstandingAmount"))), acts);
        }

        public void postCalculatedCharges(String billId, String accountId, LocalDate date, String currency, List<Activity> charges) {
            if (charges.isEmpty()) return;
            accounting.post().uri("/internal/v1/bill-postings").body(Map.of("billId", billId, "accountId", accountId, "billingPeriodStart", date.withDayOfMonth(1), "billingPeriodEnd", date.withDayOfMonth(date.lengthOfMonth()), "businessDate", date, "occurredAt", OffsetDateTime.now(ZoneOffset.UTC), "currencyCode", currency, "components", charges.stream().map(c -> Map.of("componentType", c.type(), "amount", c.amount(), "description", c.description())).toList())).retrieve().toBodilessEntity();
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
            String keyHash = sha(key), requestHash = sha(request.accountId + "|" + request.billingPeriod + "|" + request.businessDate);
            List<Idempotency> known = em.createQuery("select i from Idempotency i where i.scope=:s and i.keyHash=:k", Idempotency.class).setParameter("s", "BILL_GENERATE").setParameter("k", keyHash).getResultList();
            if (!known.isEmpty()) {
                if (!known.getFirst().requestHash.equals(requestHash))
                    throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key was used with a different request");
                return get(known.getFirst().resourceId);
            }
            List<Bill> duplicates = em.createQuery("select b from Bill b where b.accountId=:a and b.billingPeriod=:p", Bill.class).setParameter("a", request.accountId).setParameter("p", request.billingPeriod).getResultList();
            if (!duplicates.isEmpty())
                throw new ApiException(HttpStatus.CONFLICT, "BILL_ALREADY_EXISTS", "A bill already exists for this account and period");
            YearMonth ym = YearMonth.parse(request.billingPeriod);
            BillingInputs inputs = upstream.fetch(request.accountId, ym.atDay(1), ym.atEndOfMonth());
            if (!"ACTIVE".equals(inputs.card.status))
                throw new ApiException(HttpStatus.CONFLICT, "CARD_NOT_ACTIVE", "Credit card is not active");
            List<Bill> prior = em.createQuery("select b from Bill b where b.accountId=:a and b.billingPeriod<:p order by b.billingPeriod desc", Bill.class).setParameter("a", request.accountId).setParameter("p", request.billingPeriod).setMaxResults(1).getResultList();
            BigDecimal previous = prior.isEmpty() ? inputs.card.outstanding : prior.getFirst().totalDue.subtract(prior.getFirst().paidAmount).max(BigDecimal.ZERO);
            List<Activity> lines = new ArrayList<>();
            lines.add(new Activity("PREVIOUS_BALANCE", "OPENING-" + request.billingPeriod, "Previous statement balance", previous, request.businessDate.atStartOfDay().atOffset(ZoneOffset.UTC)));
            lines.addAll(inputs.activities);
            BigDecimal net = previous.add(inputs.activities.stream().map(a -> a.amount).reduce(BigDecimal.ZERO, BigDecimal::add));
            int days = ym.lengthOfMonth();
            BigDecimal interest = previous.max(BigDecimal.ZERO).multiply(inputs.product.annualRate).divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(days)).divide(new BigDecimal("365"), 4, RoundingMode.HALF_UP);
            if (interest.signum() > 0)
                lines.add(new Activity("INTEREST", "INTEREST-" + request.billingPeriod, "Monthly daily-balance interest", interest, request.businessDate.atStartOfDay().atOffset(ZoneOffset.UTC)));
            net = net.add(interest);
            BigDecimal fees = BigDecimal.ZERO;
            if (!prior.isEmpty() && prior.getFirst().paymentDueDate.isBefore(request.businessDate) && prior.getFirst().totalDue.signum() > 0) {
                Fee late = inputs.product.fees.stream().filter(f -> f.active && "LATE_PAYMENT".equals(f.type)).findFirst().orElse(null);
                if (late != null) {
                    fees = late.amount;
                    lines.add(new Activity("FEE", "LATE-" + request.billingPeriod, "Late-payment fee", fees, request.businessDate.atStartOfDay().atOffset(ZoneOffset.UTC)));
                }
            }
            net = net.add(fees).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
            BigDecimal minimum = net.multiply(inputs.product.minimumPercentage).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP).max(inputs.product.minimumAmount).min(net);
            String billId = UUID.randomUUID().toString();
            Bill bill = new Bill(billId, request.accountId, inputs.product.code, request.billingPeriod, request.businessDate, inputs.product.currency, previous, net, minimum, request.businessDate.plusDays(inputs.product.dueDays));
            em.persist(bill);
            lines.forEach(l -> em.persist(new BillLine(billId, l)));
            em.persist(new BillSnapshot(billId, inputs.product, write(inputs.product.fees)));
            em.persist(new BillHistory(billId));
            em.persist(new Idempotency("BILL_GENERATE", keyHash, requestHash, billId));
            em.persist(new Audit(billId));
            em.flush();
            upstream.postCalculatedCharges(billId, request.accountId, request.businessDate, inputs.product.currency, lines.stream().filter(l -> "INTEREST".equals(l.type) || "FEE".equals(l.type)).toList());
            notifications.sendBillGenerated(inputs.card.cifId, billId, request.billingPeriod,
                    inputs.product.currency, net, bill.paymentDueDate);
            return toResponse(bill, lines);
        }

        @Transactional(readOnly = true)
        public BillResponse get(String id) {
            Bill b = em.find(Bill.class, id);
            if (b == null) throw new ApiException(HttpStatus.NOT_FOUND, "BILL_NOT_FOUND", "Bill was not found");
            return toResponse(b, em.createQuery("select l from BillLine l where l.billId=:b order by l.occurredAt", BillLine.class).setParameter("b", id).getResultList().stream().map(l -> new Activity(l.type, l.reference, l.description, l.amount, l.occurredAt)).toList());
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
            return new BillResponse(b.id, b.accountId, b.billingPeriod, b.status, b.previousBalance, b.totalDue, b.minimumDue, b.paidAmount, outstanding, b.paymentDueDate, b.currency, lines.stream().map(l -> new BillLineResponse(l.type, l.reference, l.description, l.amount, l.occurredAt)).toList());
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
                    .anyRequest().denyAll())
                    .oauth2ResourceServer(o -> o.jwt(j -> { })).build();
        }
    }
}
