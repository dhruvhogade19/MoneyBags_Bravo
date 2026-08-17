define([
  'knockout',
  'services/api/gatewayApi',
  'services/auth/session',
  'viewModels/banker/support',
  'css!views/banker/banker'
], function (ko, gatewayApi, session, support) {
  'use strict';

  function PaymentsViewModel() {
    var self = this;
    this.state = support.createState();
    this.actionState = support.createState();
    this.accessDenied = ko.pureComputed(function () { return !support.isAdmin(session); });
    this.customerId = ko.observable('');
    this.paymentId = ko.observable('');
    this.payments = ko.observableArray([]);
    this.selectedPayment = ko.observable(null);
    this.money = support.money;
    this.date = support.date;
    this.canCancel = ko.pureComputed(function () {
      var payment = self.selectedPayment();
      return payment && ['PENDING_VALIDATION', 'PENDING_RESERVATION', 'PENDING_ACCOUNTING', 'PENDING_SETTLEMENT', 'PENDING_BILLING'].indexOf(payment.status) >= 0;
    });

    this.list = function () {
      var id = String(self.customerId() || '').trim();
      if (!/^\d+$/.test(id)) { self.state.error('Enter a numeric customer ID.'); return Promise.resolve(); }
      return support.run(self.state, function () {
        return gatewayApi.listPayments(Number(id), 0, 100).then(function (page) { self.payments(support.content(page)); });
      }).catch(function () {});
    };

    this.lookup = function () {
      var id = String(self.paymentId() || '').trim();
      if (!id) { self.state.error('Enter a payment ID.'); return Promise.resolve(); }
      return support.run(self.state, function () {
        return gatewayApi.getPayment(id).then(function (payment) { self.selectedPayment(payment); });
      }, 'Payment loaded.').catch(function () {});
    };

    this.selectPayment = function (payment) {
      self.paymentId(payment.paymentId);
      self.selectedPayment(payment);
    };

    this.cancel = function () {
      var payment = self.selectedPayment();
      if (!payment) return Promise.resolve();
      return support.run(self.actionState, function () {
        return gatewayApi.cancelPayment(payment.paymentId).then(function (updated) { self.selectedPayment(updated); });
      }, 'Payment cancellation recorded.').catch(function () {});
    };

    this.connected = function () { document.title = 'Payment operations | MoneyBag'; };
  }

  return PaymentsViewModel;
});
