package com.moneybags.accounting.service;

import com.moneybags.accounting.api.AccountingDtos.*;
import com.moneybags.accounting.domain.DomainTypes.AccountType;
import com.moneybags.accounting.exception.ApiException;
import com.moneybags.accounting.service.RuleResolver.ResolvedRule;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Component
public class PostingLineFactory {
    static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);
    public record LineDraft(String glCode, String subledgerReference, String componentType, String ruleCode,
                            int ruleVersion, BigDecimal debit, BigDecimal credit, String narration) {}
    public record PostingPlan(String eventType, String currencyCode, LocalDate businessDate,
                              OffsetDateTime occurredAt, Map<AccountType, Set<String>> accountReferences,
                              List<LineDraft> lines) {}

    private final RuleResolver rules;
    public PostingLineFactory(RuleResolver rules) { this.rules = rules; }

    public PostingPlan payment(PaymentSettlementPostingRequest request) {
        String type = request.paymentType().toUpperCase(Locale.ROOT);
        String expectedSource;
        String expectedDestination;
        if ("BOOK_TRANSFER".equals(type)) {
            expectedSource = "DEPOSIT_ACCOUNT"; expectedDestination = "DEPOSIT_ACCOUNT";
        } else if ("CREDIT_CARD_REPAYMENT".equals(type)) {
            expectedSource = "DEPOSIT_ACCOUNT"; expectedDestination = "CREDIT_CARD_ACCOUNT";
        } else if ("CREDIT_CARD_MERCHANT_PAYMENT".equals(type)) {
            expectedSource = "CREDIT_CARD_ACCOUNT"; expectedDestination = "MERCHANT";
        } else {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_PAYMENT_TYPE",
                    "Unsupported paymentType: " + request.paymentType());
        }
        requireInstrument(request.source(), expectedSource, "source");
        requireInstrument(request.destination(), expectedDestination, "destination");
        String source = requireReference(request.source().accountId(), "source.accountId");
        String destination = "CREDIT_CARD_MERCHANT_PAYMENT".equals(type)
                ? requireReference(request.destination().merchantId(), "destination.merchantId")
                : requireReference(request.destination().accountId(), "destination.accountId");
        ResolvedRule rule = rules.resolve(type, "PRINCIPAL", null, request.currencyCode(), request.businessDate());
        BigDecimal amount = money(request.amount());
        List<LineDraft> lines = List.of(
                debit(rule.debitGlCode(), source, "PRINCIPAL", rule, amount, request.reference()),
                credit(rule.creditGlCode(), destination, "PRINCIPAL", rule, amount, request.reference()));
        Map<AccountType, Set<String>> references = new EnumMap<>(AccountType.class);
        if ("CREDIT_CARD_MERCHANT_PAYMENT".equals(type)) {
            references.computeIfAbsent(AccountType.CREDIT_CARD_ACCOUNT, ignored -> new TreeSet<>()).add(source);
        } else {
            references.computeIfAbsent(AccountType.DEPOSIT_ACCOUNT, ignored -> new TreeSet<>()).add(source);
            references.computeIfAbsent("BOOK_TRANSFER".equals(type) ? AccountType.DEPOSIT_ACCOUNT
                    : AccountType.CREDIT_CARD_ACCOUNT, ignored -> new TreeSet<>()).add(destination);
        }
        return new PostingPlan(type, request.currencyCode(), request.businessDate(), request.occurredAt(),
                references, lines);
    }

    public PostingPlan bill(BillAccountingPostingRequest request) {
        Map<AccountType, Set<String>> references = Map.of(AccountType.CREDIT_CARD_ACCOUNT, Set.of(request.accountId()));
        List<LineDraft> result = new ArrayList<>();
        for (BillComponent component : request.components()) {
            String type = component.componentType().toUpperCase(Locale.ROOT);
            ResolvedRule rule = rules.resolve("BILL_POSTING", type, request.productCode(), request.currencyCode(),
                    request.businessDate());
            BigDecimal amount = money(component.amount());
            result.add(debit(rule.debitGlCode(), request.accountId(), type, rule, amount, component.description()));
            result.add(credit(rule.creditGlCode(), null, type, rule, amount, component.description()));
        }
        return new PostingPlan("BILL_POSTING", request.currencyCode(), request.businessDate(), request.occurredAt(),
                references, result);
    }

    public PostingPlan fixedDeposit(FixedDepositPostingRequest request) {
        String type = request.postingType().toUpperCase(Locale.ROOT);
        Map<String, BigDecimal> components = componentMap(request.components());
        Map<AccountType, Set<String>> references = new EnumMap<>(AccountType.class);
        references.put(AccountType.DEPOSIT_ACCOUNT, new TreeSet<>(Set.of(request.fixedDepositAccountId())));
        List<LineDraft> result = switch (type) {
            case "FUNDING" -> funding(request, components, references);
            case "INTEREST_ACCRUAL" -> interestAccrual(request, components);
            case "INTEREST_PAYOUT" -> interestPayout(request, components, references);
            case "MATURITY_PAYOUT" -> maturity(request, components, references);
            case "PREMATURE_CLOSURE" -> prematureClosure(request, components, references);
            default -> throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_FD_POSTING_TYPE",
                    "Unsupported Fixed Deposit postingType: " + request.postingType());
        };
        return new PostingPlan("FD_" + type, request.currencyCode(), request.businessDate(), request.occurredAt(),
                references, result);
    }

    private List<LineDraft> funding(FixedDepositPostingRequest request, Map<String, BigDecimal> components,
                                    Map<AccountType, Set<String>> references) {
        requireReference(request.fundingAccountId(), "fundingAccountId");
        references.get(AccountType.DEPOSIT_ACCOUNT).add(request.fundingAccountId());
        BigDecimal amount = requiredPositive(components, "PRINCIPAL");
        ResolvedRule rule = rules.resolve("FD_FUNDING", "PRINCIPAL", request.productCode(), request.currencyCode(),
                request.businessDate());
        return List.of(debit(rule.debitGlCode(), request.fundingAccountId(), "PRINCIPAL", rule, amount,
                        request.narration()),
                credit(rule.creditGlCode(), request.fixedDepositAccountId(), "PRINCIPAL", rule, amount,
                        request.narration()));
    }

    private List<LineDraft> interestAccrual(FixedDepositPostingRequest request,
                                            Map<String, BigDecimal> components) {
        BigDecimal amount = requiredPositive(components, "INTEREST");
        ResolvedRule rule = rules.resolve("FD_INTEREST_ACCRUAL", "INTEREST", request.productCode(),
                request.currencyCode(), request.businessDate());
        return List.of(debit(rule.debitGlCode(), null, "INTEREST", rule, amount, request.narration()),
                credit(rule.creditGlCode(), request.fixedDepositAccountId(), "INTEREST", rule, amount,
                        request.narration()));
    }

    private List<LineDraft> interestPayout(FixedDepositPostingRequest request, Map<String, BigDecimal> components,
                                           Map<AccountType, Set<String>> references) {
        String mode = requireReference(request.payoutMode(), "payoutMode").toUpperCase(Locale.ROOT);
        if (!Set.of("TO_DEPOSIT_ACCOUNT", "CAPITALIZE").contains(mode)) throw new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_PAYOUT_MODE", "payoutMode must be TO_DEPOSIT_ACCOUNT or CAPITALIZE");
        if ("TO_DEPOSIT_ACCOUNT".equals(mode)) {
            requireReference(request.payoutAccountId(), "payoutAccountId");
            references.get(AccountType.DEPOSIT_ACCOUNT).add(request.payoutAccountId());
        }
        BigDecimal amount = requiredPositive(components, "INTEREST");
        ResolvedRule rule = rules.resolve("FD_INTEREST_PAYOUT", mode, request.productCode(), request.currencyCode(),
                request.businessDate());
        String destination = "CAPITALIZE".equals(mode) ? request.fixedDepositAccountId() : request.payoutAccountId();
        return List.of(debit(rule.debitGlCode(), request.fixedDepositAccountId(), "INTEREST", rule, amount,
                        request.narration()),
                credit(rule.creditGlCode(), destination, "INTEREST", rule, amount, request.narration()));
    }

    private List<LineDraft> maturity(FixedDepositPostingRequest request, Map<String, BigDecimal> components,
                                     Map<AccountType, Set<String>> references) {
        requireReference(request.payoutAccountId(), "payoutAccountId");
        references.get(AccountType.DEPOSIT_ACCOUNT).add(request.payoutAccountId());
        BigDecimal principal = requiredPositive(components, "PRINCIPAL");
        BigDecimal interest = nonNegative(components, "INTEREST");
        String principalGl = rules.resolveMapping("FD_PRINCIPAL", request.productCode(), request.currencyCode(),
                request.businessDate());
        String interestGl = rules.resolveMapping("FD_INTEREST", request.productCode(), request.currencyCode(),
                request.businessDate());
        String depositGl = rules.resolveMapping("CUSTOMER_DEPOSIT", request.productCode(), request.currencyCode(),
                request.businessDate());
        List<LineDraft> result = new ArrayList<>();
        result.add(rawDebit(principalGl, request.fixedDepositAccountId(), "PRINCIPAL", "FD_MATURITY_PAYOUT", principal,
                request.narration()));
        if (interest.signum() > 0) result.add(rawDebit(interestGl, request.fixedDepositAccountId(), "INTEREST",
                "FD_MATURITY_PAYOUT", interest, request.narration()));
        result.add(rawCredit(depositGl, request.payoutAccountId(), "NET_PAYOUT", "FD_MATURITY_PAYOUT",
                principal.add(interest), request.narration()));
        return result;
    }

    private List<LineDraft> prematureClosure(FixedDepositPostingRequest request,
                                             Map<String, BigDecimal> components,
                                             Map<AccountType, Set<String>> references) {
        requireReference(request.payoutAccountId(), "payoutAccountId");
        references.get(AccountType.DEPOSIT_ACCOUNT).add(request.payoutAccountId());
        BigDecimal principal = requiredPositive(components, "PRINCIPAL");
        BigDecimal eligibleInterest = nonNegative(components, "ELIGIBLE_INTEREST");
        BigDecimal adjustment = nonNegative(components, "INTEREST_ADJUSTMENT");
        BigDecimal penalty = nonNegative(components, "PENALTY");
        BigDecimal tax = nonNegative(components, "TAX");
        BigDecimal netPayout = requiredPositive(components, "NET_PAYOUT");
        BigDecimal expected = principal.add(eligibleInterest).subtract(penalty).subtract(tax);
        if (expected.compareTo(netPayout) != 0) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "FD_COMPONENT_TOTAL_MISMATCH", "NET_PAYOUT must equal principal + eligible interest - penalty - tax");
        String principalGl = rules.resolveMapping("FD_PRINCIPAL", request.productCode(), request.currencyCode(),
                request.businessDate());
        String interestGl = rules.resolveMapping("FD_INTEREST", request.productCode(), request.currencyCode(),
                request.businessDate());
        String depositGl = rules.resolveMapping("CUSTOMER_DEPOSIT", request.productCode(), request.currencyCode(),
                request.businessDate());
        String feeGl = rules.resolveMapping("FEE_INCOME", request.productCode(), request.currencyCode(),
                request.businessDate());
        String taxGl = rules.resolveMapping("TAX_PAYABLE", request.productCode(), request.currencyCode(),
                request.businessDate());
        String expenseGl = rules.resolveMapping("DEPOSIT_INTEREST_EXPENSE", request.productCode(),
                request.currencyCode(), request.businessDate());
        List<LineDraft> result = new ArrayList<>();
        result.add(rawDebit(principalGl, request.fixedDepositAccountId(), "PRINCIPAL", "FD_PREMATURE_CLOSURE",
                principal, request.narration()));
        BigDecimal payableDebit = eligibleInterest.add(adjustment);
        if (payableDebit.signum() > 0) result.add(rawDebit(interestGl, request.fixedDepositAccountId(),
                "INTEREST_SETTLEMENT", "FD_PREMATURE_CLOSURE", payableDebit, request.narration()));
        result.add(rawCredit(depositGl, request.payoutAccountId(), "NET_PAYOUT", "FD_PREMATURE_CLOSURE",
                netPayout, request.narration()));
        if (penalty.signum() > 0) result.add(rawCredit(feeGl, null, "PENALTY", "FD_PREMATURE_CLOSURE", penalty,
                request.narration()));
        if (tax.signum() > 0) result.add(rawCredit(taxGl, null, "TAX", "FD_PREMATURE_CLOSURE", tax,
                request.narration()));
        if (adjustment.signum() > 0) result.add(rawCredit(expenseGl, null, "INTEREST_ADJUSTMENT",
                "FD_PREMATURE_CLOSURE", adjustment, request.narration()));
        return result;
    }

    private Map<String, BigDecimal> componentMap(List<FixedDepositComponent> values) {
        Map<String, BigDecimal> result = new HashMap<>();
        values.forEach(value -> result.merge(value.componentType().toUpperCase(Locale.ROOT), money(value.amount()),
                BigDecimal::add));
        return result;
    }
    private BigDecimal requiredPositive(Map<String, BigDecimal> values, String name) {
        BigDecimal value = values.get(name);
        if (value == null || value.signum() <= 0) throw new ApiException(HttpStatus.BAD_REQUEST,
                "FD_COMPONENT_REQUIRED", name + " must be supplied with a positive amount");
        return value;
    }
    private BigDecimal nonNegative(Map<String, BigDecimal> values, String name) {
        BigDecimal value = values.getOrDefault(name, ZERO);
        if (value.signum() < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "NEGATIVE_COMPONENT_NOT_ALLOWED",
                name + " must not be negative");
        return value;
    }
    private String requireAccount(InstrumentReference value, String label) {
        return requireReference(value.reference(), label + ".accountId");
    }
    private void requireInstrument(InstrumentReference value, String expected, String label) {
        if (!expected.equalsIgnoreCase(value.instrumentType())) throw new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_INSTRUMENT_TYPE", label + ".instrumentType must be " + expected);
    }
    private String requireReference(String value, String field) {
        if (value == null || value.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST,
                "REQUIRED_FIELD_MISSING", field + " is required");
        return value;
    }
    static BigDecimal money(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_EVEN); }
    private LineDraft debit(String gl, String ref, String component, ResolvedRule rule, BigDecimal amount,
                            String narration) {
        return new LineDraft(gl, ref, component, rule.ruleCode(), rule.ruleVersion(), amount, ZERO, narration);
    }
    private LineDraft credit(String gl, String ref, String component, ResolvedRule rule, BigDecimal amount,
                             String narration) {
        return new LineDraft(gl, ref, component, rule.ruleCode(), rule.ruleVersion(), ZERO, amount, narration);
    }
    private LineDraft rawDebit(String gl, String ref, String component, String rule, BigDecimal amount,
                               String narration) {
        return new LineDraft(gl, ref, component, rule, 1, amount, ZERO, narration);
    }
    private LineDraft rawCredit(String gl, String ref, String component, String rule, BigDecimal amount,
                                String narration) {
        return new LineDraft(gl, ref, component, rule, 1, ZERO, amount, narration);
    }
}
