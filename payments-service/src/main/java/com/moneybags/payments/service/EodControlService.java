package com.moneybags.payments.service;

import com.moneybags.payments.domain.PaymentStatus;
import com.moneybags.payments.dto.PaymentDtos.EodControlResponse;
import com.moneybags.payments.exception.PaymentCutoffException;
import com.moneybags.payments.repository.PaymentRepository;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

@Service
public class EodControlService {
  private static final List<PaymentStatus> PENDING = List.of(
      PaymentStatus.PENDING_VALIDATION, PaymentStatus.PENDING_RESERVATION,
      PaymentStatus.PENDING_ACCOUNTING, PaymentStatus.PENDING_SETTLEMENT,
      PaymentStatus.PENDING_BILLING, PaymentStatus.REVERSAL_PENDING);

  private final PaymentRepository payments;
  private final AtomicBoolean cutoff = new AtomicBoolean(false);
  private volatile LocalDate cutoffDate;

  public EodControlService(PaymentRepository payments) {
    this.payments = payments;
  }

  public void assertOpen() {
    if (cutoff.get()) {
      throw new PaymentCutoffException("New payment intake is closed for EOD");
    }
  }

  public EodControlResponse cutoff(LocalDate businessDate) {
    cutoffDate = businessDate;
    cutoff.set(true);
    return response("CUT_OFF");
  }

  public EodControlResponse drain() {
    long pending = payments.countByStatusIn(PENDING);
    return response(pending == 0 ? "DRAINED" : "DRAINING");
  }

  public EodControlResponse reopen() {
    cutoff.set(false);
    return response("OPEN");
  }

  private EodControlResponse response(String status) {
    long postedCount = cutoffDate == null ? 0 :
        payments.countByBusinessDateAndAccountingJournalNumberIsNotNull(cutoffDate)
            + payments.countByBusinessDateAndReversalJournalNumberIsNotNull(cutoffDate);
    BigDecimal postedTotal = cutoffDate == null ? BigDecimal.ZERO :
        value(payments.totalPostedAmount(cutoffDate)).add(value(payments.totalReversalAmount(cutoffDate)));
    return new EodControlResponse(cutoffDate, status, !cutoff.get(),
        payments.countByStatusIn(PENDING), postedCount, postedTotal.setScale(4));
  }

  private BigDecimal value(BigDecimal amount) { return amount == null ? BigDecimal.ZERO : amount; }
}
