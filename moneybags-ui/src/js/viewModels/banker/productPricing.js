define([
  'knockout',
  'services/api/gatewayApi',
  'services/auth/session',
  'viewModels/banker/support',
  'css!views/banker/banker'
], function (ko, gatewayApi, session, support) {
  'use strict';

  function ProductPricingViewModel(params) {
    var self = this;
    this.state = support.createState();
    this.policyState = support.createState();
    this.accessDenied = ko.pureComputed(function () { return !support.isAdmin(session); });
    this.productCode = ko.observable('');
    this.product = ko.observable(null);
    this.policies = ko.observableArray([]);
    this.quoteDate = ko.observable(support.today());
    this.principal = ko.observable('100000');
    this.tenureMonths = ko.observable('12');
    this.quote = ko.observable(null);
    this.pricingMode = ko.observable('FIXED');
    this.annualRate = ko.observable('');
    this.benchmarkCode = ko.observable('');
    this.productSpread = ko.observable('');
    this.policyEffectiveFrom = ko.observable(support.today());
    this.money = support.money;
    this.navigate = function (path) { return support.navigate(params, path); };

    function applyPolicy(policy) {
      policy = policy || {};
      self.pricingMode(policy.pricingMode || 'FIXED');
      self.annualRate(policy.annualInterestRate === undefined || policy.annualInterestRate === null ? '' : policy.annualInterestRate);
      self.benchmarkCode(policy.benchmarkCode || '');
      self.productSpread(policy.productSpread === undefined || policy.productSpread === null ? '' : policy.productSpread);
      self.policyEffectiveFrom(policy.effectiveFrom || support.today());
    }

    this.load = function () {
      var code = String(self.productCode() || '').trim().toUpperCase();
      if (!code) { self.state.error('Enter a product code.'); return Promise.resolve(); }
      try { window.sessionStorage.setItem('moneybags-admin-product-code', code); } catch (ignore) {}
      return support.run(self.state, function () {
        return Promise.all([gatewayApi.getProduct(code), gatewayApi.listInterestPolicies(code)]).then(function (results) {
          self.product(results[0]);
          self.policies(support.content(results[1]));
          applyPolicy(results[0].interestRule || self.policies()[0]);
        });
      }, 'Pricing policy loaded.').catch(function () {});
    };

    this.getQuote = function () {
      var code = String(self.productCode() || '').trim().toUpperCase();
      if (!code) { self.state.error('Load a product before requesting a quote.'); return Promise.resolve(); }
      return support.run(self.state, function () {
        return gatewayApi.getRateQuote(code, {
          quoteDate: self.quoteDate(),
          principal: self.principal() === '' ? null : support.number(self.principal()),
          tenureMonths: self.tenureMonths() === '' ? null : support.number(self.tenureMonths())
        }).then(function (quote) { self.quote(quote); });
      }, 'Rate quote calculated.').catch(function () {});
    };

    this.savePolicy = function () {
      var product = self.product();
      if (!product) { self.policyState.error('Load a product first.'); return Promise.resolve(); }
      if (self.pricingMode() === 'BENCHMARK_PLUS_SPREAD' && (!String(self.benchmarkCode() || '').trim() || self.productSpread() === '')) {
        self.policyState.error('Benchmark code and product spread are required for benchmark pricing.');
        return Promise.resolve();
      }
      var base = Object.assign({}, product.interestRule || {});
      base.annualInterestRate = self.annualRate() === '' ? null : support.number(self.annualRate());
      base.pricingMode = self.pricingMode();
      base.benchmarkCode = self.pricingMode() === 'BENCHMARK_PLUS_SPREAD' ? String(self.benchmarkCode()).trim().toUpperCase() : null;
      base.productSpread = self.pricingMode() === 'BENCHMARK_PLUS_SPREAD' ? support.number(self.productSpread()) : null;
      base.effectiveFrom = self.policyEffectiveFrom();
      base.effectiveTo = null;
      base.policyVersion = 'V1';
      base.interestCalculationMethod = base.interestCalculationMethod || (product.category === 'CREDIT_CARD' ? 'DAILY_BALANCE' : 'SIMPLE');
      base.interestCalculationFrequency = product.category === 'CREDIT_CARD' ? 'DAILY' : (base.interestCalculationFrequency || 'DAILY');
      base.interestPostingFrequency = product.category === 'CREDIT_CARD' ? 'MONTHLY' : (base.interestPostingFrequency || 'MONTHLY');
      base.compoundingFrequency = product.category === 'CREDIT_CARD' ? null : (base.compoundingFrequency || 'MONTHLY');
      base.dayCountConvention = base.dayCountConvention || 'ACTUAL_365';
      base.rateApplicationMethod = base.rateApplicationMethod || 'BOOKING_DATE';
      base.interestType = product.category === 'CREDIT_CARD' ? 'DEBIT' : 'CREDIT';
      return support.run(self.policyState, function () {
        return gatewayApi.addInterestPolicy(product.productCode, base).then(function () { return self.load(); });
      }, 'Interest policy saved.').catch(function () {});
    };

    this.connected = function () {
      document.title = 'Product pricing | MoneyBag';
      var remembered = '';
      try { remembered = window.sessionStorage.getItem('moneybags-admin-product-code') || ''; } catch (ignore) {}
      if (remembered && !self.accessDenied()) { self.productCode(remembered); self.load(); }
    };
  }

  return ProductPricingViewModel;
});
