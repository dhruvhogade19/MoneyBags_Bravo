package com.moneybags.deposit.fixeddeposit.service;

import com.moneybags.deposit.closure.service.MaturityClosureRecorder;
import com.moneybags.deposit.domain.DomainTypes.*;
import com.moneybags.deposit.entity.*;
import com.moneybags.deposit.exception.ApiException;
import com.moneybags.deposit.fixeddeposit.calculation.FixedDepositInterestCalculator;
import com.moneybags.deposit.fixeddeposit.dto.FixedDepositRequests.EodRequest;
import com.moneybags.deposit.fixeddeposit.dto.FixedDepositResponses.*;
import com.moneybags.deposit.fixeddeposit.entity.*;
import com.moneybags.deposit.fixeddeposit.repository.*;
import com.moneybags.deposit.repository.*;
import com.moneybags.deposit.service.Hashing;
import com.moneybags.deposit.service.NotificationOutboxService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
public class FixedDepositEodService {
    private final FixedDepositRepository fds; private final FixedDepositInterestAccrualRepository accruals;
    private final FixedDepositPayoutRepository payouts; private final AccountBalanceRepository balances;
    private final DepositAccountTransactionRepository transactions; private final AccountStatusHistoryRepository histories;
    private final FundReservationRepository reservations;
    private final AuditLogRepository audits; private final FixedDepositInterestCalculator calculator;
    private final MaturityClosureRecorder maturityClosures;
    private final NotificationOutboxService notificationOutbox;
    public FixedDepositEodService(FixedDepositRepository fds,FixedDepositInterestAccrualRepository accruals,
        FixedDepositPayoutRepository payouts,AccountBalanceRepository balances,DepositAccountTransactionRepository transactions,
        FundReservationRepository reservations,
        AccountStatusHistoryRepository histories,AuditLogRepository audits,FixedDepositInterestCalculator calculator,
        MaturityClosureRecorder maturityClosures, NotificationOutboxService notificationOutbox){
        this.fds=fds;this.accruals=accruals;this.payouts=payouts;this.balances=balances;this.transactions=transactions;this.reservations=reservations;
        this.histories=histories;this.audits=audits;this.calculator=calculator;this.maturityClosures=maturityClosures;
        this.notificationOutbox=notificationOutbox;
    }
    @Transactional public EodResult accrue(EodRequest r){
        int processed=0,skipped=0; BigDecimal total=BigDecimal.ZERO.setScale(4); List<String> failures=new ArrayList<>();
        for(FixedDeposit fd:fds.findAccrualCandidates(FixedDepositStatus.ACTIVE,r.businessDate())){
            LocalDate from=fd.getLastAccrualDate()==null?fd.getValueDate():fd.getLastAccrualDate().plusDays(1);
            LocalDate lastInterestDate=fd.getMaturityDate().minusDays(1);
            LocalDate through=r.businessDate().isAfter(lastInterestDate)?lastInterestDate:r.businessDate();
            for(LocalDate date=from;!date.isAfter(through);date=date.plusDays(1)){
                if(accruals.existsByFixedDepositIdAndBusinessDate(fd.getId(),date)){skipped++;continue;}
                BigDecimal amount=calculator.dailyAccrual(fd.getPrincipal(),fd.getBookedAnnualRate());
                BigDecimal cumulative=fd.getAccruedInterest().add(amount).setScale(4);
                accruals.save(new FixedDepositInterestAccrual(UUID.randomUUID().toString(),fd.getId(),date,fd.getPrincipal(),
                        fd.getBookedAnnualRate(),amount,cumulative,r.commandReference()+"-"+fd.getId()+"-"+date));
                fd.setAccruedInterest(cumulative);fd.setLastAccrualDate(date);fd.setUpdatedAt(OffsetDateTime.now());
                processed++;total=total.add(amount);
            }
        }
        return new EodResult(r.eodRunId(),r.businessDate(),r.commandReference(),processed,skipped,total,failures);
    }
    @Transactional public EodResult mature(EodRequest r){
        int processed=0,skipped=0; BigDecimal total=BigDecimal.ZERO.setScale(4); List<String> failures=new ArrayList<>();
        for(FixedDeposit fd:fds.findByStatusAndMaturityDateLessThanEqual(FixedDepositStatus.ACTIVE,r.businessDate())){
            String reference=r.commandReference()+"-"+fd.getId();
            if(payouts.existsBySourceReference(reference)){skipped++;continue;}
            if(fd.getLastAccrualDate()==null||fd.getLastAccrualDate().isBefore(fd.getMaturityDate().minusDays(1))){
                failures.add(fd.getId()+":ACCRUAL_INCOMPLETE");continue;
            }
            fd.setStatus(FixedDepositStatus.PAYOUT_PENDING);
            BigDecimal interest=fd.getExpectedInterest(); BigDecimal net=fd.getPrincipal().add(interest);
            AccountBalance destination=balances.findByAccountIdForUpdate(fd.getPayoutAccountId()).orElseThrow(()->
                    new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"PAYOUT_ACCOUNT_NOT_FOUND","Payout account not found"));
            AccountBalance source=balances.findByAccountIdForUpdate(fd.getAccount().getId()).orElseThrow();
            BigDecimal sourceBefore=source.getLedgerBalance(),destinationBefore=destination.getLedgerBalance();
            String reservationId=UUID.randomUUID().toString();
            FundReservation reservation=new FundReservation(reservationId,reference,PaymentOperationType.FIXED_DEPOSIT_MATURITY_PAYOUT,
                    fd.getAccount().getId(),fd.getPayoutAccountId(),null,"EOD",net,fd.getCurrencyCode(),OffsetDateTime.now().plusMinutes(5));
            reservation.transitionTo(ReservationStatus.SETTLED);reservations.save(reservation);
            source.debitLedgerOnly(fd.getPrincipal(),reference+"-FD"); destination.credit(net,reference+"-DESTINATION");
            FixedDepositPayout payout=new FixedDepositPayout(UUID.randomUUID().toString(),fd.getId(),fd.getPrincipal(),interest,fd.getPayoutAccountId(),reference);
            payout.setStatus(FixedDepositPayoutStatus.COMPLETED);payout.setCompletedAt(OffsetDateTime.now());payouts.save(payout);
            transactions.save(new DepositAccountTransaction(UUID.randomUUID().toString(),fd.getAccount().getId(),reference,reservationId,
                    DepositTransactionType.DEBIT,PaymentOperationType.FIXED_DEPOSIT_MATURITY_PAYOUT,fd.getPrincipal(),fd.getCurrencyCode(),sourceBefore,source.getLedgerBalance(),reference));
            transactions.save(new DepositAccountTransaction(UUID.randomUUID().toString(),fd.getPayoutAccountId(),reference,reservationId,
                    DepositTransactionType.CREDIT,PaymentOperationType.FIXED_DEPOSIT_MATURITY_PAYOUT,net,fd.getCurrencyCode(),destinationBefore,destination.getLedgerBalance(),reference));
            fd.setPaidInterest(interest);fd.setStatus(FixedDepositStatus.PAID_OUT);fd.setUpdatedAt(OffsetDateTime.now());
            DepositAccount account=fd.getAccount();AccountStatus from=account.getStatus();account.setStatus(AccountStatus.CLOSED);account.setClosedAt(OffsetDateTime.now());
            histories.save(new AccountStatusHistory(UUID.randomUUID().toString(),account.getId(),from,AccountStatus.CLOSED,"FD_MATURITY_PAID",null,"eod","SERVICE",reference));
            audits.save(new AuditLog(UUID.randomUUID().toString(),fd.getId(),"MATURE_FIXED_DEPOSIT","SUCCESS","eod","SERVICE","FD_MATURITY_PAID",null,Hashing.sha256(reference),reference));
            maturityClosures.recordCompleted(fd,interest,net,fd.getPayoutAccountId(),reference,r.businessDate());
            String cifId=account.getHolders().stream().filter(holder -> holder.getRole()==HolderRole.PRIMARY)
                    .findFirst().orElseThrow().getCustomerId();
            notificationOutbox.enqueue(cifId,"FD_MATURITY",account.getId(),"fd-"+account.getId()+"-maturity",
                    Map.of("accountId",account.getId(),"maturityDate",fd.getMaturityDate().toString(),
                            "currency",fd.getCurrencyCode(),"maturityAmount",net.setScale(2, RoundingMode.HALF_EVEN).toPlainString()));
            processed++;total=total.add(net);
        }
        return new EodResult(r.eodRunId(),r.businessDate(),r.commandReference(),processed,skipped,total,failures);
    }
    @Transactional(readOnly=true) public ReadinessResponse readiness(){
        long funding=fds.countByStatus(FixedDepositStatus.PENDING_FUNDING), payout=fds.countByStatus(FixedDepositStatus.PAYOUT_PENDING);
        List<String> blockers=new ArrayList<>();if(funding>0)blockers.add("PENDING_FUNDING="+funding);if(payout>0)blockers.add("PENDING_PAYOUTS="+payout);
        return new ReadinessResponse(blockers.isEmpty(),funding,payout,blockers);
    }
}
