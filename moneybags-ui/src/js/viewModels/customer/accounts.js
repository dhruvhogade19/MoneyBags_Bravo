define([
  'knockout',
  'services/api/gatewayApi',
  'viewModels/customer/pageSupport',
  'ojs/ojbutton',
  'ojs/ojinputnumber',
  'ojs/ojinputtext',
  'ojs/ojprogress-circle'
], function (ko, gatewayApi, support) {
  'use strict';

  function AccountsViewModel(params) {
    var self = support.create(this, params, 'Deposit accounts');
    this.accounts = ko.observableArray([]);
    this.products = ko.observableArray([]);
    this.selectedAccount = ko.observable(null);
    this.mode = ko.observable('list');
    this.eligibility = ko.observable(null);
    this.selectedProductCode = ko.observable('');
    this.openingAmount = ko.observable(null);
    this.servicingBranchId = ko.observable('MB-DIGITAL');
    this.operatingInstruction = ko.observable('SINGLE');
    this.money = support.money; this.dateTime = support.dateTime; this.label = support.label; this.statusClass = support.statusClass;

    this.selectedProduct = ko.pureComputed(function () {
      return self.products().find(function (product) { return product.productCode === self.selectedProductCode(); }) || null;
    });

    this.load = function () {
      var id = self.requireCustomerId(); if (!id) return Promise.resolve();
      self.loading(true); self.errorMessage('');
      return Promise.all([
        gatewayApi.listDepositAccounts({ customerId: String(id), page: 0, size: 100 }),
        gatewayApi.listPublicProducts({ category: 'DEPOSIT', status: 'ACTIVE', page: 0, size: 100 })
      ]).then(function (results) {
        self.accounts(support.asArray(results[0]));
        self.products(support.asArray(results[1]).filter(function (product) { return product.subtype !== 'FIXED_DEPOSIT'; }));
      }).catch(self.fail).finally(function () { self.loading(false); });
    };

    this.showList = function () { self.mode('list'); self.selectedAccount(null); self.clearMessages(); };
    this.showOpen = function () { self.mode('open'); self.eligibility(null); self.clearMessages(); };

    this.openDetail = function (summary) {
      self.loading(true); self.clearMessages();
      return gatewayApi.getDepositAccount(summary.accountId).then(function (account) {
        self.selectedAccount(account); self.mode('detail');
      }).catch(self.fail).finally(function () { self.loading(false); });
    };

    this.checkEligibility = function () {
      var id = self.requireCustomerId(); var product = self.selectedProduct();
      self.clearMessages(); self.eligibility(null);
      if (!id || !product || Number(self.openingAmount()) < 0) {
        self.errorMessage('Choose an account product and enter a valid opening amount.'); return;
      }
      self.submitting(true);
      return gatewayApi.checkDepositEligibility({
        customerId: String(id), productId: product.productCode, productVersion: product.version,
        currency: product.currencyCode || 'INR', openingAmount: Number(self.openingAmount())
      }).then(function (result) {
        self.eligibility(result);
        if (!result.eligible) self.errorMessage('This account cannot be opened yet. Review the eligibility decision below.');
      }).catch(self.fail).finally(function () { self.submitting(false); });
    };

    this.openAccount = function () {
      var id = self.requireCustomerId(); var product = self.selectedProduct(); var decision = self.eligibility();
      if (!id || !product || !decision || !decision.eligible) {
        self.errorMessage('Run a successful eligibility check before opening the account.'); return;
      }
      self.submitting(true); self.clearMessages();
      return gatewayApi.openDepositAccount({
        customerIds: [String(id)], primaryCustomerId: String(id), productId: product.productCode,
        productVersion: product.version, currency: product.currencyCode || 'INR', openingAmount: Number(self.openingAmount()),
        servicingBranchId: self.servicingBranchId().trim(), operatingInstruction: self.operatingInstruction(),
        nominees: [], channel: 'WEB'
      }).then(function (account) {
        self.selectedAccount(account);
        self.accounts.unshift({
          accountId: account.accountId,
          maskedAccountNumber: account.maskedAccountNumber,
          productName: account.product && account.product.name,
          currency: account.currency,
          status: account.status,
          availableBalance: account.balance && account.balance.available,
          balanceAsOf: account.balance && account.balance.asOf,
          servicingBranchId: account.servicingBranchId,
          version: account.version
        });
        self.mode('detail');
        self.successMessage('Your deposit account has been created.');
      }).catch(self.fail).finally(function () { self.submitting(false); });
    };

    var baseConnected = this.connected;
    this.connected = function () { baseConnected(); return self.load(); };
  }

  return AccountsViewModel;
});
