package com.moneybags.billing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class BillGenerationApplicationTest {
    @Autowired BillGenerationApplication.BillingService service;

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
                .contains("PREVIOUS_BALANCE", "PURCHASE", "PAYMENT", "INTEREST");
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
    void limitsCustomerBillReadsToTheAuthenticatedCif() {
        var bill = service.generate("test-bill-generation-003", new BillGenerationApplication.GenerateRequest(
                "55555555-5555-5555-5555-555555555555", "2026-10", LocalDate.of(2026, 10, 13)));

        var customerPage = service.searchForCustomer(101L, false, null, null, null, 0, 20);

        assertThat(customerPage.content()).extracting(BillGenerationApplication.BillResponse::billId)
                .contains(bill.billId());
        assertThatThrownBy(() -> service.getForCustomer(bill.billId(), 202L, false))
                .isInstanceOf(BillGenerationApplication.ApiException.class)
                .hasMessage("Bill was not found");
        assertThat(service.getForCustomer(bill.billId(), null, true).billId()).isEqualTo(bill.billId());
    }
}
