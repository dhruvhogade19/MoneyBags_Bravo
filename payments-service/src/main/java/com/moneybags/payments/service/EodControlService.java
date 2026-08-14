package com.moneybags.payments.service;

import com.moneybags.payments.domain.PaymentStatus;
import com.moneybags.payments.dto.PaymentDtos.EodControlResponse;
import com.moneybags.payments.exception.PaymentCutoffException;
import com.moneybags.payments.repository.PaymentRepository;
import java.time.LocalDate;
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
    return new EodControlResponse(cutoffDate, pending == 0 ? "DRAINED" : "DRAINING",
        false, pending);
  }

  public EodControlResponse reopen() {
    cutoff.set(false);
    return response("OPEN");
  }

  private EodControlResponse response(String status) {
    return new EodControlResponse(cutoffDate, status, !cutoff.get(),
        payments.countByStatusIn(PENDING));
  }
}
