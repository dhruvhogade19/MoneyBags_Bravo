package com.moneybags.creditcard.service;

import com.moneybags.creditcard.domain.CreditCardTypes.AccountStatus;
import com.moneybags.creditcard.domain.CreditCardTypes.ApplicationStatus;
import com.moneybags.creditcard.domain.CreditCardTypes.EligibilityStatus;
import com.moneybags.creditcard.domain.CreditCardTypes.HoldStatus;
import com.moneybags.creditcard.dto.CreditCardDtos.*;
import com.moneybags.creditcard.entity.CreditCardAccount;
import com.moneybags.creditcard.entity.CreditCardApplication;
import com.moneybags.creditcard.entity.CreditCardHold;
import com.moneybags.creditcard.entity.CreditCardBillingCharge;
import com.moneybags.creditcard.entity.CreditCardBillPayment;
import com.moneybags.creditcard.exception.ApiException;
import com.moneybags.creditcard.integration.CreditCardReferenceGateway;
import com.moneybags.creditcard.integration.AccountingLifecycleGateway;
import com.moneybags.creditcard.repository.CreditCardAccountRepository;
import com.moneybags.creditcard.repository.CreditCardApplicationRepository;
import com.moneybags.creditcard.repository.CreditCardHoldRepository;
import com.moneybags.creditcard.repository.CreditCardBillingChargeRepository;
import com.moneybags.creditcard.repository.CreditCardBillPaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class CreditCardService {
    private final CreditCardApplicationRepository applications;
    private final CreditCardAccountRepository accounts;
    private final CreditCardAccountService accountService;
    private final CreditCardHoldRepository holds;
    private final CreditCardBillingChargeRepository billingCharges;
    private final CreditCardBillPaymentRepository billPayments;
    private final CreditCardReferenceGateway references;
    private final AccountingLifecycleGateway accounting;

    public CreditCardService(CreditCardApplicationRepository applications, CreditCardAccountRepository accounts,
                             CreditCardAccountService accountService, CreditCardHoldRepository holds,
                             CreditCardBillingChargeRepository billingCharges, CreditCardBillPaymentRepository billPayments,
                             CreditCardReferenceGateway references, AccountingLifecycleGateway accounting) {
        this.applications = applications;
        this.accounts = accounts;
        this.accountService = accountService;
        this.holds = holds;
        this.billingCharges = billingCharges;
        this.billPayments = billPayments;
        this.references = references;
        this.accounting = accounting;
    }

    @Transactional
    public ApplicationResponse submit(ApplicationRequest request) {
        var cif = references.getCreditCardDetails(request.cifId());
        var validation = references.validateApplication(request.productCode(), request.requestedCreditLimit(), cif);
        var now = OffsetDateTime.now();
        var a = new CreditCardApplication();
        a.cifId = request.cifId();
        a.productCode = request.productCode();
        a.requestedCreditLimit = request.requestedCreditLimit();
        a.kycStatusSnapshot = cif.kycStatus();
        a.age = cif.age();
        a.salary = cif.salary();
        a.submittedAt = now;
        a.updatedAt = now;

        if (validation.eligible() && (validation.applicableInterestRule() == null
                || validation.applicableInterestRule().annualInterestRate() == null)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Eligible Product Master response is missing applicableInterestRule.annualInterestRate");
        }
        a.applicationStatus = ApplicationStatus.PENDING;
        a.eligibilityStatus = validation.eligible() ? EligibilityStatus.ELIGIBLE : EligibilityStatus.NOT_ELIGIBLE;
        a.approvedCreditLimit = null;
        a.purchaseInterestRateSnapshot = validation.applicableInterestRule() == null
                ? null
                : validation.applicableInterestRule().annualInterestRate();
        return application(applications.save(a));
    }

    @Transactional(readOnly = true)
    public ApplicationResponse application(Long id) {
        return application(findApplication(id));
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> applications(Long cifId) {
        return applications.findByCifIdOrderBySubmittedAtDesc(cifId).stream().map(this::application).toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> applications(ApplicationStatus status) {
        var values = status == null
                ? applications.findAllByOrderBySubmittedAtDesc()
                : applications.findByApplicationStatusOrderBySubmittedAtDesc(status);
        return values.stream().map(this::application).toList();
    }

    @Transactional
    public AccountResponse approve(Long id) {
        var a = findApplication(id);
        if (a.applicationStatus != ApplicationStatus.PENDING)
            throw conflict("Only pending applications can be approved");
        if (a.eligibilityStatus != EligibilityStatus.ELIGIBLE) throw conflict("Ineligible application cannot be approved");
        a.applicationStatus = ApplicationStatus.APPROVED;
        a.approvedCreditLimit = a.requestedCreditLimit;
        a.updatedAt = OffsetDateTime.now();
        return account(accountService.createForApplication(a));
    }

    @Transactional
    public ApplicationResponse reject(Long id) {
        var a = findApplication(id);
        if (a.applicationStatus != ApplicationStatus.PENDING)
            throw conflict("Only pending applications can be rejected");
        a.applicationStatus = ApplicationStatus.REJECTED;
        a.updatedAt = OffsetDateTime.now();
        return application(a);
    }

    @Transactional
    public AccountResponse open(AccountCreateRequest r) {
        var app = findApplication(r.applicationId());
        if (app.applicationStatus != ApplicationStatus.APPROVED || app.approvedCreditLimit == null)
            throw conflict("An approved application is required");
        return account(accountService.createForApplication(app));
    }

    @Transactional(readOnly = true)
    public AccountResponse account(Long id) {
        return account(findAccount(id));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> accounts(Long cif) {
        return accounts.findByCifIdOrderByOpenedAtDesc(cif).stream().map(this::account).toList();
    }

    @Transactional(readOnly = true)
    public LimitResponse limit(Long id) {
        var a = findAccount(id);
        return new LimitResponse(a.id, a.availableLimit);
    }

    @Transactional(readOnly = true)
    public InterestRateResponse interest(Long id) {
        var a = findAccount(id);
        return new InterestRateResponse(a.id, a.purchaseInterestRateSnapshot);
    }

    @Transactional(readOnly = true)
    public BillingAccountDetails billingDetails(Long id) {
        var account = findAccount(id);
        return new BillingAccountDetails(account.id, account.cifId, account.productCode,
                account.sanctionedLimit, account.availableLimit, account.purchaseInterestRateSnapshot,
                account.outstandingAmount, account.status, account.openedAt);
    }

    @Transactional(readOnly = true)
    public StatementAccountContext statementContext(Long id) {
        var account = findAccount(id);
        return new StatementAccountContext("CC-" + account.id, maskCardNumber(account.cardNumber),
                "CREDIT_CARD", "INR", List.of(account.cifId.toString()));
    }

    @Transactional(readOnly = true)
    public CreditCardStatementSource statementActivity(Long id, LocalDate from, LocalDate to) {
        var account = findAccount(id);
        List<StatementActivity> all = new ArrayList<>();
        holds.findByAccountId(id).stream().filter(hold -> hold.status == HoldStatus.CAPTURED)
                .forEach(hold -> all.add(new StatementActivity("HOLD-" + hold.id, hold.referenceId,
                        "DEBIT", hold.amount, "INR", hold.createdAt)));
        billingCharges.findByAccountId(id).forEach(charge -> all.add(new StatementActivity(
                "BILL-" + charge.billId, charge.billId, "DEBIT", charge.amount,
                charge.currency, charge.appliedAt)));
        billPayments.findByAccountId(id).forEach(payment -> all.add(new StatementActivity(
                "PAY-" + payment.paymentId, payment.paymentId, "CREDIT", payment.amount,
                "INR", payment.appliedAt)));

        BigDecimal changesAfterPeriod = all.stream().filter(activity -> activity.occurredAt()
                        .toLocalDate().isAfter(to))
                .map(this::outstandingChange).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal closing = account.outstandingAmount.subtract(changesAfterPeriod);
        List<StatementActivity> selected = all.stream().filter(activity -> !activity.occurredAt()
                        .toLocalDate().isBefore(from) && !activity.occurredAt().toLocalDate().isAfter(to))
                .sorted(java.util.Comparator.comparing(StatementActivity::occurredAt)
                        .thenComparing(StatementActivity::transactionId)).toList();
        BigDecimal periodChanges = selected.stream().map(this::outstandingChange)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CreditCardStatementSource(selected, closing.subtract(periodChanges), closing, "INR");
    }

    @Transactional
    public BillingChargeResponse applyBillingCharges(Long accountId, String idempotencyKey,
                                                      BillingChargeRequest request) {
        if (!request.billId().equals(idempotencyKey)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key must match billId");
        }
        var existing = billingCharges.findByBillId(request.billId());
        if (existing.isPresent()) {
            var charge = existing.get();
            if (!charge.accountId.equals(accountId) || charge.amount.compareTo(request.amount()) != 0
                    || !charge.journalNumber.equals(request.journalNumber())
                    || !charge.currency.equals(request.currency())) {
                throw conflict("Bill charges were already applied with different values");
            }
            return billingCharge(charge, findAccount(accountId));
        }
        var account = lockAccount(accountId);
        if (account.status != AccountStatus.ACTIVE) throw conflict("Card account is not active");
        BigDecimal amount = request.amount().setScale(2, java.math.RoundingMode.HALF_UP);
        account.outstandingAmount = account.outstandingAmount.add(amount);
        account.availableLimit = account.availableLimit.subtract(amount);
        var charge = new CreditCardBillingCharge();
        charge.accountId = accountId;
        charge.billId = request.billId();
        charge.journalNumber = request.journalNumber();
        charge.amount = amount;
        charge.currency = request.currency();
        charge.appliedAt = OffsetDateTime.now(ZoneOffset.UTC);
        billingCharges.save(charge);
        return billingCharge(charge, account);
    }

    @Transactional
    public HoldResponse createHold(Long accountId, HoldRequest request) {
        var account = lockAccount(accountId);
        if (account.status != AccountStatus.ACTIVE) throw conflict("Card account is not active");

        var existing = holds.findByReferenceId(request.referenceId());
        if (existing.isPresent()) {
            var hold = existing.get();
            if (!hold.accountId.equals(accountId)) throw conflict("Reference ID belongs to a different account");
            return hold(hold);
        }
        if (account.availableLimit.compareTo(request.amount()) < 0) {
            throw conflict("Insufficient available credit limit");
        }

        account.availableLimit = account.availableLimit.subtract(request.amount());
        var hold = new CreditCardHold();
        hold.accountId = accountId;
        hold.referenceId = request.referenceId();
        hold.amount = request.amount();
        hold.status = HoldStatus.HELD;
        hold.createdAt = OffsetDateTime.now();
        return hold(holds.save(hold));
    }

    @Transactional
    public HoldResponse captureHold(Long accountId, Long holdId) {
        var account = lockAccount(accountId);
        var hold = findHoldForAccount(holdId, account.id);
        if (hold.status == HoldStatus.CAPTURED) return hold(hold);
        if (hold.status == HoldStatus.RELEASED) throw conflict("Released hold cannot be captured");

        hold.status = HoldStatus.CAPTURED;
        account.outstandingAmount = account.outstandingAmount.add(hold.amount);
        return hold(hold);
    }

    @Transactional
    public HoldResponse releaseHold(Long accountId, Long holdId) {
        var account = lockAccount(accountId);
        var hold = findHoldForAccount(holdId, account.id);
        if (hold.status == HoldStatus.RELEASED) return hold(hold);
        if (hold.status == HoldStatus.CAPTURED) throw conflict("Captured hold cannot be released");

        hold.status = HoldStatus.RELEASED;
        account.availableLimit = account.availableLimit.add(hold.amount);
        return hold(hold);
    }

    @Transactional
    public AccountResponse billPaid(Long id, AmountRequest r) {
        var a = lockAccount(id);
        if (r.amount() == null || r.amount().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payment amount must be positive");
        }
        var amountApplied = r.amount().min(a.outstandingAmount);
        a.outstandingAmount = a.outstandingAmount.subtract(amountApplied);
        a.availableLimit = a.availableLimit.add(amountApplied);
        return account(a);
    }

    @Transactional
    public AccountResponse billPaid(Long id, BillPaymentRequest request) {
        var account = lockAccount(id);
        var existing = billPayments.findById(request.paymentId());
        if (existing.isPresent()) {
            var applied = existing.get();
            if (!applied.accountId.equals(id) || applied.amount.compareTo(request.amount()) != 0) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Payment identifier was already used for a different card repayment");
            }
            return account(account);
        }
        if (request.amount().compareTo(account.outstandingAmount) > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Repayment exceeds the current card outstanding amount");
        }
        account.outstandingAmount = account.outstandingAmount.subtract(request.amount());
        account.availableLimit = account.availableLimit.add(request.amount());
        billPayments.save(new CreditCardBillPayment(request.paymentId(), id, request.amount()));
        return account(account);
    }

    @Transactional
    public AccountResponse close(Long id) {
        var account = lockAccount(id);
        if (account.status == AccountStatus.CLOSED) throw conflict("Credit-card account is already closed");
        if (account.status != AccountStatus.ACTIVE && account.status != AccountStatus.BLOCKED
                && account.status != AccountStatus.CLOSURE_PENDING) {
            throw conflict("Credit-card account cannot be closed in its current status");
        }

        account.status = AccountStatus.CLOSURE_PENDING;
        String accountReference = "CC-" + account.id;
        var clearance = accounting.clearance(accountReference);
        if (clearance == null || !clearance.accountingCleared()) return account(account);

        var event = new AccountingLifecycleGateway.AccountClosedEvent(
                "CARD-CLOSE:" + accountReference, "CREDIT_CARD_ACCOUNT_CLOSED", "CREDIT_CARD_ACCOUNT",
                accountReference, "INR", LocalDate.now(), OffsetDateTime.now(ZoneOffset.UTC), "CUSTOMER_REQUEST");
        var response = accounting.publishClosure(event);
        if (response != null && "CLOSED".equals(response.accountingLifecycleState())) {
            account.status = AccountStatus.CLOSED;
        }
        return account(account);
    }

    @Transactional(readOnly = true)
    public EodReadinessResponse eod() {
        List<String> b = new ArrayList<>();
        long approvedWithoutAccounts = applications.findAll().stream().filter(a -> a.applicationStatus == ApplicationStatus.APPROVED && !accounts.existsByApplicationId(a.id)).count();
        if (approvedWithoutAccounts > 0)
            b.add(approvedWithoutAccounts + " approved applications do not have corresponding accounts");
        for (var a : accounts.findAll()) {
            if (a.status != AccountStatus.ACTIVE && a.status != AccountStatus.BLOCKED
                    && a.status != AccountStatus.CLOSURE_PENDING && a.status != AccountStatus.CLOSED)
                b.add("Account " + a.id + " has an invalid status");
            if (a.availableLimit.signum() < 0 || a.outstandingAmount.signum() < 0 || a.availableLimit.compareTo(a.sanctionedLimit) > 0)
                b.add("Account " + a.id + " has inconsistent credit state");
        }
        return new EodReadinessResponse(b.isEmpty(), accounts.countByStatus(AccountStatus.ACTIVE), accounts.countByStatus(AccountStatus.BLOCKED), applications.countByApplicationStatus(ApplicationStatus.PENDING), b);
    }

    private CreditCardApplication findApplication(Long id) {
        return applications.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Credit-card application not found"));
    }

    private CreditCardAccount findAccount(Long id) {
        return accounts.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Credit-card account not found"));
    }

    private CreditCardAccount lockAccount(Long id) {
        return accounts.lockById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Credit-card account not found"));
    }

    private CreditCardHold findHoldForAccount(Long holdId, Long accountId) {
        var hold = holds.findById(holdId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Credit-card hold not found"));
        if (!hold.accountId.equals(accountId)) throw new ApiException(HttpStatus.NOT_FOUND, "Credit-card hold not found");
        return hold;
    }

    private ApiException conflict(String m) {
        return new ApiException(HttpStatus.CONFLICT, m);
    }

    private ApplicationResponse application(CreditCardApplication a) {
        return new ApplicationResponse(a.id, a.cifId, a.productCode, a.requestedCreditLimit, a.approvedCreditLimit, a.purchaseInterestRateSnapshot, a.applicationStatus, a.kycStatusSnapshot, a.age, a.salary, a.eligibilityStatus, a.submittedAt, a.updatedAt);
    }

    private AccountResponse account(CreditCardAccount a) {
        return new AccountResponse(a.id, a.applicationId, a.cifId, a.productCode, a.age, a.salary, a.cardNumber, a.sanctionedLimit, a.purchaseInterestRateSnapshot, a.availableLimit, a.outstandingAmount, a.status, a.openedAt);
    }

    private HoldResponse hold(CreditCardHold hold) {
        return new HoldResponse(hold.id, hold.accountId, hold.referenceId, hold.amount, hold.status, hold.createdAt);
    }

    private BillingChargeResponse billingCharge(CreditCardBillingCharge charge, CreditCardAccount account) {
        return new BillingChargeResponse(charge.billId, account.id, charge.journalNumber, charge.amount,
                account.outstandingAmount, account.availableLimit, charge.appliedAt);
    }

    private BigDecimal outstandingChange(StatementActivity activity) {
        return "DEBIT".equals(activity.direction()) ? activity.amount() : activity.amount().negate();
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) return "XXXXXXXXXXXX";
        return "XXXXXXXXXXXX" + cardNumber.substring(cardNumber.length() - 4);
    }
}
