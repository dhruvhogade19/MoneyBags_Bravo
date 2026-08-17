define([
  'knockout',
  'services/api/gatewayApi',
  'viewModels/customer/pageSupport',
  'ojs/ojbutton',
  'ojs/ojinputnumber',
  'ojs/ojinputtext',
  'ojs/ojdatetimepicker',
  'ojs/ojprogress-circle'
], function (ko, gatewayApi, support) {
  'use strict';

  function DepositsViewModel(params) {
    var self = support.create(this, params, 'Fixed deposits');
    this.deposits = ko.observableArray([]); this.accounts = ko.observableArray([]); this.products = ko.observableArray([]);
    this.selectedDeposit = ko.observable(null); this.quote = ko.observable(null); this.mode = ko.observable('list');
    this.selectedProductCode = ko.observable(''); this.principal = ko.observable(null); this.tenureValue = ko.observable(12);
    this.tenureUnit = ko.observable('MONTH'); this.valueDate = ko.observable(new Date().toISOString().slice(0, 10));
    this.fundingAccountId = ko.observable(''); this.payoutAccountId = ko.observable(''); this.servicingBranchId = ko.observable('MB-DIGITAL');
    this.money = support.money; this.date = support.date; this.label = support.label; this.statusClass = support.statusClass;

    this.selectedProduct = ko.pureComputed(function () { return self.products().find(function (item) { return item.productCode === self.selectedProductCode(); }) || null; });
    this.activeAccounts = ko.pureComputed(function () { return self.accounts().filter(function (account) { return account.status === 'ACTIVE'; }); });
    [this.selectedProductCode, this.principal, this.tenureValue, this.tenureUnit, this.valueDate].forEach(function (field) {
      field.subscribe(function () { self.quote(null); });
    });

    this.load = function () {
      var id = self.requireCustomerId(); if (!id) return Promise.resolve(); self.loading(true); self.errorMessage('');
      return Promise.all([
        gatewayApi.listFixedDeposits({ customerId: String(id), page: 0, size: 100 }),
        gatewayApi.listDepositAccounts({ customerId: String(id), status: 'ACTIVE', page: 0, size: 100 }),
        gatewayApi.listPublicProducts({ category: 'DEPOSIT', subtype: 'FIXED_DEPOSIT', status: 'ACTIVE', page: 0, size: 100 })
      ]).then(function (results) {
        self.deposits(support.asArray(results[0])); self.accounts(support.asArray(results[1])); self.products(support.asArray(results[2]));
      }).catch(self.fail).finally(function () { self.loading(false); });
    };

    this.showList = function () { self.mode('list'); self.selectedDeposit(null); self.clearMessages(); };
    this.showOpen = function () { self.mode('open'); self.quote(null); self.clearMessages(); };
    this.openDetail = function (summary) {
      self.loading(true); self.clearMessages();
      return gatewayApi.getFixedDeposit(summary.fixedDepositId).then(function (deposit) { self.selectedDeposit(deposit); self.mode('detail'); })
        .catch(self.fail).finally(function () { self.loading(false); });
    };

    this.requestQuote = function () {
      var id = self.requireCustomerId(); var product = self.selectedProduct(); self.clearMessages(); self.quote(null);
      if (!id || !product || Number(self.principal()) <= 0 || Number(self.tenureValue()) < 1) { self.errorMessage('Choose a product and enter a valid principal and tenure.'); return; }
      self.submitting(true);
      return gatewayApi.quoteFixedDeposit({ customerId: String(id), productCode: product.productCode, productVersion: product.version,
        principal: Number(self.principal()), currency: product.currencyCode || 'INR', tenureValue: Number(self.tenureValue()),
        tenureUnit: self.tenureUnit(), interestPayoutFrequency: 'AT_MATURITY', valueDate: self.valueDate()
      }).then(function (quote) { self.quote(quote); }).catch(self.fail).finally(function () { self.submitting(false); });
    };

    this.book = function () {
      var id = self.requireCustomerId(); var quote = self.quote();
      if (!id || !quote || !self.fundingAccountId() || !self.payoutAccountId()) { self.errorMessage('Get a quote and choose both funding and payout accounts.'); return; }
      self.submitting(true); self.clearMessages();
      return gatewayApi.bookFixedDeposit({ customerIds: [String(id)], primaryCustomerId: String(id), productCode: quote.productCode,
        productVersion: quote.productVersion, principal: Number(self.principal()), currency: self.selectedProduct().currencyCode || 'INR', tenureValue: Number(self.tenureValue()),
        tenureUnit: self.tenureUnit(), interestPayoutFrequency: 'AT_MATURITY', fundingAccountId: self.fundingAccountId(),
        payoutAccountId: self.payoutAccountId(), servicingBranchId: self.servicingBranchId().trim(), nominees: [], channel: 'WEB'
      }).then(function (deposit) {
        self.selectedDeposit(deposit); self.deposits.unshift(deposit); self.mode('detail'); self.successMessage('Your fixed deposit has been booked.');
      }).catch(self.fail).finally(function () { self.submitting(false); });
    };

    var baseConnected = this.connected; this.connected = function () { baseConnected(); return self.load(); };
  }

  return DepositsViewModel;
});
