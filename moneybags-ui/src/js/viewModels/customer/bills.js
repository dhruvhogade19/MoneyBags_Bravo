define([
  'knockout',
  'services/api/gatewayApi',
  'viewModels/customer/pageSupport',
  'ojs/ojbutton',
  'ojs/ojinputnumber',
  'ojs/ojprogress-circle'
], function (ko, gatewayApi, support) {
  'use strict';

  function BillsViewModel(params) {
    var self = support.create(this, params, 'Bills');
    this.bills = ko.observableArray([]); this.accounts = ko.observableArray([]); this.selectedBill = ko.observable(null);
    this.mode = ko.observable('list'); this.sourceAccountId = ko.observable(''); this.repaymentAmount = ko.observable(null);
    this.money = support.money; this.date = support.date; this.dateTime = support.dateTime; this.label = support.label; this.statusClass = support.statusClass;

    this.load = function () {
      var id = self.requireCustomerId(); if (!id) return Promise.resolve(); self.loading(true); self.errorMessage('');
      return Promise.all([gatewayApi.listBills({ cifId: Number(id), page: 0, size: 100 }),
        gatewayApi.listDepositAccounts({ customerId: String(id), status: 'ACTIVE', page: 0, size: 100 })])
        .then(function (results) { self.bills(support.asArray(results[0])); self.accounts(support.asArray(results[1])); })
        .catch(self.fail).finally(function () { self.loading(false); });
    };

    this.openBill = function (summary) {
      self.loading(true); self.clearMessages();
      return gatewayApi.getBill(summary.billId).then(function (bill) { self.selectedBill(bill); self.repaymentAmount(bill.outstandingAmount); self.mode('detail'); })
        .catch(self.fail).finally(function () { self.loading(false); });
    };
    this.showList = function () { self.mode('list'); self.selectedBill(null); self.clearMessages(); };
    this.startRepayment = function () { self.mode('repay'); self.clearMessages(); };
    this.repay = function () {
      var id = self.requireCustomerId(); var bill = self.selectedBill(); var source = self.accounts().find(function (item) { return item.accountId === self.sourceAccountId(); });
      if (!id || !bill || !source || Number(self.repaymentAmount()) <= 0) { self.errorMessage('Choose a source account and enter a positive repayment amount.'); return; }
      self.submitting(true); self.clearMessages();
      return gatewayApi.createCardRepayment({ requestorCustomerId: Number(id), billId: bill.billId,
        sourceDepositAccountId: source.accountId, creditCardAccountId: String(bill.accountId), amount: Number(self.repaymentAmount()),
        currencyCode: bill.currency, reference: 'Credit-card bill repayment'
      }).then(function (payment) {
        self.mode('receipt'); self.successMessage('Repayment submitted. Payment status: ' + support.label(payment.status) + '.'); self.paymentResult = payment;
      }).catch(self.fail).finally(function () { self.submitting(false); });
    };

    var baseConnected = this.connected; this.connected = function () { baseConnected(); return self.load(); };
  }

  return BillsViewModel;
});
