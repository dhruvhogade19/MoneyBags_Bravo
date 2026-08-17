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

  function PaymentsViewModel(params) {
    var self = support.create(this, params, 'Payments');
    this.payments = ko.observableArray([]); this.accounts = ko.observableArray([]); this.cards = ko.observableArray([]); this.bills = ko.observableArray([]);
    this.selectedPayment = ko.observable(null); this.mode = ko.observable('book');
    this.sourceAccountId = ko.observable(''); this.targetAccountId = ko.observable(''); this.transferAmount = ko.observable(null); this.transferReference = ko.observable('');
    this.merchantCardAccountId = ko.observable(''); this.merchantId = ko.observable(''); this.merchantAmount = ko.observable(null); this.merchantReference = ko.observable('');
    this.repaymentSourceId = ko.observable(''); this.repaymentBillId = ko.observable(''); this.repaymentAmount = ko.observable(null); this.repaymentReference = ko.observable('Card bill repayment');
    this.money = support.money; this.dateTime = support.dateTime; this.label = support.label; this.statusClass = support.statusClass;
    this.paymentTimestamp = function (payment) {
      return payment && (payment.createdAt || payment.createdOn || payment.updatedAt || payment.settledAt);
    };

    this.selectedSource = ko.pureComputed(function () { return self.accounts().find(function (item) { return item.accountId === self.sourceAccountId(); }); });
    this.selectedBill = ko.pureComputed(function () { return self.bills().find(function (item) { return item.billId === self.repaymentBillId(); }); });
    this.activeAccounts = ko.pureComputed(function () { return self.accounts().filter(function (item) { return item.status === 'ACTIVE'; }); });
    this.activeCards = ko.pureComputed(function () { return self.cards().filter(function (item) { return item.status === 'ACTIVE'; }); });
    this.cardLabel = function (card) {
      return (card.cardNumber || ('Card account ' + card.accountId)) + ' — available ' + self.money(card.availableLimit, 'INR');
    };

    this.load = function () {
      var id = self.requireCustomerId(); if (!id) return Promise.resolve(); self.loading(true); self.errorMessage('');
      return Promise.all([
        gatewayApi.listPayments({ customerId: Number(id), page: 0, size: 100 }),
        gatewayApi.listDepositAccounts({ customerId: String(id), status: 'ACTIVE', page: 0, size: 100 }),
        gatewayApi.listCreditCardAccounts(Number(id)), gatewayApi.listBills({ cifId: Number(id), page: 0, size: 100 })
      ]).then(function (results) {
        self.payments(support.asArray(results[0])); self.accounts(support.asArray(results[1])); self.cards(support.asArray(results[2]));
        self.bills(support.asArray(results[3]).filter(function (bill) { return Number(bill.outstandingAmount) > 0; }));
      }).catch(self.fail).finally(function () { self.loading(false); });
    };

    this.show = function (mode) { self.mode(mode); self.selectedPayment(null); self.clearMessages(); };
    this.openPayment = function (summary) {
      self.loading(true); self.clearMessages();
      return gatewayApi.getPayment(summary.paymentId).then(function (payment) { self.selectedPayment(payment); self.mode('detail'); })
        .catch(self.fail).finally(function () { self.loading(false); });
    };

    this.bookTransfer = function () {
      var id = self.requireCustomerId(); var source = self.selectedSource();
      if (!id || !source || !self.targetAccountId().trim() || Number(self.transferAmount()) <= 0) { self.errorMessage('Choose a source, enter a target account ID, and provide a positive amount.'); return; }
      self.submitting(true); self.clearMessages();
      return gatewayApi.createBookTransfer({ requestorCustomerId: Number(id), sourceAccountId: source.accountId,
        targetAccountId: self.targetAccountId().trim(), amount: Number(self.transferAmount()), currencyCode: source.currency,
        reference: self.transferReference().trim() || null
      }).then(function (payment) {
        self.payments.unshift(payment); self.selectedPayment(payment); self.mode('detail'); self.successMessage('Transfer request completed with the status shown below.');
      }).catch(self.fail).finally(function () { self.submitting(false); });
    };

    this.repayCard = function () {
      var id = self.requireCustomerId(); var source = self.activeAccounts().find(function (item) { return item.accountId === self.repaymentSourceId(); }); var bill = self.selectedBill();
      if (!id || !source || !bill || Number(self.repaymentAmount()) <= 0) { self.errorMessage('Choose a bill and source account, then enter a positive amount.'); return; }
      self.submitting(true); self.clearMessages();
      return gatewayApi.createCardRepayment({ requestorCustomerId: Number(id), billId: bill.billId,
        sourceDepositAccountId: source.accountId, creditCardAccountId: String(bill.accountId), amount: Number(self.repaymentAmount()),
        currencyCode: bill.currency, reference: self.repaymentReference().trim() || null
      }).then(function (payment) {
        self.payments.unshift(payment); self.selectedPayment(payment); self.mode('detail'); self.successMessage('Repayment request completed with the status shown below.');
      }).catch(self.fail).finally(function () { self.submitting(false); });
    };

    this.payMerchant = function () {
      var id = self.requireCustomerId();
      var card = self.activeCards().find(function (item) { return String(item.accountId) === String(self.merchantCardAccountId()); });
      if (!id || !card || !self.merchantId().trim() || Number(self.merchantAmount()) <= 0) {
        self.errorMessage('Choose an active card, enter a merchant ID, and provide a positive amount.'); return;
      }
      self.submitting(true); self.clearMessages();
      return gatewayApi.createMerchantPayment({
        requestorCustomerId: Number(id), creditCardAccountId: String(card.accountId),
        merchantId: self.merchantId().trim(), amount: Number(self.merchantAmount()), currencyCode: 'INR',
        reference: self.merchantReference().trim() || null
      }).then(function (payment) {
        self.payments.unshift(payment); self.selectedPayment(payment); self.mode('detail');
        self.successMessage('Card payment captured. It is available to bill generation and payment history.');
        return gatewayApi.listCreditCardAccounts(Number(id));
      }).then(function (cards) { if (cards) self.cards(support.asArray(cards)); })
        .catch(self.fail).finally(function () { self.submitting(false); });
    };

    var baseConnected = this.connected; this.connected = function () { baseConnected(); return self.load(); };
  }

  return PaymentsViewModel;
});
