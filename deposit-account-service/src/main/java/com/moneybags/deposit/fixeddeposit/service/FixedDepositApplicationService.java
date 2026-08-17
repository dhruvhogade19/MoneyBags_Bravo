package com.moneybags.deposit.fixeddeposit.service;

import com.moneybags.deposit.domain.DomainTypes.*;
import com.moneybags.deposit.entity.*;
import com.moneybags.deposit.exception.ApiException;
import com.moneybags.deposit.fixeddeposit.calculation.FixedDepositInterestCalculator;
import com.moneybags.deposit.fixeddeposit.dto.FixedDepositRequests.BookingRequest;
import com.moneybags.deposit.fixeddeposit.dto.FixedDepositResponses.*;
import com.moneybags.deposit.dto.PaymentOperationRequests.*;
import com.moneybags.deposit.dto.PaymentOperationResponses.*;
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
        // Preserve the value date used for the quote. Null keeps older clients compatible.
        LocalDate valueDate=r.valueDate()==null?LocalDate.now():r.valueDate();
        var terms=products.resolve(r.productCode(),r.productVersion(),r.principal(),r.currency(),r.tenureValue(),
                r.tenureUnit(),r.interestPayoutFrequency(),valueDate,primaryCustomer.age(),
                primaryCustomer.monthlyIncome(),primaryCustomer.customerType(),primaryCustomer.customerCategory(),primaryCustomer.kycVerified());
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

        auditRepository.save(new AuditLog(UUID.randomUUID().toString(),fdId,"BOOK_FIXED_DEPOSIT","SUCCESS",actor,"USER",
                "PENDING_FUNDING",null,Hashing.sha256(fdId+r.principal()),correlationId));
        return view(fd);
    }

    @Transactional
    public FixedDepositFundingReservationView reserveFunding(FixedDepositFundingReservationRequest r,
                                                               String actor, String correlationId) {
        Optional<FundReservation> existing=reservationRepository.findByPaymentIdAndOperationType(
                r.paymentId(),PaymentOperationType.FIXED_DEPOSIT_FUNDING);
        if(existing.isPresent())return fundingReservationView(existing.get());
        FixedDeposit fd=fdRepository.findByIdForUpdate(r.fixedDepositId()).orElseThrow(()->
                new ApiException(HttpStatus.NOT_FOUND,"FIXED_DEPOSIT_NOT_FOUND","Fixed deposit not found"));
        if(fd.getStatus()!=FixedDepositStatus.PENDING_FUNDING)
            throw new ApiException(HttpStatus.CONFLICT,"FIXED_DEPOSIT_NOT_PENDING_FUNDING","Fixed deposit is not awaiting funding");
        if(!fd.getFundingAccountId().equals(r.sourceAccountId())||fd.getPrincipal().compareTo(r.amount())!=0||
                !fd.getCurrencyCode().equals(r.currencyCode()))
            throw new ApiException(HttpStatus.CONFLICT,"FIXED_DEPOSIT_FUNDING_MISMATCH","Funding request does not match the booked fixed deposit");
        String customerId=String.valueOf(r.requestorCustomerId());
        AccountBalance source=lockEligibleLinkedAccount(r.sourceAccountId(),customerId,r.currencyCode(),true);
        if(source.getAvailableBalance().compareTo(r.amount())<0)
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INSUFFICIENT_FUNDS","Funding account has insufficient available balance");
        BigDecimal before=source.getAvailableBalance();String reservationId=UUID.randomUUID().toString();
        source.reserve(r.amount(),reservationId);
        FundReservation reservation=new FundReservation(reservationId,r.paymentId(),PaymentOperationType.FIXED_DEPOSIT_FUNDING,
                r.sourceAccountId(),fd.getAccount().getId(),null,customerId,r.amount(),r.currencyCode(),
                OffsetDateTime.ofInstant(r.expiresAt(),ZoneOffset.UTC));
        reservationRepository.save(reservation);
        transactionRepository.save(new DepositAccountTransaction(UUID.randomUUID().toString(),r.sourceAccountId(),r.paymentId(),reservationId,
                DepositTransactionType.PAYMENT_HOLD,PaymentOperationType.FIXED_DEPOSIT_FUNDING,r.amount(),r.currencyCode(),before,
                source.getAvailableBalance(),correlationId));
        auditRepository.save(new AuditLog(UUID.randomUUID().toString(),fd.getId(),"RESERVE_FD_FUNDING","SUCCESS",actor,"SERVICE",
                "FUNDS_RESERVED",null,Hashing.sha256(r.paymentId()),correlationId));
        return fundingReservationView(reservation);
    }

    @Transactional
    public FixedDepositFundingSettlementView settleFunding(String paymentId,
            FixedDepositFundingSettlementRequest r,String actor,String correlationId){
        FundReservation reservation=reservationRepository.findLockedByPaymentIdAndOperationType(paymentId,
                PaymentOperationType.FIXED_DEPOSIT_FUNDING).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,
                "PAYMENT_OPERATION_NOT_FOUND","Fixed-deposit funding reservation not found"));
        FixedDeposit fd=fdRepository.findByIdForUpdate(r.fixedDepositId()).orElseThrow();
        if(!reservation.getId().equals(r.reservationId())||!reservation.getTargetAccountId().equals(fd.getAccount().getId()))
            throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_RESERVATION_MISMATCH","Reservation does not match this fixed deposit");
        if(reservation.getStatus()==ReservationStatus.SETTLED)return fundingSettlementView(reservation,fd);
        if(reservation.getStatus()!=ReservationStatus.ACTIVE||!reservation.getExpiresAt().isAfter(OffsetDateTime.now()))
            throw new ApiException(HttpStatus.CONFLICT,"RESERVATION_NOT_ACTIVE","Funding reservation is not active");
        AccountBalance source=balanceRepository.findByAccountIdForUpdate(reservation.getSourceAccountId()).orElseThrow();
        AccountBalance target=balanceRepository.findByAccountIdForUpdate(reservation.getTargetAccountId()).orElseThrow();
        BigDecimal sourceBefore=source.getLedgerBalance(),targetBefore=target.getLedgerBalance();
        source.captureDebit(reservation.getAmount(),paymentId+":FD_SOURCE");target.creditLedgerOnly(reservation.getAmount(),paymentId+":FD_TARGET");
        transactionRepository.save(new DepositAccountTransaction(UUID.randomUUID().toString(),source.getAccountId(),paymentId,reservation.getId(),
                DepositTransactionType.DEBIT,PaymentOperationType.FIXED_DEPOSIT_FUNDING,reservation.getAmount(),reservation.getCurrencyCode(),sourceBefore,source.getLedgerBalance(),correlationId));
        transactionRepository.save(new DepositAccountTransaction(UUID.randomUUID().toString(),target.getAccountId(),paymentId,reservation.getId(),
                DepositTransactionType.CREDIT,PaymentOperationType.FIXED_DEPOSIT_FUNDING,reservation.getAmount(),reservation.getCurrencyCode(),targetBefore,target.getLedgerBalance(),correlationId));
        reservation.transitionTo(ReservationStatus.SETTLED);fd.setStatus(FixedDepositStatus.ACTIVE);fd.setUpdatedAt(OffsetDateTime.now());
        DepositAccount account=fd.getAccount();account.setStatus(AccountStatus.ACTIVE);account.setOpenedAt(OffsetDateTime.now());
        account.setUpdatedAt(OffsetDateTime.now());account.setUpdatedBy(actor);OffsetDateTime openedAt=account.getOpenedAt();
        accountingLifecycle.publishOpening(new AccountingLifecycleGateway.AccountOpenedEvent("DEPOSIT-OPEN:"+account.getId(),"DEPOSIT_ACCOUNT_OPENED",
                "DEPOSIT_ACCOUNT",account.getId(),account.getProductId(),fd.getCurrencyCode(),openedAt.toLocalDate(),openedAt),
                "DEPOSIT-OPEN:"+account.getId(),correlationId);
        historyRepository.save(new AccountStatusHistory(UUID.randomUUID().toString(),account.getId(),AccountStatus.PENDING_ACTIVATION,
                AccountStatus.ACTIVE,"FD_FUNDED",r.journalNumber(),actor,"SERVICE",correlationId));
        auditRepository.save(new AuditLog(UUID.randomUUID().toString(),fd.getId(),"SETTLE_FD_FUNDING","SUCCESS",actor,"SERVICE",
                "FD_FUNDED",r.journalNumber(),Hashing.sha256(paymentId),correlationId));
        String customerId=account.getHolders().stream().filter(holder->holder.getRole()==HolderRole.PRIMARY).findFirst().orElseThrow().getCustomerId();
        notificationOutbox.enqueue(customerId,"DEPOSIT_ACCOUNT_CREATED",account.getId(),"deposit-account-"+account.getId()+"-created",
                Map.of("accountType","Fixed Deposit","accountId",account.getId()));
        return fundingSettlementView(reservation,fd);
    }

    @Transactional
    public FixedDepositFundingReservationView releaseFunding(String reservationId,ReleaseReservationRequest r,
                                                               String actor,String correlationId){
        FundReservation reservation=reservationRepository.findLockedById(reservationId).orElseThrow(()->
                new ApiException(HttpStatus.NOT_FOUND,"RESERVATION_NOT_FOUND","Reservation not found"));
        if(reservation.getOperationType()!=PaymentOperationType.FIXED_DEPOSIT_FUNDING||!reservation.getPaymentId().equals(r.paymentId()))
            throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_RESERVATION_MISMATCH","Reservation is not owned by this funding payment");
        if(reservation.getStatus()==ReservationStatus.ACTIVE){AccountBalance source=balanceRepository.findByAccountIdForUpdate(reservation.getSourceAccountId()).orElseThrow();
            BigDecimal before=source.getAvailableBalance();source.release(reservation.getAmount(),r.paymentId()+":FD_RELEASE");
            transactionRepository.save(new DepositAccountTransaction(UUID.randomUUID().toString(),source.getAccountId(),r.paymentId(),reservationId,
                    DepositTransactionType.HOLD_RELEASE,PaymentOperationType.FIXED_DEPOSIT_FUNDING,reservation.getAmount(),reservation.getCurrencyCode(),before,source.getAvailableBalance(),correlationId));
            reservation.transitionTo(ReservationStatus.RELEASED);FixedDeposit fd=fdRepository.findByAccountIdForUpdate(reservation.getTargetAccountId()).orElseThrow();
            fd.setStatus(FixedDepositStatus.FUNDING_FAILED);fd.setUpdatedAt(OffsetDateTime.now());
            auditRepository.save(new AuditLog(UUID.randomUUID().toString(),fd.getId(),"RELEASE_FD_FUNDING","SUCCESS",actor,"SERVICE",
                    r.reasonCode(),null,Hashing.sha256(r.paymentId()),correlationId));}
        return fundingReservationView(reservation);
    }

    private FixedDepositFundingReservationView fundingReservationView(FundReservation r){return new FixedDepositFundingReservationView(
            r.getId(),r.getPaymentId(),r.getOperationType().name(),r.getStatus().name(),r.getSourceAccountId(),r.getTargetAccountId(),
            fixedDepositIdFor(r),r.getAmount(),r.getCurrencyCode(),r.getExpiresAt().toInstant());}
    private String fixedDepositIdFor(FundReservation reservation){
        if(reservation.getExternalTargetId()!=null)return reservation.getExternalTargetId();
        return fdRepository.findByAccountIdForUpdate(reservation.getTargetAccountId()).map(FixedDeposit::getId).orElse(null);
    }
    private FixedDepositFundingSettlementView fundingSettlementView(FundReservation r,FixedDeposit fd){return new FixedDepositFundingSettlementView(
            r.getId(),r.getPaymentId(),r.getOperationType().name(),r.getStatus().name(),fd.getId(),fd.getStatus().name(),
            transactionRepository.findByPaymentIdOrderByCreatedAtAsc(r.getPaymentId()).stream().map(DepositAccountTransaction::getId).toList());}

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
                a.getHolders().stream().noneMatch(h->sameCustomer(h.getCustomerId(),customerId)&&h.getStatus()==RecordStatus.ACTIVE))
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,funding?"FUNDING_ACCOUNT_INELIGIBLE":"PAYOUT_ACCOUNT_INELIGIBLE","Linked account is not eligible");
        return balance;
    }
    private boolean sameCustomer(String stored,String requested){
        return Objects.equals(stored,requested)||Objects.equals(stripCifPrefix(stored),stripCifPrefix(requested));
    }
    private String stripCifPrefix(String value){return value!=null&&value.regionMatches(true,0,"CIF-",0,4)?value.substring(4):value;}
    private FixedDeposit load(String id){return fdRepository.findDetailedById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"FIXED_DEPOSIT_NOT_FOUND","Fixed deposit not found"));}
    private FixedDepositView view(FixedDeposit f){DepositAccount a=f.getAccount(); return new FixedDepositView(f.getId(),a.getId(),mask(a.getAccountNumber()),a.getProductId(),a.getProductVersion(),f.getStatus(),f.getPrincipal(),f.getCurrencyCode(),f.getBookedAnnualRate(),f.getValueDate(),f.getMaturityDate(),f.getExpectedInterest(),f.getExpectedMaturityAmount(),f.getAccruedInterest(),f.getFundingAccountId(),f.getPayoutAccountId(),f.getVersion());}
    private String mask(String n){return n.length()<4?"****":"****"+n.substring(n.length()-4);} private String blank(String v){return v==null||v.isBlank()?null:v;}
}
