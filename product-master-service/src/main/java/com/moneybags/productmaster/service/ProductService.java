package com.moneybags.productmaster.service;

import com.moneybags.productmaster.api.ProductDtos.*;
import com.moneybags.productmaster.domain.Enums.*;
import com.moneybags.productmaster.entity.*;
import com.moneybags.productmaster.exception.ProductExceptions.BusinessValidationException;
import com.moneybags.productmaster.exception.ProductExceptions.ProductNotFoundException;
import com.moneybags.productmaster.repository.CreditCardProductRepository;
import com.moneybags.productmaster.repository.DepositProductRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductService {
    private final DepositProductRepository deposits;
    private final CreditCardProductRepository cards;
    private final ProductMapper mapper;

    public ProductService(DepositProductRepository deposits, CreditCardProductRepository cards, ProductMapper mapper) {
        this.deposits = deposits; this.cards = cards; this.mapper = mapper;
    }

    public ProductResponse create(ProductRequest request) {
        ensureCodeAvailable(request.productCode());
        if (request.category() == Category.DEPOSIT) {
            DepositProduct product = new DepositProduct(); mapper.apply(product, request, true); product.setStatus(Status.DRAFT);
            validate(product, false); return mapper.response(deposits.save(product));
        }
        if (request.category() == Category.CREDIT_CARD) {
            CreditCardProduct product = new CreditCardProduct(); mapper.apply(product, request, true); product.setStatus(Status.DRAFT);
            validate(product, false); return mapper.response(cards.save(product));
        }
        throw new BusinessValidationException(List.of("Unsupported product category"));
    }

    @Transactional(readOnly = true) public ProductResponse get(String productCode) { return response(find(productCode)); }

    public ProductResponse update(String productCode, ProductRequest request) {
        CatalogProduct located = find(productCode);
        if (!located.productCode().equalsIgnoreCase(request.productCode())) fail("productCode in the body must match the path and cannot be changed");
        if (located.category() != request.category()) fail("Product category cannot be changed");
        if (located.deposit() != null) { mapper.apply(located.deposit(), request, false); validate(located.deposit(), located.deposit().getStatus() == Status.ACTIVE); return mapper.response(deposits.save(located.deposit())); }
        mapper.apply(located.card(), request, false); validate(located.card(), located.card().getStatus() == Status.ACTIVE); return mapper.response(cards.save(located.card()));
    }

    public ProductResponse changeStatus(String productCode, StatusRequest request) {
        CatalogProduct located = find(productCode);
        if (located.deposit() != null) { if (request.status() == Status.ACTIVE) validate(located.deposit(), true); located.deposit().setStatus(request.status()); located.deposit().setUpdatedBy(request.changedBy()); return mapper.response(deposits.save(located.deposit())); }
        if (request.status() == Status.ACTIVE) validate(located.card(), true); located.card().setStatus(request.status()); located.card().setUpdatedBy(request.changedBy()); return mapper.response(cards.save(located.card()));
    }

    public void discontinue(String productCode, String changedBy) {
        CatalogProduct located = find(productCode);
        if (located.deposit() != null) { located.deposit().setStatus(Status.DISCONTINUED); located.deposit().setUpdatedBy(changedBy); deposits.save(located.deposit()); }
        else { located.card().setStatus(Status.DISCONTINUED); located.card().setUpdatedBy(changedBy); cards.save(located.card()); }
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> findAll(Category category, Subtype subtype, Status status, String productName, LocalDate activeOn, Pageable pageable) {
        List<ProductResponse> values = new ArrayList<>();
        if (category == null || category == Category.DEPOSIT) deposits.findAll().stream().map(mapper::response).forEach(values::add);
        if (category == null || category == Category.CREDIT_CARD) cards.findAll().stream().map(mapper::response).forEach(values::add);
        List<ProductResponse> filtered = values.stream().filter(value -> subtype == null || value.subtype() == subtype)
                .filter(value -> status == null || value.status() == status)
                .filter(value -> productName == null || productName.isBlank() || value.productName().toLowerCase(Locale.ROOT).contains(productName.toLowerCase(Locale.ROOT)))
                .filter(value -> activeOn == null || effective(value, activeOn) && value.status() == Status.ACTIVE)
                .sorted(Comparator.comparing(ProductResponse::productCode)).toList();
        if (pageable.isUnpaged()) return new PageResponse<>(filtered, 0, filtered.size(), filtered.size(), filtered.isEmpty() ? 0 : 1, true, true);
        int size = pageable.getPageSize(), page = pageable.getPageNumber(), from = Math.min(page * size, filtered.size()), to = Math.min(from + size, filtered.size());
        int pages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / size);
        return new PageResponse<>(filtered.subList(from, to), page, size, filtered.size(), pages, page == 0, pages == 0 || page >= pages - 1);
    }

    @Transactional(readOnly = true) public List<ProductResponse> active(Category category) { return findAll(category, null, Status.ACTIVE, null, LocalDate.now(), Pageable.unpaged()).content(); }
    @Transactional(readOnly = true) public List<EligibilityRuleDto> eligibility(String productCode) { return get(productCode).eligibilityRules(); }
    @Transactional(readOnly = true) public ProductResponse pricing(String productCode) { return get(productCode); }

    @Transactional(readOnly = true)
    public ValidationResponse validateAccountOpening(String productCode, AccountOpeningValidationRequest request) {
        ProductResponse product = get(productCode); List<String> messages = baseValidation(product, Category.DEPOSIT); AmountRuleDto amount = product.amountRule();
        if (amount == null) messages.add("Product has no deposit amount rules");
        else if (product.subtype() == Subtype.FIXED_DEPOSIT) validateFixedDepositOpening(product, request, messages);
        else {
            compareAmount(request.openingAmount(), amount.minimumOpeningBalance(), "Opening amount is below the minimum opening balance", messages);
            if (amount.maximumBalance() != null && request.openingAmount().compareTo(amount.maximumBalance()) > 0) messages.add("Opening amount is above the maximum balance");
        }
        validateEligibility(product.eligibilityRules(), request.age(), null, request.customerType(), request.customerCategory(), request.kycCompleted(), messages);
        return new ValidationResponse(messages.isEmpty(), messages, activeFees(product.fees()), product.interestRule(), amount);
    }

    @Transactional(readOnly = true)
    public CreditCardValidationResponse validateCreditCardApplication(String productCode, CreditCardApplicationValidationRequest request) {
        ProductResponse product = get(productCode); List<String> messages = baseValidation(product, Category.CREDIT_CARD); CreditCardRuleDto rule = product.creditCardRule();
        if (rule == null) messages.add("Product has no credit-card rules"); else {
            compareAmount(request.requestedCreditLimit(), rule.minimumCreditLimit(), "Requested credit limit is below the product minimum", messages);
            if (request.requestedCreditLimit().compareTo(rule.maximumCreditLimit()) > 0) messages.add("Requested credit limit is above the product maximum");
        }
        validateEligibility(product.eligibilityRules(), request.age(), request.monthlyIncome(), request.customerType(), null, request.kycCompleted(), messages);
        return new CreditCardValidationResponse(messages.isEmpty(), messages, activeFees(product.fees()), product.interestRule(), rule);
    }

    @Transactional(readOnly = true)
    public List<MinimalCreditCardProductResponse> minimalCreditCards() {
        return active(Category.CREDIT_CARD).stream().map(this::minimalCreditCard).toList();
    }

    @Transactional(readOnly = true)
    public MinimalCreditCardProductResponse minimalCreditCard(String productCode) {
        ProductResponse product = get(productCode);
        if (product.category() != Category.CREDIT_CARD) {
            fail("The minimal product view is available only for credit-card products");
        }
        return minimalCreditCard(product);
    }

    private MinimalCreditCardProductResponse minimalCreditCard(ProductResponse product) {
        EligibilityRuleDto rule = product.eligibilityRules().stream().filter(EligibilityRuleDto::active).findFirst().orElse(null);
        List<String> messages = new ArrayList<>();
        if (rule != null && rule.kycRequired()) messages.add("KYC verification is required");
        if (product.creditCardRule() != null) {
            messages.add("Interest-free period: " + product.creditCardRule().interestFreeDays() + " days");
            messages.add("Minimum payment: " + product.creditCardRule().minimumPaymentPercentage() + "% or "
                    + product.currencyCode() + " " + product.creditCardRule().minimumPaymentAmount() + ", whichever is higher");
        }
        return new MinimalCreditCardProductResponse(product.productCode(), product.productName(),
                product.interestRule() == null ? null : product.interestRule().annualInterestRate(), rule, messages);
    }

    InterestRuleDto addInterestPolicy(String productCode, InterestRuleDto request) {
        CatalogProduct located = find(productCode);
        if (located.deposit() != null) { DepositInterestPolicy policy = located.deposit().getInterestPolicies().isEmpty() ? new DepositInterestPolicy() : located.deposit().getInterestPolicies().getFirst(); mapper.apply(policy, request, located.deposit().getEffectiveFrom()); if (policy.getDepositProduct() == null) { policy.setDepositProduct(located.deposit()); located.deposit().getInterestPolicies().add(policy); } validate(located.deposit(), located.deposit().getStatus() == Status.ACTIVE); deposits.save(located.deposit()); return mapper.interest(policy); }
        CreditCardInterestPolicy policy = located.card().getInterestPolicies().isEmpty() ? new CreditCardInterestPolicy() : located.card().getInterestPolicies().getFirst(); mapper.apply(policy, request, located.card().getEffectiveFrom()); if (policy.getCreditCardProduct() == null) { policy.setCreditCardProduct(located.card()); located.card().getInterestPolicies().add(policy); } validate(located.card(), located.card().getStatus() == Status.ACTIVE); cards.save(located.card()); return mapper.interest(policy);
    }
    @Transactional(readOnly = true) List<InterestRuleDto> interestPolicies(String productCode) { CatalogProduct value = find(productCode); return value.deposit() != null ? value.deposit().getInterestPolicies().stream().map(mapper::interest).toList() : value.card().getInterestPolicies().stream().map(mapper::interest).toList(); }
    CreditCardRuleDto addCreditCardTerms(String productCode, CreditCardRuleDto request) { CatalogProduct value = find(productCode); if (value.card() == null) fail("Credit-card policies are supported only for credit-card products"); CreditCardTerms terms = value.card().getTerms().isEmpty() ? new CreditCardTerms() : value.card().getTerms().getFirst(); mapper.apply(terms, request, value.card().getEffectiveFrom()); if (terms.getCreditCardProduct() == null) { terms.setCreditCardProduct(value.card()); value.card().getTerms().add(terms); } validate(value.card(), value.card().getStatus() == Status.ACTIVE); cards.save(value.card()); return mapper.card(terms); }
    @Transactional(readOnly = true) List<CreditCardRuleDto> creditCardTerms(String productCode) { CatalogProduct value = find(productCode); if (value.card() == null) fail("Credit-card policies are supported only for credit-card products"); return value.card().getTerms().stream().map(mapper::card).toList(); }

    CatalogProduct find(String productCode) {
        String normalized = productCode.toUpperCase(Locale.ROOT); Optional<DepositProduct> deposit = deposits.findByProductCode(normalized); if (deposit.isPresent()) return new CatalogProduct(deposit.get(), null);
        return cards.findByProductCode(normalized).map(card -> new CatalogProduct(null, card)).orElseThrow(() -> new ProductNotFoundException(productCode));
    }

    private void ensureCodeAvailable(String productCode) { String normalized = productCode.toUpperCase(Locale.ROOT); if (deposits.existsByProductCode(normalized) || cards.existsByProductCode(normalized)) fail("productCode already exists"); }
    private ProductResponse response(CatalogProduct value) { return value.deposit() == null ? mapper.response(value.card()) : mapper.response(value.deposit()); }
    private boolean effective(ProductResponse value, LocalDate on) { return !value.effectiveFrom().isAfter(on) && (value.effectiveTo() == null || !value.effectiveTo().isBefore(on)); }

    private void validate(DepositProduct product, boolean activate) {
        List<String> errors = new ArrayList<>(); if (!EnumSet.of(Subtype.SAVINGS, Subtype.CURRENT, Subtype.FIXED_DEPOSIT).contains(product.getSubtype())) errors.add("Deposit category requires SAVINGS, CURRENT, or FIXED_DEPOSIT subtype");
        validateDates(product.getEffectiveFrom(), product.getEffectiveTo(), "effectiveTo", errors); AmountRuleDto amount = mapper.amount(product.getAmountRule()); validateAmount(amount, errors); validateEligibilityRanges(product.getEligibilityRules(), errors); validateInterest(mapper.effective(product.getInterestPolicies(), product.getEffectiveFrom()), Category.DEPOSIT, errors);
        if (product.getSubtype() == Subtype.FIXED_DEPOSIT) { if (mapper.fixedDeposit(product.getFixedDepositRule()) == null) errors.add("A fixed-deposit product requires fixedDepositRule"); if (product.getInterestRateSlabs().isEmpty()) errors.add("A fixed-deposit product requires at least one interest-rate slab"); validateSlabs(product.getInterestRateSlabs(), errors); }
        if (activate) { if (mapper.effective(product.getInterestPolicies(), product.getEffectiveFrom()) == null) errors.add("An active product requires an interest rule"); if (product.getSubtype() == Subtype.FIXED_DEPOSIT && (amount == null || amount.minimumAmount() == null)) errors.add("An active fixed-deposit product requires minimumAmount"); if (product.getSubtype() != Subtype.FIXED_DEPOSIT && (amount == null || amount.minimumOpeningBalance() == null)) errors.add("An active savings/current product requires minimumOpeningBalance"); if (mapper.closure(product.getAccountClosureRule()) == null) errors.add("An active deposit product requires an account closure rule"); }
        throwIf(errors);
    }
    private void validate(CreditCardProduct product, boolean activate) {
        List<String> errors = new ArrayList<>(); validateDates(product.getEffectiveFrom(), product.getEffectiveTo(), "effectiveTo", errors); validateEligibilityRanges(product.getEligibilityRules(), errors); validateInterest(mapper.effective(product.getInterestPolicies(), product.getEffectiveFrom()), Category.CREDIT_CARD, errors); CreditCardRuleDto terms = mapper.card(mapper.effectiveTerms(product.getTerms(), product.getEffectiveFrom())); validateCard(terms, errors); if (activate && mapper.effective(product.getInterestPolicies(), product.getEffectiveFrom()) == null) errors.add("An active product requires an interest rule"); if (activate && terms == null) errors.add("An active credit-card product requires a credit-card rule"); throwIf(errors);
    }
    private void validateFixedDepositOpening(ProductResponse product, AccountOpeningValidationRequest request, List<String> messages) {
        AmountRuleDto amount = product.amountRule(); compareAmount(request.openingAmount(), amount.minimumAmount(), "Opening amount is below the minimum fixed-deposit amount", messages); if (amount.maximumAmount() != null && request.openingAmount().compareTo(amount.maximumAmount()) > 0) messages.add("Opening amount is above the maximum fixed-deposit amount");
        if (request.tenureMonths() == null) { messages.add("Fixed-deposit opening requires tenureMonths"); return; }
        String unit = request.tenureUnit() == null ? "MONTH" : request.tenureUnit(); boolean matches = product.interestRateSlabs().stream().anyMatch(s -> s.active() && unit.equals(s.tenureUnit()) && request.tenureMonths() >= s.minimumTenure() && request.tenureMonths() <= s.maximumTenure() && request.openingAmount().compareTo(s.minimumAmount()) >= 0 && (s.maximumAmount() == null || request.openingAmount().compareTo(s.maximumAmount()) <= 0) && (s.customerCategory() == null || s.customerCategory() == CustomerCategory.ANY || s.customerCategory() == request.customerCategory()) && effective(s.effectiveFrom(), s.effectiveTo())); if (!matches) messages.add("No active fixed-deposit rate slab matches the requested tenure, amount, and customer category");
        FixedDepositRuleDto fd = product.fixedDepositRule(); if (fd != null && !fd.allowedTenureUnits().contains(unit)) messages.add("Tenure unit is not supported by the fixed-deposit product"); if (fd != null && request.interestPayoutFrequency() != null && !fd.allowedInterestPayoutFrequencies().contains(request.interestPayoutFrequency())) messages.add("Interest payout frequency is not supported by the fixed-deposit product");
    }
    private boolean effective(LocalDate from, LocalDate to) { LocalDate today = LocalDate.now(); return !from.isAfter(today) && (to == null || !to.isBefore(today)); }
    private List<String> baseValidation(ProductResponse product, Category category) { List<String> messages = new ArrayList<>(); if (product.category() != category) messages.add("Product category is not " + category); if (product.status() != Status.ACTIVE) messages.add("Product is not active"); if (!effective(product, LocalDate.now())) messages.add("Product is not effective today"); return messages; }
    private void validateEligibility(List<EligibilityRuleDto> rules, Integer age, BigDecimal income, CustomerType type, CustomerCategory category, Boolean kyc, List<String> messages) { List<EligibilityRuleDto> matchesType = rules.stream().filter(EligibilityRuleDto::active).filter(rule -> (rule.customerType() == CustomerType.ANY || type == null || rule.customerType() == type) && (rule.customerCategory() == null || rule.customerCategory() == CustomerCategory.ANY || category == null || rule.customerCategory() == category)).toList(); if (matchesType.isEmpty()) { messages.add("No active eligibility rule matches the customer type"); return; } boolean matches = matchesType.stream().anyMatch(rule -> (rule.minimumAge() == null || age != null && age >= rule.minimumAge()) && (rule.maximumAge() == null || age != null && age <= rule.maximumAge()) && (rule.minimumMonthlyIncome() == null || income != null && income.compareTo(rule.minimumMonthlyIncome()) >= 0) && (!rule.kycRequired() || Boolean.TRUE.equals(kyc))); if (!matches) messages.add("Customer does not meet age, income, customer-type, customer-category, or KYC rules"); }
    private void validateAmount(AmountRuleDto amount, List<String> errors) { if (amount == null) return; compare(amount.minimumBalance(), amount.maximumBalance(), "minimumBalance must not exceed maximumBalance", errors); compare(amount.minimumAmount(), amount.maximumAmount(), "minimumAmount must not exceed maximumAmount", errors); compare(amount.minimumTenureMonths(), amount.maximumTenureMonths(), "minimumTenureMonths must not exceed maximumTenureMonths", errors); if (!amount.overdraftAllowed() && positive(amount.overdraftLimit())) errors.add("overdraftLimit must be zero or null when overdraft is not allowed"); }
    private void validateInterest(AbstractInterestPolicy policy, Category category, List<String> errors) { if (policy == null) return; validateDates(policy.getEffectiveFrom(), policy.getEffectiveTo(), "interest policy effectiveTo", errors); compare(policy.getMinimumRate(), policy.getMaximumRate(), "minimumRate must not exceed maximumRate", errors); if (policy.getPricingMode() == PricingMode.FIXED && policy.getAnnualInterestRate() == null) errors.add("FIXED pricing requires annualInterestRate"); if (policy.getPricingMode() == PricingMode.BENCHMARK_PLUS_SPREAD && (policy.getBenchmarkCode() == null || policy.getProductSpread() == null)) errors.add("BENCHMARK_PLUS_SPREAD requires benchmarkCode and productSpread"); if (category == Category.DEPOSIT && policy.getInterestType() != InterestType.CREDIT) errors.add("Deposit interest must use CREDIT interestType"); if (category == Category.CREDIT_CARD && (policy.getInterestType() != InterestType.DEBIT || policy.getInterestCalculationFrequency() != InterestFrequency.DAILY || policy.getInterestPostingFrequency() != InterestPostingFrequency.MONTHLY)) errors.add("Credit-card interest must be DEBIT with DAILY calculation and MONTHLY posting"); }
    private void validateCard(CreditCardRuleDto card, List<String> errors) { if (card == null) return; validateDates(card.effectiveFrom(), card.effectiveTo(), "credit-card policy effectiveTo", errors); compare(card.minimumCreditLimit(), card.maximumCreditLimit(), "minimumCreditLimit must not exceed maximumCreditLimit", errors); if (card.cashAdvanceAllowed() && card.cashAdvanceLimitPercentage() == null) errors.add("cashAdvanceLimitPercentage is required when cash advance is allowed"); if (!card.cashAdvanceAllowed() && card.cashAdvanceLimitPercentage() != null) errors.add("cashAdvanceLimitPercentage must be null when cash advance is not allowed"); }
    private void validateSlabs(List<CatalogRuleValues.FixedDepositRateSlab> slabs, List<String> errors) { Set<String> seen = new HashSet<>(); for (CatalogRuleValues.FixedDepositRateSlab slab : slabs) { compare(slab.getMinimumTenure(), slab.getMaximumTenure(), "fixed-deposit slab minimumTenure must not exceed maximumTenure", errors); compare(slab.getMinimumAmount(), slab.getMaximumAmount(), "fixed-deposit slab minimumAmount must not exceed maximumAmount", errors); validateDates(slab.getEffectiveFrom(), slab.getEffectiveTo(), "fixed-deposit slab effectiveTo", errors); if (!seen.add(slab.getSlabCode())) errors.add("fixed-deposit slabCode must be unique within a product"); } }
    private void validateEligibilityRanges(List<CatalogRuleValues.Eligibility> rules, List<String> errors) { rules.forEach(rule -> compare(rule.getMinimumAge(), rule.getMaximumAge(), "minimumAge must not exceed maximumAge", errors)); }
    private void validateDates(LocalDate from, LocalDate to, String name, List<String> errors) { if (from == null) errors.add(name + " requires effectiveFrom"); else if (to != null && !to.isAfter(from)) errors.add(name + " must be later than effectiveFrom"); }
    private List<FeeDto> activeFees(List<FeeDto> fees) { return fees.stream().filter(FeeDto::active).toList(); }
    private void compareAmount(BigDecimal requested, BigDecimal minimum, String message, List<String> messages) { if (minimum != null && requested.compareTo(minimum) < 0) messages.add(message); }
    private boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private <T extends Comparable<T>> void compare(T min, T max, String message, List<String> errors) { if (min != null && max != null && min.compareTo(max) > 0) errors.add(message); }
    private void throwIf(List<String> errors) { if (!errors.isEmpty()) throw new BusinessValidationException(errors); }
    private void fail(String message) { throw new BusinessValidationException(List.of(message)); }
    record CatalogProduct(DepositProduct deposit, CreditCardProduct card) { Category category() { return deposit == null ? Category.CREDIT_CARD : Category.DEPOSIT; } String productCode() { return deposit == null ? card.getProductCode() : deposit.getProductCode(); } }
}
