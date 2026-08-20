package com.moneybags.billing;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class BillGenerationApplicationTest {
    @Autowired BillGenerationApplication.BillingService service;
    @Autowired StatementPdfRenderer pdfRenderer;
    @PersistenceContext EntityManager em;

    @Test
    void generatesAndReplaysAnIdempotentBill() {
        var request = new BillGenerationApplication.GenerateRequest(
                "11111111-1111-1111-1111-111111111111", "2026-08", LocalDate.of(2026, 8, 13));

        var generated = service.generate("test-bill-generation-001", request);
        var replay = service.generate("test-bill-generation-001", request);

        assertThat(generated.billId()).isEqualTo(replay.billId());
        assertThat(generated.totalAmountDue()).isPositive();
        assertThat(generated.minimumAmountDue()).isLessThanOrEqualTo(generated.totalAmountDue());
        assertThat(generated.lines()).extracting(BillGenerationApplication.BillLineResponse::lineType)
                .contains("PREVIOUS_BALANCE", "PURCHASE", "PAYMENT", "INTEREST", "ANNUAL_FEE");
        assertThat(generated.accountId()).startsWith("CC-");
        assertThat(service.searchForCustomer(101L, null, "2026-08", null, 0, 20).content())
                .extracting(BillGenerationApplication.BillResponse::billId).contains(generated.billId());
        assertThat(service.searchForCustomer(999L, null, "2026-08", null, 0, 20).content()).isEmpty();
    }

    @Test
    void recordsASettlementAndAllowsClosureOnlyAfterFullPayment() {
        var bill = service.generate("test-bill-generation-002", new BillGenerationApplication.GenerateRequest(
                "22222222-2222-2222-2222-222222222222", "2026-09", LocalDate.of(2026, 9, 13)));

        var partial = service.settlePayment(bill.billId(), new BillGenerationApplication.PaymentSettlementRequest(
                "33333333-3333-3333-3333-333333333333", "JRN-001", new java.math.BigDecimal("100.00"), "INR",
                OffsetDateTime.of(2026, 9, 13, 12, 0, 0, 0, ZoneOffset.UTC)));

        assertThat(partial.status()).isEqualTo("PARTIALLY_PAID");
        assertThat(service.closureEligibility(bill.accountId()).eligible()).isFalse();

        var paid = service.settlePayment(bill.billId(), new BillGenerationApplication.PaymentSettlementRequest(
                "44444444-4444-4444-4444-444444444444", "JRN-002", partial.outstandingAmount(), "INR",
                OffsetDateTime.of(2026, 9, 13, 12, 1, 0, 0, ZoneOffset.UTC)));

        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(paid.outstandingAmount()).isZero();
        assertThat(service.closureEligibility(bill.accountId()).eligible()).isTrue();
    }

    @Test
    void previewsAndGeneratesAProtectedCustomerStatement() throws java.io.IOException {
        var request = new BillGenerationApplication.CustomerStatementRequest(
                "33333333-3333-3333-3333-333333333333",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), true);

        var preview = service.previewForCustomer(101L, request);
        assertThat(preview.duplicate()).isFalse();
        assertThat(preview.lines()).extracting(BillGenerationApplication.BillLineResponse::lineType)
                .contains("PURCHASE", "PAYMENT", "INTEREST");
        assertThat(preview.paymentsReceived()).isPositive();

        var generated = service.generateForCustomer("customer-statement-001", 101L, request);
        var replay = service.generateForCustomer("customer-statement-001", 101L, request);
        assertThat(replay.billId()).isEqualTo(generated.billId());
        assertThat(service.previewForCustomer(101L, request).duplicate()).isTrue();
        assertThat(service.searchForCustomer(101L, generated.accountId(), null, null, 0, 20).content())
                .extracting(BillGenerationApplication.BillResponse::billId).contains(generated.billId());

        byte[] pdf = pdfRenderer.render(generated);
        assertThat(new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1))
                .startsWith("%PDF-")
                .doesNotContain("/Encrypt");
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isPositive();
            assertThat(document.getDocumentInformation().getTitle()).isEqualTo("MoneyBags Credit Card Statement");
        }
    }

    @Test
    void excludesStatementsThatWereNotSavedToHistory() {
        var request = new BillGenerationApplication.CustomerStatementRequest(
                "55555555-5555-5555-5555-555555555555",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), false);

        var generated = service.generateForCustomer("customer-statement-002", 101L, request);

        assertThat(generated.savedToHistory()).isFalse();
        assertThat(service.getForCustomer(generated.billId(), 101L).billId()).isEqualTo(generated.billId());
        assertThat(service.searchForCustomer(101L, generated.accountId(), null, null, 0, 20).content())
                .extracting(BillGenerationApplication.BillResponse::billId).doesNotContain(generated.billId());
    }

    @Test
    void adminGenerationValidatesCardOwnershipAndAlwaysRetainsTheStatement() {
        var request = new BillGenerationApplication.AdminStatementRequest(
                101L, "66666666-6666-6666-6666-666666666666",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        var preview = service.previewForAdmin(request);
        var generated = service.generateForAdmin("admin-statement-001", request);
        var replay = service.generateForAdmin("admin-statement-001", request);

        assertThat(preview.accountId()).startsWith("CC-");
        assertThat(generated.savedToHistory()).isTrue();
        assertThat(replay.billId()).isEqualTo(generated.billId());
        assertThat(service.search(null, generated.billingPeriod(), null, 0, 20).content())
                .extracting(BillGenerationApplication.BillResponse::billId).contains(generated.billId());
        assertThat(service.searchForCustomer(101L, generated.accountId(), null, null, 0, 20).content())
                .extracting(BillGenerationApplication.BillResponse::billId).contains(generated.billId());
        assertThat(service.searchForCustomer(999L, generated.accountId(), null, null, 0, 20).content())
                .extracting(BillGenerationApplication.BillResponse::billId).doesNotContain(generated.billId());
    }

    @Test
    @Transactional
    void eodClosePersistsOverdueStateAndReplaysWithoutDuplicateHistory() {
        LocalDate businessDate = LocalDate.of(2043, 2, 18);
        String billId = UUID.randomUUID().toString();
        em.persist(new BillGenerationApplication.Bill(billId, "CC-EOD-TEST", 101L,
                "CARD-GOLD", "2043-02", businessDate, "INR", BigDecimal.ZERO,
                new BigDecimal("250.0000"), new BigDecimal("25.0000"),
                businessDate.minusDays(1)));
        em.flush();

        var request = new BillGenerationApplication.CloseRequest(
                UUID.randomUUID().toString(), businessDate, "EOD:BILLS_CLOSE");
        var first = service.close("billing-eod-close-key", request);
        var replay = service.close("billing-eod-close-key", request);

        em.flush();
        em.clear();
        var stored = em.find(BillGenerationApplication.Bill.class, billId);
        Long transitions = em.createQuery(
                        "select count(h) from BillHistory h where h.billId=:billId "
                                + "and h.toStatus='OVERDUE'", Long.class)
                .setParameter("billId", billId)
                .getSingleResult();
        assertThat(stored.status).isEqualTo("OVERDUE");
        assertThat(transitions).isEqualTo(1);
        assertThat(replay).isEqualTo(first);

        var changed = new BillGenerationApplication.CloseRequest(
                request.eodRunId(), businessDate.plusDays(1), request.commandReference());
        assertThatThrownBy(() -> service.close("billing-eod-close-key", changed))
                .isInstanceOfSatisfying(BillGenerationApplication.ApiException.class,
                        error -> assertThat(error.code).isEqualTo("IDEMPOTENCY_CONFLICT"));
    }
}
