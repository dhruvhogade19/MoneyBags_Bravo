define([
  'knockout',
  'services/api/gatewayApi',
  'services/auth/session',
  'viewModels/banker/support',
  'css!views/banker/banker'
], function (ko, gatewayApi, session, support) {
  'use strict';

  function ProductEditorViewModel(params) {
    var self = this;
    this.state = support.createState();
    this.accessDenied = ko.pureComputed(function () { return !support.isAdmin(session); });
    this.existing = ko.observable(null);
    this.lookupCode = ko.observable('');
    this.productCode = ko.observable('');
    this.productName = ko.observable('');
    this.description = ko.observable('');
    this.category = ko.observable('DEPOSIT');
    this.subtype = ko.observable('SAVINGS');
    this.currencyCode = ko.observable('INR');
    this.effectiveFrom = ko.observable(support.today());
    this.effectiveTo = ko.observable('');
    this.annualInterestRate = ko.observable('3.5');
    this.minimumOpeningBalance = ko.observable('1000');
    this.maximumBalance = ko.observable('10000000');
    this.minimumTenureMonths = ko.observable('1');
    this.maximumTenureMonths = ko.observable('120');
    this.minimumCreditLimit = ko.observable('25000');
    this.maximumCreditLimit = ko.observable('500000');
    this.minimumIncome = ko.observable('25000');
    this.interestFreeDays = ko.observable('45');
    this.paymentDueDays = ko.observable('20');
    this.minimumPaymentPercentage = ko.observable('5');
    this.minimumPaymentAmount = ko.observable('500');
    this.minimumAge = ko.observable('18');
    this.maximumAge = ko.observable('75');
    this.isEditing = ko.pureComputed(function () { return Boolean(self.existing()); });
    this.isCard = ko.pureComputed(function () { return self.category() === 'CREDIT_CARD'; });
    this.isFixedDeposit = ko.pureComputed(function () { return self.subtype() === 'FIXED_DEPOSIT'; });
    this.navigate = function (path) { return support.navigate(params, path); };

    this.category.subscribe(function (category) {
      if (category === 'CREDIT_CARD') self.subtype('CREDIT_CARD');
      else if (self.subtype() === 'CREDIT_CARD') self.subtype('SAVINGS');
    });

    function assign(product) {
      self.existing(product);
      self.lookupCode(product.productCode);
      self.productCode(product.productCode);
      self.productName(product.productName);
      self.description(product.description || '');
      self.category(product.category);
      self.subtype(product.subtype);
      self.currencyCode(product.currencyCode || 'INR');
      self.effectiveFrom(product.effectiveFrom || support.today());
      self.effectiveTo(product.effectiveTo || '');
      var interest = product.interestRule || {};
      self.annualInterestRate(interest.annualInterestRate === null || interest.annualInterestRate === undefined ? '' : interest.annualInterestRate);
      var amount = product.amountRule || {};
      self.minimumOpeningBalance(amount.minimumOpeningBalance === undefined || amount.minimumOpeningBalance === null ? '1000' : amount.minimumOpeningBalance);
      self.maximumBalance(amount.maximumBalance === undefined || amount.maximumBalance === null ? '10000000' : amount.maximumBalance);
      self.minimumTenureMonths(amount.minimumTenureMonths || '1');
      self.maximumTenureMonths(amount.maximumTenureMonths || '120');
      var card = product.creditCardRule || {};
      self.minimumCreditLimit(card.minimumCreditLimit || '25000');
      self.maximumCreditLimit(card.maximumCreditLimit || '500000');
      self.interestFreeDays(card.interestFreeDays || '45');
      self.paymentDueDays(card.paymentDueDays || '20');
      self.minimumPaymentPercentage(card.minimumPaymentPercentage || '5');
      self.minimumPaymentAmount(card.minimumPaymentAmount || '500');
      var eligibility = (product.eligibilityRules || [])[0] || {};
      self.minimumAge(eligibility.minimumAge || '18');
      self.maximumAge(eligibility.maximumAge || '75');
      self.minimumIncome(eligibility.minimumMonthlyIncome || '25000');
    }

    this.load = function () {
      var code = String(self.lookupCode() || '').trim().toUpperCase();
      if (!code) { self.state.error('Enter a product code.'); return Promise.resolve(); }
      return support.run(self.state, function () {
        return gatewayApi.getProduct(code).then(assign);
      }, 'Product loaded for editing.').catch(function () {});
    };

    this.reset = function () {
      self.existing(null);
      self.lookupCode(''); self.productCode(''); self.productName(''); self.description('');
      self.category('DEPOSIT'); self.subtype('SAVINGS'); self.currencyCode('INR');
      self.effectiveFrom(support.today()); self.effectiveTo(''); self.annualInterestRate('3.5');
      self.state.error(''); self.state.success('');
    };

    function interestRule(category, from) {
      return {
        annualInterestRate: support.number(self.annualInterestRate()),
        pricingMode: 'FIXED', benchmarkCode: null, productSpread: null,
        minimumRate: null, maximumRate: null, targetProfitPercentage: null,
        effectiveFrom: from, effectiveTo: self.effectiveTo() || null, policyVersion: 'V1',
        interestCalculationMethod: category === 'CREDIT_CARD' ? 'DAILY_BALANCE' : 'SIMPLE',
        interestCalculationFrequency: 'DAILY',
        interestPostingFrequency: category === 'CREDIT_CARD' ? 'MONTHLY' : (self.isFixedDeposit() ? 'AT_MATURITY' : 'MONTHLY'),
        compoundingFrequency: category === 'CREDIT_CARD' ? null : 'MONTHLY',
        dayCountConvention: 'ACTUAL_365', rateApplicationMethod: 'BOOKING_DATE',
        loanRepaymentFrequency: null, interestType: category === 'CREDIT_CARD' ? 'DEBIT' : 'CREDIT'
      };
    }

    function payload() {
      var old = self.existing() || {};
      var category = self.category();
      var subtype = category === 'CREDIT_CARD' ? 'CREDIT_CARD' : self.subtype();
      var from = self.effectiveFrom();
      var code = String(self.productCode() || '').trim().toUpperCase();
      var result = {
        productCode: code,
        productName: String(self.productName() || '').trim(),
        description: String(self.description() || '').trim() || null,
        category: category,
        subtype: subtype,
        currencyCode: String(self.currencyCode() || 'INR').toUpperCase(),
        effectiveFrom: from,
        effectiveTo: self.effectiveTo() || null,
        changedBy: support.actor(session),
        interestRule: Object.assign({}, old.interestRule || {}, interestRule(category, from)),
        amountRule: null,
        creditCardRule: null,
        fixedDepositRule: null,
        interestRateSlabs: [],
        accountClosureRule: null,
        prematureClosureRule: old.prematureClosureRule || null,
        renewalRule: old.renewalRule || null,
        fees: old.fees || [],
        eligibilityRules: old.eligibilityRules && old.eligibilityRules.length ? old.eligibilityRules : [{
          minimumAge: support.number(self.minimumAge(), 18), maximumAge: support.number(self.maximumAge(), 75),
          minimumMonthlyIncome: category === 'CREDIT_CARD' ? support.number(self.minimumIncome()) : null,
          customerType: 'INDIVIDUAL', customerCategory: category === 'DEPOSIT' ? 'ANY' : null,
          kycRequired: true, collateralRequired: false, active: true
        }],
        features: old.features || []
      };
      if (category === 'DEPOSIT') {
        var minimum = support.number(self.minimumOpeningBalance());
        result.amountRule = Object.assign({}, old.amountRule || {}, {
          minimumOpeningBalance: subtype === 'FIXED_DEPOSIT' ? null : minimum,
          minimumBalance: 0,
          maximumBalance: support.number(self.maximumBalance()),
          minimumAmount: subtype === 'FIXED_DEPOSIT' ? minimum : null,
          maximumAmount: subtype === 'FIXED_DEPOSIT' ? support.number(self.maximumBalance()) : null,
          minimumTenureMonths: subtype === 'FIXED_DEPOSIT' ? support.number(self.minimumTenureMonths(), 1) : null,
          maximumTenureMonths: subtype === 'FIXED_DEPOSIT' ? support.number(self.maximumTenureMonths(), 120) : null,
          overdraftAllowed: false, overdraftLimit: null
        });
        result.accountClosureRule = old.accountClosureRule || {
          closureAllowed: true, closureMode: 'STANDARD', minimumAccountAgeDays: 0, noticePeriodDays: 0,
          closureFeeApplicable: false, closureFeeCode: null, closureFeeWaiverAfterDays: 0,
          zeroBalanceRequired: true, activeReservationsAllowed: false, activeMandatesAllowed: false,
          negativeBalanceAllowed: false, pendingChargesMustBeSettled: true,
          allowedClosureChannels: ['BRANCH', 'ONLINE'], settlementMethods: ['ACCOUNT_TRANSFER'],
          approvalRequired: false, effectiveFrom: from, effectiveTo: null, policyVersion: 'V1'
        };
        if (subtype === 'FIXED_DEPOSIT') {
          result.fixedDepositRule = old.fixedDepositRule || {
            allowedTenureUnits: ['MONTH'], allowedInterestPayoutFrequencies: ['AT_MATURITY'],
            defaultInterestPayoutFrequency: 'AT_MATURITY', compoundingFrequency: 'MONTHLY',
            dayCountConvention: 'ACTUAL_365', minimumHoldingDays: 0, partialWithdrawalAllowed: false,
            autoRenewalAllowed: true, defaultMaturityInstruction: 'CREDIT_PAYOUT_ACCOUNT',
            maximumRenewalCount: 12, gracePeriodDays: 7
          };
          result.interestRateSlabs = old.interestRateSlabs && old.interestRateSlabs.length ? old.interestRateSlabs : [{
            slabCode: code + '-DEFAULT', minimumTenure: support.number(self.minimumTenureMonths(), 1),
            maximumTenure: support.number(self.maximumTenureMonths(), 120), tenureUnit: 'MONTH',
            minimumAmount: minimum, maximumAmount: support.number(self.maximumBalance()), customerCategory: 'ANY',
            annualInterestRate: support.number(self.annualInterestRate()), effectiveFrom: from,
            effectiveTo: self.effectiveTo() || null, active: true
          }];
        }
      } else {
        result.creditCardRule = Object.assign({}, old.creditCardRule || {}, {
          policyVersion: 'V1', effectiveFrom: from, effectiveTo: self.effectiveTo() || null,
          minimumCreditLimit: support.number(self.minimumCreditLimit()),
          maximumCreditLimit: support.number(self.maximumCreditLimit()),
          interestFreeDays: support.number(self.interestFreeDays(), 45),
          minimumPaymentPercentage: support.number(self.minimumPaymentPercentage(), 5),
          minimumPaymentAmount: support.number(self.minimumPaymentAmount(), 500),
          paymentDueDays: support.number(self.paymentDueDays(), 20),
          cashAdvanceAllowed: false, cashAdvanceLimitPercentage: null
        });
      }
      return result;
    }

    this.save = function () {
      var body = payload();
      if (!body.productCode || !body.productName || !body.effectiveFrom) {
        self.state.error('Product code, name, and effective date are required.');
        return Promise.resolve();
      }
      return support.run(self.state, function () {
        var request = self.isEditing() ? gatewayApi.updateProduct(body.productCode, body) : gatewayApi.createProduct(body);
        return request.then(function (saved) { assign(saved); return saved; });
      }, function (saved) { return 'Product ' + saved.productCode + ' saved as ' + saved.status + '.'; }).catch(function () {});
    };

    this.connected = function () {
      document.title = 'Product editor | MoneyBag';
      var remembered = '';
      try { remembered = window.sessionStorage.getItem('moneybags-admin-product-code') || ''; } catch (ignore) {}
      if (remembered && !self.accessDenied()) { self.lookupCode(remembered); self.load(); }
    };
  }

  return ProductEditorViewModel;
});
