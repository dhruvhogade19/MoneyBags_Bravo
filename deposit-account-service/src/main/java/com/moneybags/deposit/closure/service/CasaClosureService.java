package com.moneybags.deposit.closure.service;

import com.moneybags.deposit.closure.dto.AccountClosureRequests.*;
import com.moneybags.deposit.closure.dto.AccountClosureResponses.*;
import com.moneybags.deposit.closure.entity.*;
import com.moneybags.deposit.closure.repository.*;
import com.moneybags.deposit.domain.DomainTypes.*;
import com.moneybags.deposit.entity.*;
import com.moneybags.deposit.exception.ApiException;
import com.moneybags.deposit.repository.*;
import com.moneybags.deposit.service.Hashing;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class CasaClosureService {
    private static final String POLICY="CASA-CLOSE-V1";
    private static final Set<ClosureRequestStatus> TERMINAL=EnumSet.of(ClosureRequestStatus.REJECTED,
        ClosureRequestStatus.CLOSED,ClosureRequestStatus.CANCELLED);
    private final DepositAccountRepository accounts; private final AccountBalanceRepository balances;
    private final FundReservationRepository reservations; private final AccountMandateRepository mandates;
    private final AccountLimitRepository limits; private final DepositAccountTransactionRepository transactions;
    private final AccountClosureRequestRepository requests; private final AccountClosureCheckRepository checks;
    private final AccountClosureSettlementRepository settlements; private final AccountStatusHistoryRepository histories;
    private final AuditLogRepository audits;

    public CasaClosureService(DepositAccountRepository accounts,AccountBalanceRepository balances,
        FundReservationRepository reservations,AccountMandateRepository mandates,AccountLimitRepository limits,
        DepositAccountTransactionRepository transactions,AccountClosureRequestRepository requests,
        AccountClosureCheckRepository checks,AccountClosureSettlementRepository settlements,
        AccountStatusHistoryRepository histories,AuditLogRepository audits){this.accounts=accounts;this.balances=balances;
        this.reservations=reservations;this.mandates=mandates;this.limits=limits;this.transactions=transactions;
        this.requests=requests;this.checks=checks;this.settlements=settlements;this.histories=histories;this.audits=audits;}

    @Transactional(readOnly=true)
    public ClosureQuoteResponse quote(String accountId,ClosureQuoteRequest request){
        DepositAccount account=load(accountId,false); Validation validation=validate(account,request.customerId(),
            request.destinationAccountId(),request.requestedClosureDate(),false);
        BigDecimal balance=account.getBalance().getLedgerBalance();
        return new ClosureQuoteResponse(validation.blockers.isEmpty(),accountId,account.getProductSubtype(),balance,
            zero(),balance.max(zero()),account.getCurrencyCode(),request.destinationAccountId(),validation.blockers,
            OffsetDateTime.now().plusMinutes(15));
    }

    @Transactional
    public ClosureRequestView close(String accountId,CasaClosureRequest input,String actor,String correlationId,String reference){
        if(!requests.findActive(accountId,TERMINAL).isEmpty()) throw new ApiException(HttpStatus.CONFLICT,
            "CLOSURE_ALREADY_ACTIVE","An active closure request already exists for this account");
        DepositAccount account=load(accountId,true);
        AccountClosureRequest request=requests.save(new AccountClosureRequest(UUID.randomUUID().toString(),accountId,
            ClosureType.CASA_CUSTOMER_REQUEST,actor,input.channel(),input.requestedClosureDate(),input.reasonCode(),
            input.reasonText(),input.destinationAccountId(),POLICY,correlationId));
        request.transition(ClosureRequestStatus.VALIDATING);
        Validation validation=validate(account,input.customerId(),input.destinationAccountId(),input.requestedClosureDate(),true);
        persistChecks(request.getId(),validation.results);
        if(!validation.blockers.isEmpty()){
            request.setRejectionCode("CLOSURE_CHECK_FAILED");request.setRejectionDetails(String.join("; ",validation.blockers));
            request.transition(ClosureRequestStatus.REJECTED);audit(accountId,"REQUEST_CASA_CLOSURE","REJECTED",actor,correlationId);
            return view(request);
        }
        AccountStatus from=account.getStatus();account.setStatus(AccountStatus.CLOSURE_PENDING);account.setUpdatedAt(OffsetDateTime.now());account.setUpdatedBy(actor);
        histories.save(new AccountStatusHistory(UUID.randomUUID().toString(),accountId,from,AccountStatus.CLOSURE_PENDING,
            input.reasonCode(),input.reasonText(),actor,"USER",correlationId));
        request.transition(ClosureRequestStatus.SETTLEMENT_PENDING);
        BigDecimal amount=account.getBalance().getLedgerBalance();
        AccountClosureSettlement settlement=new AccountClosureSettlement(UUID.randomUUID().toString(),request.getId(),amount,
            zero(),zero(),zero(),zero(),zero(),amount,account.getCurrencyCode(),input.destinationAccountId(),reference);
        settlement=settlements.save(settlement);
        if(amount.signum()>0) transfer(account,input.destinationAccountId(),input.customerId(),amount,reference,correlationId);
        revokeConfiguration(accountId);
        AccountBalance source=balances.findByAccountIdForUpdate(accountId).orElseThrow();
        if(source.getLedgerBalance().signum()!=0||source.getAvailableBalance().signum()!=0||source.getBlockedAmount().signum()!=0)
            throw new ApiException(HttpStatus.CONFLICT,"CLOSURE_SETTLEMENT_NOT_ZERO","Account balances are not zero after settlement");
        settlement.complete();request.transition(ClosureRequestStatus.READY_TO_CLOSE);
        account.setStatus(AccountStatus.CLOSED);account.setClosedAt(OffsetDateTime.now());account.setUpdatedAt(OffsetDateTime.now());
        histories.save(new AccountStatusHistory(UUID.randomUUID().toString(),accountId,AccountStatus.CLOSURE_PENDING,
            AccountStatus.CLOSED,"CASA_CLOSURE_SETTLED",input.reasonText(),actor,"USER",correlationId));
        request.transition(ClosureRequestStatus.CLOSED);audit(accountId,"CLOSE_CASA_ACCOUNT","SUCCESS",actor,correlationId);
        return view(request);
    }

    @Transactional(readOnly=true) public ClosureRequestView get(String accountId,String requestId){
        AccountClosureRequest r=requests.findById(requestId).filter(x->x.getAccountId().equals(accountId)).orElseThrow(()->
            new ApiException(HttpStatus.NOT_FOUND,"CLOSURE_REQUEST_NOT_FOUND","Closure request not found"));return view(r);}
    @Transactional(readOnly=true) public ClosureRequestView getById(String requestId){
        return view(requests.findById(requestId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,
            "CLOSURE_REQUEST_NOT_FOUND","Closure request not found")));
    }
    @Transactional(readOnly=true) public List<ClosureRequestView> history(String accountId){load(accountId,false);return requests.findByAccountIdOrderByCreatedAtDesc(accountId).stream().map(this::view).toList();}
    @Transactional public ClosureRequestView cancel(String accountId,String requestId,CancelClosureRequest input,String actor,String correlationId){
        AccountClosureRequest r=requests.findByIdForUpdate(requestId).filter(x->x.getAccountId().equals(accountId)).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"CLOSURE_REQUEST_NOT_FOUND","Closure request not found"));
        if(r.getStatus()!=ClosureRequestStatus.REQUESTED&&r.getStatus()!=ClosureRequestStatus.VALIDATING)
            throw new ApiException(HttpStatus.CONFLICT,"CLOSURE_CANNOT_BE_CANCELLED","Settlement has started or closure is terminal");
        r.transition(ClosureRequestStatus.CANCELLED);audit(accountId,"CANCEL_ACCOUNT_CLOSURE","SUCCESS",actor,correlationId);return view(r);
    }

    private Validation validate(DepositAccount a,String customerId,String destinationId,LocalDate date,boolean locked){
        List<CheckResult> results=new ArrayList<>();
        add(results,"ACCOUNT_SUBTYPE",a.getProductSubtype()!=ProductSubtype.FIXED_DEPOSIT,"Only savings/current accounts use CASA closure");
        add(results,"ACCOUNT_STATUS",EnumSet.of(AccountStatus.ACTIVE,AccountStatus.BLOCKED,AccountStatus.DORMANT).contains(a.getStatus()),"Account must be active, blocked or dormant");
        add(results,"PRIMARY_OWNERSHIP",a.getHolders().stream().anyMatch(h->h.getCustomerId().equals(customerId)&&h.getRole()==HolderRole.PRIMARY&&h.getStatus()==RecordStatus.ACTIVE),"Requester must be the active primary holder");
        add(results,"CLOSURE_DATE",date.equals(LocalDate.now()),"Closure date must be today's business date");
        AccountBalance b=a.getBalance(); add(results,"NON_NEGATIVE_BALANCE",b!=null&&b.getLedgerBalance().signum()>=0,"Negative or missing balance must be resolved");
        add(results,"NO_BLOCKED_AMOUNT",b!=null&&b.getBlockedAmount().signum()==0,"Blocked amount must be zero");
        add(results,"BALANCE_RECONCILED",b!=null&&b.getLedgerBalance().compareTo(b.getAvailableBalance())==0,"Ledger and available balances must agree");
        add(results,"NO_ACTIVE_RESERVATIONS",reservations.countBySourceAccountIdAndStatus(a.getId(),ReservationStatus.ACTIVE)==0,"Active fund reservations must be released");
        long activeMandates=mandates.countByAccountIdAndStatus(a.getId(),RecordStatus.ACTIVE);
        add(results,"MANDATES_REVOCABLE",true,activeMandates==0?"No active mandates":"Active mandates will be revoked during closure: "+activeMandates);
        boolean destinationRequired=b!=null&&b.getLedgerBalance().signum()>0;
        if(destinationRequired)add(results,"DESTINATION_REQUIRED",destinationId!=null&&!destinationId.isBlank(),"Destination account is required for a positive balance");
        if(destinationId!=null&&!destinationId.isBlank()){
            Optional<DepositAccount> destination=accounts.findDetailedById(destinationId);
            boolean valid=destination.isPresent()&&!destinationId.equals(a.getId())&&destination.get().getStatus()==AccountStatus.ACTIVE
                &&destination.get().getProductSubtype()!=ProductSubtype.FIXED_DEPOSIT&&destination.get().getCurrencyCode().equals(a.getCurrencyCode())
                &&destination.get().getHolders().stream().anyMatch(h->h.getCustomerId().equals(customerId)&&h.getStatus()==RecordStatus.ACTIVE);
            add(results,"DESTINATION_ACCOUNT",valid,"Destination must be an active same-currency CASA account owned by the requester");
        }
        return new Validation(results,results.stream().filter(x->!x.passed).map(CheckResult::details).toList());
    }
    private void transfer(DepositAccount sourceAccount,String destinationId,String customerId,BigDecimal amount,String reference,String correlationId){
        List<String> ids=new ArrayList<>(List.of(sourceAccount.getId(),destinationId));ids.sort(String::compareTo);
        Map<String,AccountBalance> locked=new HashMap<>();for(String id:ids)locked.put(id,balances.findByAccountIdForUpdate(id).orElseThrow());
        AccountBalance source=locked.get(sourceAccount.getId()),destination=locked.get(destinationId);
        String reservationId=UUID.randomUUID().toString();FundReservation reservation=new FundReservation(reservationId,reference,
            PaymentOperationType.CASA_ACCOUNT_CLOSURE,sourceAccount.getId(),destinationId,null,customerId,amount,
            sourceAccount.getCurrencyCode(),OffsetDateTime.now().plusMinutes(5));reservation.transitionTo(ReservationStatus.SETTLED);reservations.save(reservation);
        BigDecimal sourceBefore=source.getLedgerBalance(),destinationBefore=destination.getLedgerBalance();source.debit(amount,reference+"-SOURCE");destination.credit(amount,reference+"-DEST");
        transactions.save(new DepositAccountTransaction(UUID.randomUUID().toString(),sourceAccount.getId(),reference,reservationId,DepositTransactionType.DEBIT,PaymentOperationType.CASA_ACCOUNT_CLOSURE,amount,sourceAccount.getCurrencyCode(),sourceBefore,source.getLedgerBalance(),correlationId));
        transactions.save(new DepositAccountTransaction(UUID.randomUUID().toString(),destinationId,reference,reservationId,DepositTransactionType.CREDIT,PaymentOperationType.CASA_ACCOUNT_CLOSURE,amount,sourceAccount.getCurrencyCode(),destinationBefore,destination.getLedgerBalance(),correlationId));
    }
    private void revokeConfiguration(String accountId){OffsetDateTime now=OffsetDateTime.now();for(AccountMandate m:mandates.findByAccountId(accountId))if(m.getStatus()==RecordStatus.ACTIVE)m.setStatus(RecordStatus.REVOKED);for(AccountLimit l:limits.findByAccountId(accountId))if(l.getEffectiveTo()==null||l.getEffectiveTo().isAfter(now))l.setEffectiveTo(now);}
    private DepositAccount load(String id,boolean lock){
        DepositAccount account=(lock?accounts.findByIdForUpdate(id):accounts.findDetailedById(id)).orElseThrow(()->
            new ApiException(HttpStatus.NOT_FOUND,"ACCOUNT_NOT_FOUND","Deposit account not found"));
        if(lock){account.getHolders().size();account.getBalance().getAccountId();}
        return account;
    }
    private void persistChecks(String requestId,List<CheckResult> values){values.forEach(c->checks.save(new AccountClosureCheck(UUID.randomUUID().toString(),requestId,c.code,c.passed,c.details)));}
    private void add(List<CheckResult> list,String code,boolean passed,String details){list.add(new CheckResult(code,passed,passed?"Check passed":details));}
    private ClosureRequestView view(AccountClosureRequest r){List<ClosureCheckView> cv=checks.findByClosureRequestIdOrderByCheckedAtAsc(r.getId()).stream().map(c->new ClosureCheckView(c.getCheckCode(),c.getStatus(),c.getDetails(),c.getCheckedAt())).toList();ClosureSettlementView sv=settlements.findByClosureRequestId(r.getId()).map(s->new ClosureSettlementView(s.getPrincipalAmount(),s.getOriginalInterestAmount(),s.getRecalculatedInterestAmount(),s.getInterestPenaltyAmount(),s.getClosureFeeAmount(),s.getTaxAmount(),s.getNetPayoutAmount(),s.getCurrencyCode(),s.getDestinationAccountId(),s.getTransactionReference(),s.getStatus())).orElse(null);return new ClosureRequestView(r.getId(),r.getAccountId(),r.getClosureType(),r.getStatus(),r.getRequestedBy(),r.getRequestedChannel(),r.getRequestedDate(),r.getReasonCode(),r.getReasonText(),r.getDestinationAccountId(),r.getRejectionCode(),r.getRejectionDetails(),r.getPolicyVersion(),cv,sv,r.getCreatedAt(),r.getCompletedAt(),r.getVersion());}
    private void audit(String accountId,String action,String outcome,String actor,String correlationId){audits.save(new AuditLog(UUID.randomUUID().toString(),accountId,action,outcome,actor,"USER",action,null,Hashing.sha256(accountId+action),correlationId));}
    private BigDecimal zero(){return BigDecimal.ZERO.setScale(4);} private record CheckResult(String code,boolean passed,String details){} private record Validation(List<CheckResult> results,List<String> blockers){}
}
