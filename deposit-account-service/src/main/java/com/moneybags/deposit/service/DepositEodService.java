package com.moneybags.deposit.service;

import com.moneybags.deposit.domain.DomainTypes.AccountStatus;
import com.moneybags.deposit.dto.EodRequests.DepositAccrualRequest;
import com.moneybags.deposit.dto.EodResponses.DepositAccrualResponse;
import com.moneybags.deposit.dto.EodResponses.ServiceReadinessResponse;
import com.moneybags.deposit.repository.DepositAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class DepositEodService {
    private static final String ACCRUAL_SCOPE = "DEPOSIT_EOD_ACCRUAL";
    private final DepositAccountRepository accountRepository;
    private final IdempotentMutationExecutor idempotency;

    public DepositEodService(DepositAccountRepository accountRepository, IdempotentMutationExecutor idempotency) {
        this.accountRepository = accountRepository;
        this.idempotency = idempotency;
    }

    @Transactional
    public DepositAccrualResponse runAccruals(DepositAccrualRequest request) {
        return idempotency.execute(ACCRUAL_SCOPE, request.commandReference(), request,
                DepositAccrualResponse.class, () -> {
                    int processed = (int) accountRepository.countByCurrencyCodeAndStatus(request.currency(), AccountStatus.ACTIVE);
                    // Interest posting is intentionally not implemented in release 1; this command records a safe,
                    // replayable daily control result until a product-rate and accounting-posting integration is added.
                    return new DepositAccrualResponse(request.eodRunId(), request.commandReference(), request.businessDate(),
                            processed, 0, BigDecimal.ZERO.setScale(4), List.of());
                });
    }

    @Transactional(readOnly = true)
    public ServiceReadinessResponse readiness() {
        long pendingReservations = accountRepository.countAccountsWithActiveReservations();
        return new ServiceReadinessResponse("deposit-account-service", LocalDate.now(), pendingReservations == 0,
                pendingReservations == 0 ? List.of() : List.of("ACTIVE_PAYMENT_RESERVATIONS=" + pendingReservations));
    }
}
