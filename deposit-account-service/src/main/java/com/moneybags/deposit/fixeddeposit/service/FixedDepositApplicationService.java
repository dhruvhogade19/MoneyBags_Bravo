package com.moneybags.deposit.fixeddeposit.service;

import com.moneybags.deposit.domain.DomainTypes.*;
import com.moneybags.deposit.entity.*;
import com.moneybags.deposit.exception.ApiException;
import com.moneybags.deposit.fixeddeposit.calculation.FixedDepositInterestCalculator;
import com.moneybags.deposit.fixeddeposit.dto.FixedDepositRequests.BookingRequest;
import com.moneybags.deposit.fixeddeposit.dto.FixedDepositResponses.*;
import com.moneybags.deposit.fixeddeposit.entity.*;
import com.moneybags.deposit.fixeddeposit.integration.FixedDepositProductGateway;
import com.moneybags.deposit.fixeddeposit.repository.*;
import com.moneybags.deposit.integration.BankingReferenceGateway;
import com.moneybags.deposit.integration.AccountingLifecycleGateway;
import com.moneybags.deposit.repository.*;
import com.moneybags.deposit.service.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class FixedDepositApplicationService {
    private final FixedDepositRepository fdRepository; private final FixedDepositRateSnapshotRepository snapshotRepository;
    private final FixedDepositInterestAccrualRepository accrualRepository; private final DepositAccountRepository accountRepository;
    private final AccountBalanceRepository balanceRepository; private final DepositAccountTransactionRepository transactionRepository;
    private final FundReservationRepository reservationRepository;
    private final AccountNomineeRepository nomineeRepository; private final AccountStatusHistoryRepository historyRepository;
    private final AuditLogRepository auditRepository; private final BankingReferenceGateway customers;
    private final FixedDepositProductGateway products; private final FixedDepositInterestCalculator calculator;
    private final AccountNumberGenerator numberGenerator; private final PiiProtector pii; private final NotificationOutboxService notificationOutbox; private final AccountingLifecycleGateway accountingLifecycle;

    public FixedDepositApplicationService(FixedDepositRepository fdRepository, FixedDepositRateSnapshotRepository snapshotRepository,
        FixedDepositInterestAccrualRepository accrualRepository, DepositAccountRepository accountRepository,
        AccountBalanceRepository balanceRepository, DepositAccountTransactionRepository transactionRepository,
        FundReservationRepository reservationRepository,
        AccountNomineeRepository nomineeRepository, AccountStatusHistoryRepository historyRepository, AuditLogRepository auditRepository,
        BankingReferenceGateway customers, FixedDepositProductGateway products, FixedDepositInterestCalculator calculator,
        AccountNumberGenerator numberGenerator, PiiProtector pii, NotificationOutboxService notificationOutbox, AccountingLifecycleGateway accountingLifecycle) {
        this.fdRepository=fdRepository; this.snapshotRepository=snapshotRepository; this.accrualRepository=accrualRepository;
        this.accountRepository=accountRepository; this.balanceRepository=balanceRepository; this.transactionRepository=transactionRepository;
        this.reservationRepository=reservationRepository;
        this.nomineeRepository=nomineeRepository; this.historyRepository=historyRepository; this.auditRepository=auditRepository;
        this.customers=customers; this.products=products; this.calculator=calculator; this.numberGenerator=numberGenerator; this.pii=pii; this.notificationOutbox=notificationOutbox;this.accountingLifecycle=accountingLifecycle;
    }

    @Transactional
    public FixedDepositView book(BookingRequest r, String operationReference, String actor, String correlationId) {
        var primaryCustomer=validateCustomers(r);
        LocalDate valueDate=LocalDate.now();
        var terms=products.resolve(r.productCode(),r.productVersion(),r.principal(),r.currency(),r.tenureValue(),
                r.tenureUnit(),r.interestPayoutFrequency(),valueDate,primaryCustomer.age(),
                primaryCustomer.customerType(),primaryCustomer.customerCategory(),primaryCustomer.kycVerified());
        var calculation=calculator.calculate(r.principal(),terms.annualRate(),valueDate,r.tenureValue(),r.tenureUnit(),terms.compoundingFrequency());
        AccountBalance source=lockEligibleLinkedAccount(r.fundingAccountId(),r.primaryCustomerId(),r.currency(),true);
        lockEligibleLinkedAccount(r.payoutAccountId(),r.primaryCustomerId(),r.currency(),false);
        if (source.getAvailableBalance().compareTo(r.principal())<0)
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INSUFFICIENT_FUNDS","Funding account has insufficient available balance");

        String accountId=UUID.randomUUID().toString(), fdId=UUID.randomUUID().toString();
        DepositAccount account=new DepositAccount(accountId,numberGenerator.next(),r.productCode(),r.productVersion(),
                terms.productName(),ProductSubtype.FIXED_DEPOSIT,r.currency(),r.servicingBranchId(),
                OperatingInstruction.SINGLE,r.externalReference(),actor);
        for(String customerId:new LinkedHashSet<>(r.customerIds())) account.addHolder(new AccountHolder(UUID.randomUUID().toString(),
                customerId,customerId.equals(r.primaryCustomerId())?HolderRole.PRIMARY:HolderRole.JOINT,"SINGLE",null));
        account.setBalanceProjection(AccountBalance.initial(r.currency(),"FD-OPEN-"+fdId));
        account=accountRepository.saveAndFlush(account);

        FixedDeposit fd=new FixedDeposit(fdId,account,r.principal(),r.currency(),valueDate,calculation.maturityDate(),
                r.tenureValue(),r.tenureUnit(),terms.annualRate(),terms.calculationMethod(),terms.compoundingFrequency(),
                terms.payoutFrequency(),terms.dayCountConvention(),calculation.interest(),calculation.maturityAmount(),
                r.fundingAccountId(),r.payoutAccountId());
        fd=fdRepository.save(fd);
        snapshotRepository.save(new FixedDepositRateSnapshot(UUID.randomUUID().toString(),fdId,r.productCode(),r.productVersion(),
                terms.rateSlabCode(),terms.interestPolicyVersion(),terms.annualRate(),terms.calculationMethod(),
                terms.compoundingFrequency().name(),terms.payoutFrequency().name(),terms.dayCountConvention().name(),terms.ruleSnapshotJson()));
        if(r.nominees()!=null) for(var n:r.nominees()) nomineeRepository.save(new AccountNominee(UUID.randomUUID().toString(),accountId,
                n.customerReference(),pii.encrypt(n.name()),n.relationshipCode(),n.allocationPercentage()));

        String reservationId=UUID.randomUUID().toString();
        FundReservation reservation=new FundReservation(reservationId,operationReference,PaymentOperationType.FIXED_DEPOSIT_FUNDING,
                r.fundingAccountId(),accountId,null,r.primaryCustomerId(),r.principal(),r.currency(),OffsetDateTime.now().plusMinutes(5));
        reservation.transitionTo(ReservationStatus.SETTLED);reservationRepository.save(reservation);
        String debitRef=operationReference+"-SOURCE", creditRef=operationReference+"-FD";
        BigDecimal sourceBefore=source.getLedgerBalance(); source.debit(r.principal(),debitRef);
        AccountBalance fdBalance=balanceRepository.findByAccountIdForUpdate(accountId).orElseThrow();
        BigDecimal fdBefore=fdBalance.getLedgerBalance(); fdBalance.creditLedgerOnly(r.principal(),creditRef);
        transactionRepository.save(new DepositAccountTransaction(UUID.randomUUID().toString(),r.fundingAccountId(),operationReference,reservationId,
                DepositTransactionType.DEBIT,PaymentOperationType.FIXED_DEPOSIT_FUNDING,r.principal(),r.currency(),sourceBefore,source.getLedgerBalance(),correlationId));
        transactionRepository.save(new DepositAccountTransaction(UUID.randomUUID().toString(),accountId,operationReference,reservationId,
                DepositTransactionType.CREDIT,PaymentOperationType.FIXED_DEPOSIT_FUNDING,r.principal(),r.currency(),fdBefore,fdBalance.getLedgerBalance(),correlationId));
        fd.setStatus(FixedDepositStatus.ACTIVE); fd.setUpdatedAt(OffsetDateTime.now()); account.setStatus(AccountStatus.ACTIVE);
        account.setOpenedAt(OffsetDateTime.now()); account.setUpdatedAt(OffsetDateTime.now()); account.setUpdatedBy(actor);
        OffsetDateTime openedAt=account.getOpenedAt();accountingLifecycle.publishOpening(new AccountingLifecycleGateway.AccountOpenedEvent("DEPOSIT-OPEN:"+accountId,"DEPOSIT_ACCOUNT_OPENED","DEPOSIT_ACCOUNT",accountId,r.productCode(),r.currency(),openedAt.toLocalDate(),openedAt),"DEPOSIT-OPEN:"+accountId,correlationId);
        historyRepository.save(new AccountStatusHistory(UUID.randomUUID().toString(),accountId,AccountStatus.PENDING_ACTIVATION,
                AccountStatus.ACTIVE,"FD_FUNDED",null,actor,"USER",correlationId));
        auditRepository.save(new AuditLog(UUID.randomUUID().toString(),fdId,"BOOK_FIXED_DEPOSIT","SUCCESS",actor,"USER",
                "FD_FUNDED",null,Hashing.sha256(fdId+r.principal()),correlationId));
        notificationOutbox.enqueue(r.primaryCustomerId(), "DEPOSIT_ACCOUNT_CREATED", accountId,
                "deposit-account-" + accountId + "-created", Map.of("accountType", "Fixed Deposit", "accountId", accountId));
        return view(fd);
    }

    @Transactional(readOnly=true) public FixedDepositView get(String id){return view(load(id));}
    @Transactional(readOnly=true) public Page<FixedDepositView> search(String customerId, FixedDepositStatus status,
        LocalDate maturingBefore, Pageable pageable){return fdRepository.search(blank(customerId),status,maturingBefore,pageable).map(this::view);}
    @Transactional(readOnly=true) public List<AccrualView> accruals(String id){load(id); return accrualRepository.findByFixedDepositIdOrderByBusinessDateAsc(id).stream()
        .map(a->new AccrualView(a.getBusinessDate(),a.getAccrualBase(),a.getAnnualRate(),a.getInterestAmount(),a.getCumulativeInterest(),a.getStatus(),a.getCreatedAt())).toList();}
    @Transactional(readOnly=true) public ProjectedScheduleResponse projectedSchedule(String id){
        FixedDeposit f=load(id); return new ProjectedScheduleResponse(f.getId(),f.getValueDate(),f.getMaturityDate(),
                f.getPrincipal(),f.getBookedAnnualRate(),f.getExpectedInterest(),f.getExpectedMaturityAmount(),f.getPayoutFrequency());
    }

    private BankingReferenceGateway.CustomerProfile validateCustomers(BookingRequest r){
        if(!r.customerIds().contains(r.primaryCustomerId())||new HashSet<>(r.customerIds()).size()!=r.customerIds().size())
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_HOLDERS","Primary holder must be present and holders must be unique");
        BankingReferenceGateway.CustomerProfile primary=null;
        for(String id:r.customerIds()) {
            var customer=customers.customerProfile(id);
            if(!customer.eligible())
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"CUSTOMER_NOT_ELIGIBLE","A holder is not eligible");
            if(id.equals(r.primaryCustomerId())) primary=customer;
        }
        if(r.nominees()!=null&&!r.nominees().isEmpty()){
            BigDecimal total=r.nominees().stream().map(n->n.allocationPercentage()).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(total.compareTo(new BigDecimal("100.00"))!=0) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_NOMINEE_ALLOCATION","Nominee allocations must total 100 percent");
        }
        return primary;
    }
    private AccountBalance lockEligibleLinkedAccount(String accountId,String customerId,String currency,boolean funding){
        AccountBalance balance=balanceRepository.findByAccountIdForUpdate(accountId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"LINKED_ACCOUNT_NOT_FOUND","Linked account not found"));
        DepositAccount a=balance.getAccount();
        if(a.getProductSubtype()==ProductSubtype.FIXED_DEPOSIT||a.getStatus()!=AccountStatus.ACTIVE||!currency.equals(a.getCurrencyCode())||
                a.getHolders().stream().noneMatch(h->h.getCustomerId().equals(customerId)&&h.getStatus()==RecordStatus.ACTIVE))
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,funding?"FUNDING_ACCOUNT_INELIGIBLE":"PAYOUT_ACCOUNT_INELIGIBLE","Linked account is not eligible");
        return balance;
    }
    private FixedDeposit load(String id){return fdRepository.findDetailedById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"FIXED_DEPOSIT_NOT_FOUND","Fixed deposit not found"));}
    private FixedDepositView view(FixedDeposit f){DepositAccount a=f.getAccount(); return new FixedDepositView(f.getId(),a.getId(),mask(a.getAccountNumber()),a.getProductId(),a.getProductVersion(),f.getStatus(),f.getPrincipal(),f.getCurrencyCode(),f.getBookedAnnualRate(),f.getValueDate(),f.getMaturityDate(),f.getExpectedInterest(),f.getExpectedMaturityAmount(),f.getAccruedInterest(),f.getFundingAccountId(),f.getPayoutAccountId(),f.getVersion());}
    private String mask(String n){return n.length()<4?"****":"****"+n.substring(n.length()-4);} private String blank(String v){return v==null||v.isBlank()?null:v;}
}
