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
import com.moneybags.deposit.integration.AccountingFixedDepositPostingGateway;
import com.moneybags.deposit.integration.AccountingFixedDepositPostingGateway.FixedDepositPosting;
import com.moneybags.deposit.integration.AccountingFixedDepositPostingGateway.PostingComponent;
import com.moneybags.deposit.integration.AccountingFixedDepositPostingGateway.PostingResponse;
import com.moneybags.deposit.integration.AccountingLifecycleGateway;
import com.moneybags.deposit.repository.*;
import com.moneybags.deposit.service.Hashing;
import com.moneybags.deposit.service.NotificationOutboxService;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
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
    private final NotificationOutboxService notificationOutbox; private final AccountingLifecycleGateway accountingLifecycle;
    private final AccountingFixedDepositPostingGateway accountingPostings;
    private final EntityManager entityManager;
    public FixedDepositEodService(FixedDepositRepository fds,FixedDepositInterestAccrualRepository accruals,
        FixedDepositPayoutRepository payouts,AccountBalanceRepository balances,DepositAccountTransactionRepository transactions,
        FundReservationRepository reservations,
        AccountStatusHistoryRepository histories,AuditLogRepository audits,FixedDepositInterestCalculator calculator,
        MaturityClosureRecorder maturityClosures, NotificationOutboxService notificationOutbox,
        AccountingLifecycleGateway accountingLifecycle, AccountingFixedDepositPostingGateway accountingPostings,
        EntityManager entityManager){
        this.fds=fds;this.accruals=accruals;this.payouts=payouts;this.balances=balances;this.transactions=transactions;this.reservations=reservations;
        this.histories=histories;this.audits=audits;this.calculator=calculator;this.maturityClosures=maturityClosures;
        this.notificationOutbox=notificationOutbox;this.accountingLifecycle=accountingLifecycle;
        this.accountingPostings=accountingPostings;this.entityManager=entityManager;
    }
    @Transactional public EodResult accrue(EodRequest r){
        AccountingRecovery recovery=recoverAccrualAccounting(r);
        int processed=recovery.processed(),skipped=recovery.skipped(); BigDecimal total=recovery.totalAmount();
        long postedJournalCount=recovery.postedJournalCount();
        BigDecimal postedDebitTotal=recovery.postedDebitTotal();
        List<String> failures=new ArrayList<>(recovery.failures());
        for(FixedDeposit fd:fds.findAccrualCandidates(FixedDepositStatus.ACTIVE,r.businessDate())){
            LocalDate from=fd.getLastAccrualDate()==null?fd.getValueDate():fd.getLastAccrualDate().plusDays(1);
            LocalDate lastInterestDate=fd.getMaturityDate().minusDays(1);
            LocalDate through=r.businessDate().isAfter(lastInterestDate)?lastInterestDate:r.businessDate();
            if(!from.isAfter(through))ensureAccountingRegistration(fd.getAccount(),fd,r.eodRunId());
            for(LocalDate date=from;!date.isAfter(through);date=date.plusDays(1)){
                Optional<FixedDepositInterestAccrual> existing=accruals.findByFixedDepositIdAndBusinessDate(fd.getId(),date);
                if(existing.isPresent()){
                    if(!recovery.handledIds().contains(existing.get().getId()))skipped++;
                    continue;
                }
                BigDecimal amount=accrualAmount(fd,date,lastInterestDate);
                BigDecimal cumulative=fd.getAccruedInterest().add(amount).setScale(4);
                String reference=accrualReference(fd.getId(),date);
                FixedDepositInterestAccrual accrual=accruals.save(new FixedDepositInterestAccrual(
                        UUID.randomUUID().toString(),fd.getId(),date,fd.getPrincipal(),fd.getBookedAnnualRate(),
                        amount,cumulative,reference));
                fd.setAccruedInterest(cumulative);fd.setLastAccrualDate(date);fd.setUpdatedAt(OffsetDateTime.now());
                entityManager.flush();
                PostingResponse posting=accountingPostings.post(accrualPosting(fd,date,r,amount),reference,r.eodRunId());
                requirePosted(posting,amount,reference);
                accrual.recordAccountingPosting(posting.journalNumber(),posting.status().toUpperCase(Locale.ROOT));
                entityManager.flush();
                processed++;total=total.add(amount);
                if(isCurrentRunPosting(posting,r.eodRunId())){
                    postedJournalCount++;postedDebitTotal=postedDebitTotal.add(posting.totalDebit()).setScale(4);
                }
            }
        }
        return new EodResult(r.eodRunId(),r.businessDate(),r.commandReference(),processed,skipped,total,failures,
                postedJournalCount,postedDebitTotal);
    }
    @Transactional public EodResult mature(EodRequest r){
        AccountingRecovery recovery=recoverMaturityAccounting(r);
        int processed=recovery.processed(),skipped=recovery.skipped(); BigDecimal total=recovery.totalAmount();
        long postedJournalCount=recovery.postedJournalCount();
        BigDecimal postedDebitTotal=recovery.postedDebitTotal();
        List<String> failures=new ArrayList<>(recovery.failures());
        for(FixedDeposit fd:fds.findByStatusAndMaturityDateLessThanEqual(FixedDepositStatus.ACTIVE,r.businessDate())){
            String reference=maturityReference(fd.getId());
            Optional<FixedDepositPayout> existingPayout=payouts.findBySourceReference(reference).or(()->
                    payouts.findFirstByFixedDepositIdAndPayoutTypeOrderByCreatedAtDesc(fd.getId(),"MATURITY"));
            if(existingPayout.isPresent()){
                if(existingPayout.get().needsAccountingPosting()
                        && !recovery.handledIds().contains(existingPayout.get().getId()))
                    failures.add(fd.getId()+":PAYOUT_LOCAL_STATE_INCOMPLETE");
                else if(!recovery.handledIds().contains(existingPayout.get().getId()))skipped++;
                continue;
            }
            if(fd.getLastAccrualDate()==null||fd.getLastAccrualDate().isBefore(fd.getMaturityDate().minusDays(1))){
                failures.add(fd.getId()+":ACCRUAL_INCOMPLETE");continue;
            }
            DepositAccount account=fd.getAccount();
            ensureAccountingRegistration(account,fd,r.eodRunId());
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
            payout.setStatus(FixedDepositPayoutStatus.COMPLETED);payout.setCompletedAt(OffsetDateTime.now());payout=payouts.save(payout);
            transactions.save(new DepositAccountTransaction(UUID.randomUUID().toString(),fd.getAccount().getId(),reference,reservationId,
                    DepositTransactionType.DEBIT,PaymentOperationType.FIXED_DEPOSIT_MATURITY_PAYOUT,fd.getPrincipal(),fd.getCurrencyCode(),sourceBefore,source.getLedgerBalance(),r.eodRunId()));
            transactions.save(new DepositAccountTransaction(UUID.randomUUID().toString(),fd.getPayoutAccountId(),reference,reservationId,
                    DepositTransactionType.CREDIT,PaymentOperationType.FIXED_DEPOSIT_MATURITY_PAYOUT,net,fd.getCurrencyCode(),destinationBefore,destination.getLedgerBalance(),r.eodRunId()));
            fd.setPaidInterest(interest);fd.setStatus(FixedDepositStatus.PAID_OUT);fd.setUpdatedAt(OffsetDateTime.now());
            // Persist every local financial mutation before creating the authoritative remote journal.
            entityManager.flush();
            PostingResponse posting=accountingPostings.post(maturityPosting(fd,r,payout),reference,r.eodRunId());
            requirePosted(posting,net,reference);
            payout.recordAccountingPosting(posting.journalNumber(),posting.status().toUpperCase(Locale.ROOT));
            entityManager.flush();
            var clearance=accountingLifecycle.clearance(account.getId(),account.getCurrencyCode());
            if(!clearance.accountingCleared())throw new ApiException(HttpStatus.CONFLICT,"ACCOUNTING_CLEARANCE_FAILED",
                    "Accounting has not cleared the account for closure: "+String.join(", ",clearance.blockers()));
            AccountStatus from=account.getStatus();account.setStatus(AccountStatus.CLOSED);
            OffsetDateTime closedAt=eodTimestamp(r.businessDate());account.setClosedAt(closedAt);
            histories.save(new AccountStatusHistory(UUID.randomUUID().toString(),account.getId(),from,AccountStatus.CLOSED,
                    "FD_MATURITY_PAID",posting.journalNumber(),"eod","SERVICE",r.eodRunId()));
            audits.save(new AuditLog(UUID.randomUUID().toString(),fd.getId(),"MATURE_FIXED_DEPOSIT","SUCCESS","eod",
                    "SERVICE","FD_MATURITY_PAID",posting.journalNumber(),Hashing.sha256(reference),r.eodRunId()));
            maturityClosures.recordCompleted(fd,interest,net,fd.getPayoutAccountId(),reference,r.eodRunId(),r.businessDate());
            String cifId=account.getHolders().stream().filter(holder -> holder.getRole()==HolderRole.PRIMARY)
                    .findFirst().orElseThrow().getCustomerId();
            notificationOutbox.enqueue(cifId,"FD_MATURITY",account.getId(),"fd-"+account.getId()+"-maturity",
                    Map.of("accountId",account.getId(),"maturityDate",fd.getMaturityDate().toString(),
                            "currency",fd.getCurrencyCode(),"maturityAmount",net.setScale(2, RoundingMode.HALF_EVEN).toPlainString()));
            // Surface closure-record constraint/optimistic-lock failures before publishing the lifecycle event.
            entityManager.flush();
            accountingLifecycle.publishClosure(new AccountingLifecycleGateway.AccountClosedEvent(
                    "DEPOSIT-CLOSE:"+account.getId(),"DEPOSIT_ACCOUNT_CLOSED","DEPOSIT_ACCOUNT",account.getId(),
                    account.getCurrencyCode(),r.businessDate(),closedAt,"FD_MATURITY_PAID"),
                    "DEPOSIT-CLOSE:"+account.getId(),r.eodRunId());
            processed++;total=total.add(net);
            if(isCurrentRunPosting(posting,r.eodRunId())){
                postedJournalCount++;postedDebitTotal=postedDebitTotal.add(posting.totalDebit()).setScale(4);
            }
        }
        return new EodResult(r.eodRunId(),r.businessDate(),r.commandReference(),processed,skipped,total,failures,
                postedJournalCount,postedDebitTotal);
    }
    private AccountingRecovery recoverAccrualAccounting(EodRequest request){
        int processed=0,skipped=0;long postedJournalCount=0;
        BigDecimal total=BigDecimal.ZERO.setScale(4),postedDebitTotal=BigDecimal.ZERO.setScale(4);
        Set<String> handled=new HashSet<>();
        List<String> failures=new ArrayList<>();
        for(FixedDepositInterestAccrual review:accruals.findAccountingReviewRequiredThrough(request.businessDate())){
            handled.add(review.getId());skipped++;
            failures.add(review.getFixedDepositId()+":ACCRUAL_ACCOUNTING_REVIEW_REQUIRED");
        }
        for(FixedDepositInterestAccrual accrual:accruals.findPendingAccountingPostingsThrough(request.businessDate())){
            handled.add(accrual.getId());
            if(!accrual.needsAccountingPosting())continue;
            Optional<FixedDeposit> candidate=fds.findByIdForUpdate(accrual.getFixedDepositId());
            if(candidate.isEmpty()){
                failures.add(accrual.getFixedDepositId()+":ACCRUAL_FIXED_DEPOSIT_NOT_FOUND");continue;
            }
            FixedDeposit fd=candidate.get();
            LocalDate lastInterestDate=fd.getMaturityDate().minusDays(1);
            if(accrual.getBusinessDate().isBefore(fd.getValueDate())
                    ||accrual.getBusinessDate().isAfter(lastInterestDate)
                    ||accrual.getInterestAmount()==null||accrual.getInterestAmount().signum()<=0){
                markAccrualReviewRequired(accrual,fd,request,"INVALID_LEGACY_ACCRUAL");
                failures.add(fd.getId()+":ACCRUAL_ACCOUNTING_REVIEW_REQUIRED");skipped++;continue;
            }
            if(isTerminal(fd)){
                if(canAcceptLegacyAccrual(fd,accrual)){
                    acceptLegacyAccrual(accrual,fd,request);skipped++;continue;
                }
                markAccrualReviewRequired(accrual,fd,request,"TERMINAL_STATE_NOT_PROVABLE");
                failures.add(fd.getId()+":ACCRUAL_ACCOUNTING_REVIEW_REQUIRED");skipped++;continue;
            }
            ensureAccountingRegistration(fd.getAccount(),fd,request.eodRunId());
            String reference=accrualReference(fd.getId(),accrual.getBusinessDate());
            PostingResponse posting=accountingPostings.post(
                    accrualPosting(fd,accrual.getBusinessDate(),request,accrual.getInterestAmount()),
                    reference,request.eodRunId());
            requirePosted(posting,accrual.getInterestAmount(),reference);
            accrual.recordAccountingPosting(posting.journalNumber(),posting.status().toUpperCase(Locale.ROOT));
            entityManager.flush();
            processed++;total=total.add(accrual.getInterestAmount()).setScale(4);
            if(isCurrentRunPosting(posting,request.eodRunId())){
                postedJournalCount++;postedDebitTotal=postedDebitTotal.add(posting.totalDebit()).setScale(4);
            }
        }
        return new AccountingRecovery(processed,skipped,total,postedJournalCount,postedDebitTotal,handled,failures);
    }
    private AccountingRecovery recoverMaturityAccounting(EodRequest request){
        int processed=0,skipped=0;long postedJournalCount=0;
        BigDecimal total=BigDecimal.ZERO.setScale(4),postedDebitTotal=BigDecimal.ZERO.setScale(4);
        Set<String> handled=new HashSet<>();
        List<String> failures=new ArrayList<>();
        for(FixedDepositPayout review:payouts.findMaturityAccountingReviewRequiredThrough(request.businessDate())){
            handled.add(review.getId());skipped++;
            failures.add(review.getFixedDepositId()+":PAYOUT_ACCOUNTING_REVIEW_REQUIRED");
        }
        for(FixedDepositPayout payout:payouts.findPendingMaturityAccountingPostingsThrough(
                request.businessDate(),FixedDepositPayoutStatus.COMPLETED)){
            handled.add(payout.getId());
            if(!payout.needsAccountingPosting())continue;
            Optional<FixedDeposit> candidate=fds.findByIdForUpdate(payout.getFixedDepositId());
            if(candidate.isEmpty()){
                failures.add(payout.getFixedDepositId()+":PAYOUT_FIXED_DEPOSIT_NOT_FOUND");continue;
            }
            FixedDeposit fd=candidate.get();
            if(!isRecoverableCompletedMaturity(fd,payout)){
                markPayoutReviewRequired(payout,fd,request,"TERMINAL_STATE_NOT_PROVABLE");
                failures.add(fd.getId()+":PAYOUT_ACCOUNTING_REVIEW_REQUIRED");skipped++;continue;
            }
            if(isTerminal(fd)){
                if(payout.hasNoAccountingDisposition()){
                    acceptLegacyPayout(payout,fd,request);skipped++;continue;
                }
                markPayoutReviewRequired(payout,fd,request,"CLOSED_ACCOUNT_REPOST_NOT_ALLOWED");
                failures.add(fd.getId()+":PAYOUT_ACCOUNTING_REVIEW_REQUIRED");skipped++;continue;
            }
            ensureAccountingRegistration(fd.getAccount(),fd,request.eodRunId());
            String reference=maturityReference(fd.getId());
            PostingResponse posting=accountingPostings.post(maturityPosting(fd,request,payout),reference,
                    request.eodRunId());
            requirePosted(posting,payout.getNetAmount(),reference);
            payout.recordAccountingPosting(posting.journalNumber(),posting.status().toUpperCase(Locale.ROOT));
            entityManager.flush();
            processed++;total=total.add(payout.getNetAmount()).setScale(4);
            if(isCurrentRunPosting(posting,request.eodRunId())){
                postedJournalCount++;postedDebitTotal=postedDebitTotal.add(posting.totalDebit()).setScale(4);
            }
        }
        return new AccountingRecovery(processed,skipped,total,postedJournalCount,postedDebitTotal,handled,failures);
    }
    private boolean isRecoverableCompletedMaturity(FixedDeposit fd,FixedDepositPayout payout){
        return fd.getStatus()==FixedDepositStatus.PAID_OUT
                &&fd.getAccount().getStatus()==AccountStatus.CLOSED
                &&payout.getStatus()==FixedDepositPayoutStatus.COMPLETED
                &&payout.getCompletedAt()!=null
                &&payout.getPrincipal().compareTo(fd.getPrincipal())==0
                &&payout.getInterest().compareTo(fd.getExpectedInterest())==0
                &&fd.getPaidInterest().compareTo(fd.getExpectedInterest())==0
                &&fd.getAccruedInterest().compareTo(fd.getPaidInterest())==0
                &&payout.getNetAmount().compareTo(payout.getPrincipal().add(payout.getInterest()))==0
                &&Objects.equals(payout.getDestinationAccountId(),fd.getPayoutAccountId());
    }
    private boolean isTerminal(FixedDeposit fd){
        return fd.getStatus()==FixedDepositStatus.PAID_OUT||fd.getAccount().getStatus()==AccountStatus.CLOSED;
    }
    private boolean canAcceptLegacyAccrual(FixedDeposit fd,FixedDepositInterestAccrual accrual){
        if(!accrual.hasNoAccountingDisposition()||!"POSTED".equalsIgnoreCase(accrual.getStatus()))return false;
        Optional<FixedDepositPayout> payout=payouts.findFirstByFixedDepositIdAndPayoutTypeOrderByCreatedAtDesc(
                fd.getId(),"MATURITY");
        if(payout.isEmpty()||!isRecoverableCompletedMaturity(fd,payout.get()))return false;
        List<FixedDepositInterestAccrual> history=accruals.findByFixedDepositIdOrderByBusinessDateAsc(fd.getId());
        if(history.isEmpty()||history.stream().anyMatch(item->!"POSTED".equalsIgnoreCase(item.getStatus())))return false;
        BigDecimal accrued=history.stream().map(FixedDepositInterestAccrual::getInterestAmount)
                .reduce(BigDecimal.ZERO,BigDecimal::add).setScale(4);
        return accrued.compareTo(fd.getPaidInterest())==0
                &&history.getLast().getCumulativeInterest().compareTo(fd.getAccruedInterest())==0;
    }
    private void acceptLegacyAccrual(FixedDepositInterestAccrual accrual,FixedDeposit fd,EodRequest request){
        String before=Hashing.sha256(accrual.getSourceReference()+"|ACCOUNTING_UNSET");
        accrual.recordLegacyAccountingAcceptance();
        audits.save(new AuditLog(UUID.randomUUID().toString(),fd.getId(),
                "ACCEPT_LEGACY_FD_ACCRUAL_ACCOUNTING","SUCCESS","eod","SERVICE",
                "CLOSED_LEGACY_ACCOUNTING",before,
                Hashing.sha256(accrual.getSourceReference()+"|LEGACY_ACCEPTED"),request.eodRunId()));
        entityManager.flush();
    }
    private void markAccrualReviewRequired(FixedDepositInterestAccrual accrual,FixedDeposit fd,EodRequest request,
                                           String reason){
        String before=Hashing.sha256(accrual.getSourceReference()+"|"+Objects.toString(
                accrual.getAccountingPostingStatus(),"ACCOUNTING_UNSET"));
        accrual.recordAccountingReviewRequired();
        audits.save(new AuditLog(UUID.randomUUID().toString(),fd.getId(),
                "REVIEW_FD_ACCRUAL_ACCOUNTING","REVIEW_REQUIRED","eod","SERVICE",
                reason,before,Hashing.sha256(accrual.getSourceReference()+"|REVIEW_REQUIRED"),request.eodRunId()));
        entityManager.flush();
    }
    private void acceptLegacyPayout(FixedDepositPayout payout,FixedDeposit fd,EodRequest request){
        String before=Hashing.sha256(payout.getSourceReference()+"|ACCOUNTING_UNSET");
        payout.recordLegacyAccountingAcceptance();
        audits.save(new AuditLog(UUID.randomUUID().toString(),fd.getId(),
                "ACCEPT_LEGACY_FD_PAYOUT_ACCOUNTING","SUCCESS","eod","SERVICE",
                "CLOSED_LEGACY_ACCOUNTING",before,
                Hashing.sha256(payout.getSourceReference()+"|LEGACY_ACCEPTED"),request.eodRunId()));
        entityManager.flush();
    }
    private void markPayoutReviewRequired(FixedDepositPayout payout,FixedDeposit fd,EodRequest request,
                                          String reason){
        String before=Hashing.sha256(payout.getSourceReference()+"|"+Objects.toString(
                payout.getAccountingPostingStatus(),"ACCOUNTING_UNSET"));
        payout.recordAccountingReviewRequired();
        audits.save(new AuditLog(UUID.randomUUID().toString(),fd.getId(),
                "REVIEW_FD_PAYOUT_ACCOUNTING","REVIEW_REQUIRED","eod","SERVICE",
                reason,before,Hashing.sha256(payout.getSourceReference()+"|REVIEW_REQUIRED"),request.eodRunId()));
        entityManager.flush();
    }
    private void ensureAccountingRegistration(DepositAccount account,FixedDeposit fd,String correlationId){
        try{accountingLifecycle.clearance(account.getId(),account.getCurrencyCode());}
        catch(HttpClientErrorException.NotFound missing){
            OffsetDateTime openedAt=account.getOpenedAt()==null?account.getCreatedAt():account.getOpenedAt();
            accountingLifecycle.publishOpening(new AccountingLifecycleGateway.AccountOpenedEvent(
                    "DEPOSIT-OPEN:"+account.getId(),"DEPOSIT_ACCOUNT_OPENED","DEPOSIT_ACCOUNT",account.getId(),
                    account.getProductId(),fd.getCurrencyCode(),openedAt.toLocalDate(),openedAt),
                    "DEPOSIT-OPEN:"+account.getId(),correlationId);
        }
    }
    private FixedDepositPosting accrualPosting(FixedDeposit fd,LocalDate effectiveDate,EodRequest request,
                                                BigDecimal amount){
        return new FixedDepositPosting(accrualReference(fd.getId(),effectiveDate),"INTEREST_ACCRUAL",
                fd.getAccount().getId(),fd.getAccount().getProductId(),fd.getCurrencyCode(),request.businessDate(),
                eodTimestamp(request.businessDate()),List.of(new PostingComponent("INTEREST",amount)),
                null,null,null,"EOD_ACCRUAL","FD interest accrual for "+effectiveDate);
    }
    private FixedDepositPosting maturityPosting(FixedDeposit fd,EodRequest request,FixedDepositPayout payout){
        return new FixedDepositPosting(maturityReference(fd.getId()),"MATURITY_PAYOUT",fd.getAccount().getId(),
                fd.getAccount().getProductId(),fd.getCurrencyCode(),request.businessDate(),
                eodTimestamp(request.businessDate()),List.of(new PostingComponent("PRINCIPAL",payout.getPrincipal()),
                new PostingComponent("INTEREST",payout.getInterest())),null,payout.getDestinationAccountId(),null,
                "FD_MATURITY_PAID","Fixed Deposit maturity payout");
    }
    private BigDecimal accrualAmount(FixedDeposit fd,LocalDate effectiveDate,LocalDate lastInterestDate){
        BigDecimal amount=effectiveDate.equals(lastInterestDate)
                ? fd.getExpectedInterest().subtract(fd.getAccruedInterest()).setScale(4,RoundingMode.HALF_EVEN)
                : calculator.dailyAccrual(fd.getPrincipal(),fd.getBookedAnnualRate());
        if(amount.signum()<=0)throw new ApiException(HttpStatus.CONFLICT,"FD_ACCRUAL_AMOUNT_INVALID",
                "Fixed Deposit accrual must be positive for "+fd.getId()+" on "+effectiveDate);
        return amount;
    }
    private void requirePosted(PostingResponse posting,BigDecimal expectedTotal,String reference){
        if(posting==null||posting.journalNumber()==null||posting.journalNumber().isBlank()||
                posting.journalNumber().length()>100||!"POSTED".equalsIgnoreCase(posting.status())||
                posting.totalDebit()==null||posting.totalDebit().compareTo(expectedTotal)!=0||
                posting.correlationId()==null||posting.correlationId().isBlank()||posting.correlationId().length()>64){
            throw new ApiException(HttpStatus.BAD_GATEWAY,"ACCOUNTING_POSTING_INVALID",
                    "Accounting returned an invalid posting result for "+reference);
        }
    }
    private boolean isCurrentRunPosting(PostingResponse posting,String eodRunId){
        return eodRunId.equals(posting.correlationId());
    }
    static String accrualReference(String fixedDepositId,LocalDate businessDate){
        return "FD-ACCRUAL:"+fixedDepositId+":"+businessDate;
    }
    static String maturityReference(String fixedDepositId){return "FD-MATURITY:"+fixedDepositId;}
    private static OffsetDateTime eodTimestamp(LocalDate businessDate){
        return businessDate.atStartOfDay().atOffset(ZoneOffset.UTC);
    }
    private record AccountingRecovery(int processed,int skipped,BigDecimal totalAmount,long postedJournalCount,
                                      BigDecimal postedDebitTotal,Set<String> handledIds,List<String> failures){}
    @Transactional(readOnly=true) public ReadinessResponse readiness(){
        long funding=fds.countByStatus(FixedDepositStatus.PENDING_FUNDING), payout=fds.countByStatus(FixedDepositStatus.PAYOUT_PENDING);
        List<String> blockers=new ArrayList<>();if(funding>0)blockers.add("PENDING_FUNDING="+funding);if(payout>0)blockers.add("PENDING_PAYOUTS="+payout);
        return new ReadinessResponse(blockers.isEmpty(),funding,payout,blockers);
    }
}
