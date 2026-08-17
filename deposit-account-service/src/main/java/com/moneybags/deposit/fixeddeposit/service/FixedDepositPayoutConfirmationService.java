package com.moneybags.deposit.fixeddeposit.service;

import com.moneybags.deposit.domain.DomainTypes.*;
import com.moneybags.deposit.dto.PaymentOperationRequests.FixedDepositPayoutConfirmationRequest;
import com.moneybags.deposit.dto.PaymentOperationResponses.FixedDepositPayoutConfirmationView;
import com.moneybags.deposit.entity.*;
import com.moneybags.deposit.exception.ApiException;
import com.moneybags.deposit.fixeddeposit.entity.*;
import com.moneybags.deposit.fixeddeposit.repository.*;
import com.moneybags.deposit.repository.*;
import com.moneybags.deposit.service.Hashing;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FixedDepositPayoutConfirmationService {
    private final FixedDepositRepository fixedDeposits;
    private final FixedDepositPayoutRepository payouts;
    private final AccountBalanceRepository balances;
    private final FundReservationRepository reservations;
    private final DepositAccountTransactionRepository transactions;
    private final AccountStatusHistoryRepository histories;
    private final AuditLogRepository audits;

    public FixedDepositPayoutConfirmationService(FixedDepositRepository fixedDeposits,
            FixedDepositPayoutRepository payouts, AccountBalanceRepository balances,
            FundReservationRepository reservations, DepositAccountTransactionRepository transactions,
            AccountStatusHistoryRepository histories, AuditLogRepository audits) {
        this.fixedDeposits=fixedDeposits;this.payouts=payouts;this.balances=balances;
        this.reservations=reservations;this.transactions=transactions;this.histories=histories;this.audits=audits;
    }

    @Transactional
    public FixedDepositPayoutConfirmationView confirm(String fixedDepositId,
            FixedDepositPayoutConfirmationRequest request,String actor,String correlationId) {
        Optional<FixedDepositPayout> replay=payouts.findBySourceReference(request.paymentId());
        if(replay.isPresent())return view(replay.get(),request.currencyCode());
        FixedDeposit fd=fixedDeposits.findByIdForUpdate(fixedDepositId).orElseThrow(()->
                new ApiException(HttpStatus.NOT_FOUND,"FIXED_DEPOSIT_NOT_FOUND","Fixed deposit not found"));
        validate(fd,request);
        DepositAccount sourceAccount=fd.getAccount();
        AccountBalance source=balances.findByAccountIdForUpdate(sourceAccount.getId()).orElseThrow();
        AccountBalance destination=balances.findByAccountIdForUpdate(request.payoutAccountId()).orElseThrow(()->
                new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"PAYOUT_ACCOUNT_NOT_FOUND","Payout account not found"));
        if(destination.getAccount().getStatus()!=AccountStatus.ACTIVE||
                destination.getAccount().getProductSubtype()==ProductSubtype.FIXED_DEPOSIT||
                !request.currencyCode().equals(destination.getCurrencyCode()))
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"PAYOUT_ACCOUNT_INELIGIBLE","Payout account is not eligible");
        if(source.getLedgerBalance().compareTo(request.principalAmount())<0)
            throw new ApiException(HttpStatus.CONFLICT,"FIXED_DEPOSIT_BALANCE_MISMATCH","Fixed deposit principal is not available for payout");

        PaymentOperationType operation="MATURITY".equals(request.payoutType())
                ?PaymentOperationType.FIXED_DEPOSIT_MATURITY_PAYOUT:PaymentOperationType.FIXED_DEPOSIT_PREMATURE_PAYOUT;
        String reservationId=UUID.randomUUID().toString();
        FundReservation reservation=new FundReservation(reservationId,request.paymentId(),operation,
                sourceAccount.getId(),request.payoutAccountId(),null,"PAYMENTS",request.netPayoutAmount(),
                request.currencyCode(),OffsetDateTime.now().plusMinutes(5));
        reservation.transitionTo(ReservationStatus.SETTLED);reservations.save(reservation);
        BigDecimal sourceBefore=source.getLedgerBalance(),destinationBefore=destination.getLedgerBalance();
        source.debitLedgerOnly(request.principalAmount(),request.paymentId()+":FD_PAYOUT");
        destination.credit(request.netPayoutAmount(),request.paymentId()+":FD_DESTINATION");
        transactions.save(new DepositAccountTransaction(UUID.randomUUID().toString(),source.getAccountId(),request.paymentId(),reservationId,
                DepositTransactionType.DEBIT,operation,request.principalAmount(),request.currencyCode(),sourceBefore,source.getLedgerBalance(),correlationId));
        transactions.save(new DepositAccountTransaction(UUID.randomUUID().toString(),destination.getAccountId(),request.paymentId(),reservationId,
                DepositTransactionType.CREDIT,operation,request.netPayoutAmount(),request.currencyCode(),destinationBefore,destination.getLedgerBalance(),correlationId));
        FixedDepositPayout payout=new FixedDepositPayout(UUID.randomUUID().toString(),fixedDepositId,request.payoutType(),
                request.principalAmount(),request.interestAmount(),request.payoutAccountId(),request.paymentId());
        payout.setStatus(FixedDepositPayoutStatus.COMPLETED);payout.setCompletedAt(OffsetDateTime.now());payouts.save(payout);
        AccountStatus from=sourceAccount.getStatus();sourceAccount.setStatus(AccountStatus.CLOSED);sourceAccount.setClosedAt(OffsetDateTime.now());
        sourceAccount.setUpdatedAt(OffsetDateTime.now());sourceAccount.setUpdatedBy(actor);
        fd.setPaidInterest(request.interestAmount());fd.setStatus("MATURITY".equals(request.payoutType())
                ?FixedDepositStatus.PAID_OUT:FixedDepositStatus.CLOSED_PREMATURE);fd.setUpdatedAt(OffsetDateTime.now());
        histories.save(new AccountStatusHistory(UUID.randomUUID().toString(),sourceAccount.getId(),from,AccountStatus.CLOSED,
                "FD_"+request.payoutType()+"_PAID",request.journalNumber(),actor,"SERVICE",correlationId));
        audits.save(new AuditLog(UUID.randomUUID().toString(),fixedDepositId,"CONFIRM_FD_PAYOUT","SUCCESS",actor,"SERVICE",
                request.payoutType(),request.journalNumber(),Hashing.sha256(request.paymentId()),correlationId));
        return view(payout,request.currencyCode());
    }

    private void validate(FixedDeposit fd,FixedDepositPayoutConfirmationRequest request) {
        if(fd.getPrincipal().compareTo(request.principalAmount())!=0||!fd.getCurrencyCode().equals(request.currencyCode())||
                request.principalAmount().add(request.interestAmount()).compareTo(request.netPayoutAmount())!=0)
            throw new ApiException(HttpStatus.CONFLICT,"FIXED_DEPOSIT_PAYOUT_MISMATCH","Payout amounts or currency do not match the fixed deposit");
        if("MATURITY".equals(request.payoutType())) {
            if(fd.getStatus()!=FixedDepositStatus.ACTIVE&&fd.getStatus()!=FixedDepositStatus.PAYOUT_PENDING)
                throw new ApiException(HttpStatus.CONFLICT,"FIXED_DEPOSIT_NOT_READY_FOR_PAYOUT","Fixed deposit is not ready for maturity payout");
            if(LocalDate.now().isBefore(fd.getMaturityDate()))
                throw new ApiException(HttpStatus.CONFLICT,"FIXED_DEPOSIT_NOT_MATURED","Fixed deposit has not reached maturity");
        } else if(fd.getStatus()!=FixedDepositStatus.PAYOUT_PENDING&&fd.getStatus()!=FixedDepositStatus.PREMATURE_CLOSURE_REQUESTED) {
            throw new ApiException(HttpStatus.CONFLICT,"FIXED_DEPOSIT_NOT_READY_FOR_PAYOUT","Premature closure has not reached payout state");
        }
    }

    private FixedDepositPayoutConfirmationView view(FixedDepositPayout payout,String currency) {
        return new FixedDepositPayoutConfirmationView(payout.getFixedDepositId(),payout.getSourceReference(),
                payout.getStatus().name(),payout.getDestinationAccountId(),payout.getNetAmount(),currency,
                payout.getCompletedAt()==null?null:payout.getCompletedAt().toInstant());
    }
}
