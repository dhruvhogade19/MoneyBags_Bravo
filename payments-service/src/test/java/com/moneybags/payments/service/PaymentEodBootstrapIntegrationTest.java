package com.moneybags.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneybags.payments.dto.PaymentDtos.BookTransferRequest;
import com.moneybags.payments.dto.PaymentDtos.EodControlResponse;
import com.moneybags.payments.exception.PaymentCutoffException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties =
    "spring.datasource.url=jdbc:h2:mem:payments-bootstrap-test;MODE=Oracle")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentEodBootstrapIntegrationTest {
  @Autowired EodControlService eod;
  @Autowired PaymentOrchestrationService orchestration;

  @Test
  void bootstrapIsFailClosedUntilAnEodOwnerEstablishesTheAuthoritativeDate() {
    EodControlResponse bootstrap = eod.drain();
    assertThat(bootstrap.newPaymentIntake()).isFalse();
    assertThat(bootstrap.commandReference()).isEqualTo(EodControlService.BOOTSTRAP_REFERENCE);
    assertThat(bootstrap.businessDate()).isNotNull();

    assertThatThrownBy(() -> orchestration.bookTransfer(new BookTransferRequest(
        99_001L, "bootstrap-source", "bootstrap-target", new BigDecimal("10.00"),
        "INR", "Must remain closed"), "bootstrap-payment-" + UUID.randomUUID(),
        "bootstrap-trace"))
        .isInstanceOf(PaymentCutoffException.class)
        .hasMessageContaining("closed for EOD");
    assertThatThrownBy(eod::reopen)
        .isInstanceOf(PaymentCutoffException.class)
        .hasMessageContaining("owned by an active EOD run");

    LocalDate configuredPastDate = LocalDate.of(2001, 2, 3);
    String owner = "EOD:bootstrap-run:PAYMENTS_BARRIER:EPOCH:1";
    EodControlResponse cutoff = eod.cutoff(configuredPastDate, "INR", owner);
    assertThat(cutoff.newPaymentIntake()).isFalse();
    assertThat(cutoff.businessDate()).isEqualTo(configuredPastDate);
    assertThat(cutoff.commandReference()).isEqualTo(owner);
    assertThat(eod.drain(configuredPastDate, "INR", owner).status()).isEqualTo("DRAINED");

    assertThatThrownBy(() -> eod.reopen(configuredPastDate,
        configuredPastDate.plusDays(2), "INR", owner))
        .isInstanceOf(PaymentCutoffException.class)
        .hasMessageContaining("immediately following");
    assertThatThrownBy(() -> eod.reopen(configuredPastDate,
        configuredPastDate, "INR", "EOD:wrong-owner"))
        .isInstanceOf(PaymentCutoffException.class);
    assertThat(eod.drain().newPaymentIntake()).isFalse();

    EodControlResponse cleanup = eod.reopen(configuredPastDate, configuredPastDate,
        "INR", owner);
    assertThat(cleanup.newPaymentIntake()).isTrue();
    assertThat(cleanup.businessDate()).isEqualTo(configuredPastDate);

    eod.cutoff(configuredPastDate, "INR", owner);
    EodControlResponse rollover = eod.reopen(configuredPastDate,
        configuredPastDate.plusDays(1), "INR", owner);
    assertThat(rollover.newPaymentIntake()).isTrue();
    assertThat(rollover.businessDate()).isEqualTo(configuredPastDate.plusDays(1));
    assertThatThrownBy(() -> eod.cutoff(configuredPastDate, "INR", owner))
        .isInstanceOf(PaymentCutoffException.class)
        .hasMessageContaining("cannot replace it");
  }
}
