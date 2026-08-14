package com.moneybags.deposit.closure.service;

import com.moneybags.deposit.closure.entity.AccountClosureCheck;
import com.moneybags.deposit.closure.entity.AccountClosureRequest;
import com.moneybags.deposit.closure.entity.AccountClosureSettlement;
import com.moneybags.deposit.closure.repository.AccountClosureCheckRepository;
import com.moneybags.deposit.closure.repository.AccountClosureRequestRepository;
import com.moneybags.deposit.closure.repository.AccountClosureSettlementRepository;
import com.moneybags.deposit.domain.DomainTypes.ClosureRequestStatus;
import com.moneybags.deposit.domain.DomainTypes.ClosureType;
import com.moneybags.deposit.fixeddeposit.entity.FixedDeposit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class MaturityClosureRecorder {
    private static final String POLICY = "FD-MATURITY-V1";

    private final AccountClosureRequestRepository requests;
    private final AccountClosureCheckRepository checks;
    private final AccountClosureSettlementRepository settlements;

    public MaturityClosureRecorder(AccountClosureRequestRepository requests,
                                   AccountClosureCheckRepository checks,
                                   AccountClosureSettlementRepository settlements) {
        this.requests = requests;
        this.checks = checks;
        this.settlements = settlements;
    }

    public void recordCompleted(FixedDeposit fd, BigDecimal interest, BigDecimal netAmount,
                                String destinationAccountId, String reference, LocalDate businessDate) {
        String accountId = fd.getAccount().getId();
        if (requests.existsByAccountIdAndClosureType(accountId, ClosureType.FD_MATURITY)) return;

        AccountClosureRequest request = requests.save(new AccountClosureRequest(
                UUID.randomUUID().toString(), accountId, ClosureType.FD_MATURITY, "eod", "EOD",
                businessDate, "FD_MATURITY_PAID", null, destinationAccountId, POLICY, reference));
        request.transition(ClosureRequestStatus.VALIDATING);
        checks.save(new AccountClosureCheck(UUID.randomUUID().toString(), request.getId(),
                "FD_MATURITY_ELIGIBILITY", true, "Maturity date reached and accrual is complete"));
        request.transition(ClosureRequestStatus.PAYOUT_PENDING);

        AccountClosureSettlement settlement = settlements.save(new AccountClosureSettlement(
                UUID.randomUUID().toString(), request.getId(), fd.getPrincipal(), interest, interest,
                zero(), zero(), zero(), netAmount, fd.getCurrencyCode(), destinationAccountId, reference));
        settlement.complete();
        request.transition(ClosureRequestStatus.CLOSED);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(4);
    }
}
