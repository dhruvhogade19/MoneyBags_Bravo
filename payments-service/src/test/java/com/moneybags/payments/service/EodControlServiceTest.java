package com.moneybags.payments.service;

import com.moneybags.payments.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EodControlServiceTest {
    @Test
    void drainReportsRealPostedJournalCountAndDebitTotalForReconciliation() {
        LocalDate date = LocalDate.of(2026, 8, 13);
        PaymentRepository payments = mock(PaymentRepository.class);
        when(payments.countByStatusIn(anyList())).thenReturn(0L);
        when(payments.countByBusinessDateAndAccountingJournalNumberIsNotNull(date)).thenReturn(3L);
        when(payments.countByBusinessDateAndReversalJournalNumberIsNotNull(date)).thenReturn(1L);
        when(payments.totalPostedAmount(date)).thenReturn(new BigDecimal("750.0000"));
        when(payments.totalReversalAmount(date)).thenReturn(new BigDecimal("50.0000"));
        EodControlService service = new EodControlService(payments);

        service.cutoff(date);
        var response = service.drain();

        assertThat(response.status()).isEqualTo("DRAINED");
        assertThat(response.postedJournalCount()).isEqualTo(4);
        assertThat(response.postedDebitTotal()).isEqualByComparingTo("800.0000");
    }
}
