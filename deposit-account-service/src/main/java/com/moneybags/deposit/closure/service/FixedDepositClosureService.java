package com.moneybags.deposit.closure.service;

import com.moneybags.deposit.closure.calculation.PrematureClosureCalculator;
import com.moneybags.deposit.closure.dto.AccountClosureRequests.*;
import com.moneybags.deposit.closure.dto.AccountClosureResponses.*;
import com.moneybags.deposit.closure.entity.*;
import com.moneybags.deposit.closure.repository.*;
import com.moneybags.deposit.domain.DomainTypes.*;
import com.moneybags.deposit.entity.*;
import com.moneybags.deposit.exception.ApiException;
import com.moneybags.deposit.fixeddeposit.entity.*;
import com.moneybags.deposit.fixeddeposit.repository.*;
import com.moneybags.deposit.repository.*;
import com.moneybags.deposit.service.Hashing;
import com.moneybags.deposit.integration.AccountingLifecycleGateway;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class FixedDepositClosureService {
    private static final BigDecimal PENALTY_RATE=new BigDecimal("1.00000000");
    private static final String POLICY="FD-PC-LOCAL-V1";
    private static final Set<ClosureRequestStatus> TERMINAL=EnumSet.of(ClosureRequestStatus.REJECTED,ClosureRequestStatus.CLOSED,ClosureRequestStatus.CANCELLED);
    private final FixedDepositRepository fds;private final DepositAccountRepository accounts;private final AccountBalanceRepository balances;
    private final FundReservationRepository reservations;private final DepositAccountTransactionRepository transactions;
    private final FixedDepositPayoutRepository payouts;private final AccountClosureRequestRepository requests;
    private final AccountClosureCheckRepository checks;private final AccountClosureSettlementRepository settlements;
    private final FixedDepositPrematureClosureCalculationRepository calculations;private final AccountStatusHistoryRepository histories;
    private final AuditLogRepository audits;private final PrematureClosureCalculator calculator;private final CasaClosureService closureReader;private final AccountingLifecycleGateway accountingLifecycle;
    public FixedDepositClosureService(FixedDepositRepository fds,DepositAccountRepository accounts,AccountBalanceRepository balances,
        FundReservationRepository reservations,DepositAccountTransactionRepository transactions,FixedDepositPayoutRepository payouts,
        AccountClosureRequestRepository requests,AccountClosureCheckRepository checks,AccountClosureSettlementRepository settlements,
        FixedDepositPrematureClosureCalculationRepository calculations,AccountStatusHistoryRepository histories,
        AuditLogRepository audits,PrematureClosureCalculator calculator,CasaClosureService closureReader,AccountingLifecycleGateway accountingLifecycle){this.fds=fds;this.accounts=accounts;
        this.balances=balances;this.reservations=reservations;this.transactions=transactions;this.payouts=payouts;
        this.requests=requests;this.checks=checks;this.settlements=settlements;this.calculations=calculations;
        this.histories=histories;this.audits=audits;this.calculator=calculator;this.closureReader=closureReader;this.accountingLifecycle=accountingLifecycle;}

    @Transactional(readOnly=true)
    public PrematureClosureQuoteResponse quote(String fdId,PrematureClosureQuoteRequest input){
        FixedDeposit fd=load(fdId,false);List<String> blockers=validate(fd,input.customerId(),input.destinationAccountId(),input.requestedClosureDate());
        var c=calculate(fd,input.requestedClosureDate());
        return new PrematureClosureQuoteResponse(blockers.isEmpty(),fdId,fd.getPrincipal(),fd.getBookedAnnualRate(),c.holdingDays(),
            fd.getBookedAnnualRate(),PENALTY_RATE,c.finalRate(),fd.getExpectedInterest(),c.recalculatedInterest(),
            c.interestRecovery(),c.netPayout(),fd.getCurrencyCode(),input.destinationAccountId(),blockers,OffsetDateTime.now().plusMinutes(15));
    }

    @Transactional
    public ClosureRequestView close(String fdId,PrematureClosureRequest input,String actor,String correlationId,String reference){
        FixedDeposit fd=load(fdId,true);String accountId=fd.getAccount().getId();
        if(!requests.findActive(accountId,TERMINAL).isEmpty())throw new ApiException(HttpStatus.CONFLICT,"CLOSURE_ALREADY_ACTIVE","An active closure request already exists");
        AccountClosureRequest request=requests.save(new AccountClosureRequest(UUID.randomUUID().toString(),accountId,
            ClosureType.FD_PREMATURE,actor,input.channel(),input.requestedClosureDate(),input.reasonCode(),input.reasonText(),
            input.destinationAccountId(),POLICY,correlationId));request.transition(ClosureRequestStatus.VALIDATING);
        List<String> blockers=validate(fd,input.customerId(),input.destinationAccountId(),input.requestedClosureDate());
        persistChecks(request.getId(),blockers);
        if(!blockers.isEmpty()){request.setRejectionCode("PREMATURE_CLOSURE_CHECK_FAILED");request.setRejectionDetails(String.join("; ",blockers));request.transition(ClosureRequestStatus.REJECTED);return closureReader.get(accountId,request.getId());}
        requireAccountingClearance(fd.getAccount());
        var c=calculate(fd,input.requestedClosureDate());var original=calculator.calculate(fd.getPrincipal(),fd.getBookedAnnualRate(),fd.getValueDate(),input.requestedClosureDate(),fd.getBookedAnnualRate(),BigDecimal.ZERO,fd.getPaidInterest());
        calculations.save(new FixedDepositPrematureClosureCalculation(UUID.randomUUID().toString(),fdId,request.getId(),
            fd.getValueDate(),input.requestedClosureDate(),c.holdingDays(),fd.getMaturityDate(),fd.getBookedAnnualRate(),
            fd.getBookedAnnualRate(),PENALTY_RATE,c.finalRate(),fd.getAccruedInterest(),c.recalculatedInterest(),
            fd.getPaidInterest(),c.interestRecovery(),c.netInterest(),fd.getPrincipal(),c.netPayout(),
            "{\"policyVersion\":\""+POLICY+"\",\"method\":\"BOOKED_RATE_MINUS_PENALTY\"}"));
        BigDecimal penalty=original.recalculatedInterest().subtract(c.recalculatedInterest()).max(BigDecimal.ZERO).setScale(4,RoundingMode.HALF_EVEN);
        AccountClosureSettlement settlement=settlements.save(new AccountClosureSettlement(UUID.randomUUID().toString(),request.getId(),
            fd.getPrincipal(),fd.getExpectedInterest(),c.recalculatedInterest(),penalty,zero(),zero(),c.netPayout(),fd.getCurrencyCode(),
            input.destinationAccountId(),reference));
        DepositAccount account=fd.getAccount();AccountStatus from=account.getStatus();account.setStatus(AccountStatus.CLOSURE_PENDING);
        account.setUpdatedAt(OffsetDateTime.now());account.setUpdatedBy(actor);fd.setStatus(FixedDepositStatus.PREMATURE_CLOSURE_REQUESTED);
        histories.save(new AccountStatusHistory(UUID.randomUUID().toString(),accountId,from,AccountStatus.CLOSURE_PENDING,input.reasonCode(),input.reasonText(),actor,"USER",correlationId));
        request.transition(ClosureRequestStatus.PAYOUT_PENDING);fd.setStatus(FixedDepositStatus.PAYOUT_PENDING);
        transfer(fd,input.destinationAccountId(),input.customerId(),c.netPayout(),reference,correlationId);
        FixedDepositPayout payout=new FixedDepositPayout(UUID.randomUUID().toString(),fdId,"PREMATURE",fd.getPrincipal(),c.netInterest(),input.destinationAccountId(),reference);
        payout.setStatus(FixedDepositPayoutStatus.COMPLETED);payout.setCompletedAt(OffsetDateTime.now());payouts.save(payout);
        settlement.complete();fd.setPaidInterest(c.recalculatedInterest());fd.setStatus(FixedDepositStatus.CLOSED_PREMATURE);fd.setUpdatedAt(OffsetDateTime.now());
        account.setStatus(AccountStatus.CLOSED);account.setClosedAt(OffsetDateTime.now());account.setUpdatedAt(OffsetDateTime.now());
        OffsetDateTime closedAt=account.getClosedAt();accountingLifecycle.publishClosure(new AccountingLifecycleGateway.AccountClosedEvent("DEPOSIT-CLOSE:"+accountId,"DEPOSIT_ACCOUNT_CLOSED","DEPOSIT_ACCOUNT",accountId,account.getCurrencyCode(),closedAt.toLocalDate(),closedAt,input.reasonCode()),"DEPOSIT-CLOSE:"+accountId,correlationId);
        histories.save(new AccountStatusHistory(UUID.randomUUID().toString(),accountId,AccountStatus.CLOSURE_PENDING,AccountStatus.CLOSED,"FD_PREMATURELY_CLOSED",input.reasonText(),actor,"USER",correlationId));
        request.transition(ClosureRequestStatus.CLOSED);audits.save(new AuditLog(UUID.randomUUID().toString(),fdId,"CLOSE_FIXED_DEPOSIT_PREMATURELY","SUCCESS",actor,"USER",input.reasonCode(),null,Hashing.sha256(reference),correlationId));
        return closureReader.get(accountId,request.getId());
    }

    @Transactional(readOnly=true)
    public ClosureRequestView get(String fdId,String requestId){
        FixedDeposit fd=load(fdId,false);
        ClosureRequestView result=closureReader.get(fd.getAccount().getId(),requestId);
        if(result.closureType()!=ClosureType.FD_PREMATURE)throw new ApiException(HttpStatus.NOT_FOUND,
            "CLOSURE_REQUEST_NOT_FOUND","Premature closure request not found");
        return result;
    }

    private List<String> validate(FixedDeposit fd,String customerId,String destinationId,LocalDate date){List<String>b=new ArrayList<>();DepositAccount a=fd.getAccount();
        if(fd.getStatus()!=FixedDepositStatus.ACTIVE)b.add("Fixed deposit must be active");
        if(a.getStatus()!=AccountStatus.ACTIVE)b.add("Fixed deposit account must be active");
        AccountBalance balance=a.getBalance();
        if(balance==null||balance.getLedgerBalance().compareTo(fd.getPrincipal())!=0||balance.getAvailableBalance().signum()!=0||balance.getBlockedAmount().signum()!=0)
            b.add("Fixed deposit balance is not reconciled to principal");
        if(!date.isBefore(fd.getMaturityDate()))b.add("Use maturity processing on or after maturity date");
        if(!date.equals(LocalDate.now()))b.add("Closure date must be today's business date");
        long days=Duration.between(fd.getValueDate().atStartOfDay(),date.atStartOfDay()).toDays();if(days<7)b.add("Minimum holding period is 7 days");
        if(a.getHolders().stream().noneMatch(h->h.getCustomerId().equals(customerId)&&h.getRole()==HolderRole.PRIMARY&&h.getStatus()==RecordStatus.ACTIVE))b.add("Requester must be the active primary holder");
        if(reservations.countBySourceAccountIdAndStatus(a.getId(),ReservationStatus.ACTIVE)>0)b.add("Active fund reservations must be released");
        Optional<DepositAccount>d=accounts.findDetailedById(destinationId);if(d.isEmpty()||d.get().getStatus()!=AccountStatus.ACTIVE||d.get().getProductSubtype()==ProductSubtype.FIXED_DEPOSIT||!d.get().getCurrencyCode().equals(fd.getCurrencyCode())||d.get().getHolders().stream().noneMatch(h->h.getCustomerId().equals(customerId)&&h.getStatus()==RecordStatus.ACTIVE))b.add("Destination must be an active same-currency CASA account owned by the requester");return b;}
    private PrematureClosureCalculator.Calculation calculate(FixedDeposit fd,LocalDate date){return calculator.calculate(fd.getPrincipal(),fd.getBookedAnnualRate(),fd.getValueDate(),date,fd.getBookedAnnualRate(),PENALTY_RATE,fd.getPaidInterest());}
    private void transfer(FixedDeposit fd,String destinationId,String customerId,BigDecimal net,String reference,String correlationId){List<String>ids=new ArrayList<>(List.of(fd.getAccount().getId(),destinationId));ids.sort(String::compareTo);Map<String,AccountBalance>locked=new HashMap<>();for(String id:ids)locked.put(id,balances.findByAccountIdForUpdate(id).orElseThrow());AccountBalance source=locked.get(fd.getAccount().getId()),destination=locked.get(destinationId);String reservationId=UUID.randomUUID().toString();FundReservation r=new FundReservation(reservationId,reference,PaymentOperationType.FIXED_DEPOSIT_PREMATURE_PAYOUT,fd.getAccount().getId(),destinationId,null,customerId,net,fd.getCurrencyCode(),OffsetDateTime.now().plusMinutes(5));r.transitionTo(ReservationStatus.SETTLED);reservations.save(r);BigDecimal sb=source.getLedgerBalance(),db=destination.getLedgerBalance();source.debitLedgerOnly(fd.getPrincipal(),reference+"-FD");destination.credit(net,reference+"-DEST");transactions.save(new DepositAccountTransaction(UUID.randomUUID().toString(),fd.getAccount().getId(),reference,reservationId,DepositTransactionType.DEBIT,PaymentOperationType.FIXED_DEPOSIT_PREMATURE_PAYOUT,fd.getPrincipal(),fd.getCurrencyCode(),sb,source.getLedgerBalance(),correlationId));transactions.save(new DepositAccountTransaction(UUID.randomUUID().toString(),destinationId,reference,reservationId,DepositTransactionType.CREDIT,PaymentOperationType.FIXED_DEPOSIT_PREMATURE_PAYOUT,net,fd.getCurrencyCode(),db,destination.getLedgerBalance(),correlationId));}
    private void persistChecks(String requestId,List<String> blockers){checks.save(new AccountClosureCheck(UUID.randomUUID().toString(),requestId,"FD_PREMATURE_ELIGIBILITY",blockers.isEmpty(),blockers.isEmpty()?"Check passed":String.join("; ",blockers)));}
    private void requireAccountingClearance(DepositAccount account){var clearance=accountingLifecycle.clearance(account.getId(),account.getCurrencyCode());if(!clearance.accountingCleared())throw new ApiException(HttpStatus.CONFLICT,"ACCOUNTING_CLEARANCE_FAILED","Accounting has not cleared the account for closure: "+String.join(", ",clearance.blockers()));}
    private FixedDeposit load(String id,boolean lock){return (lock?fds.findByIdForUpdate(id):fds.findDetailedById(id)).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"FIXED_DEPOSIT_NOT_FOUND","Fixed deposit not found"));}
    private BigDecimal zero(){return BigDecimal.ZERO.setScale(4);}
}
