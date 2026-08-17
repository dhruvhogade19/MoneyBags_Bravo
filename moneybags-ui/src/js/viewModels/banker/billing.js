define([
  'knockout',
  'services/api/gatewayApi',
  'services/auth/session',
  'viewModels/banker/support',
  'css!views/banker/banker'
], function (ko, gatewayApi, session, support) {
  'use strict';

  function BillingViewModel() {
    var self = this;
    this.state = support.createState();
    this.accessDenied = ko.pureComputed(function () { return !support.isAdmin(session); });
    this.billId = ko.observable('');
    this.bill = ko.observable(null);
    this.money = support.money;
    this.date = support.date;

    this.lookup = function () {
      var id = String(self.billId() || '').trim();
      if (!id) { self.state.error('Enter a bill ID.'); return Promise.resolve(); }
      return support.run(self.state, function () {
        return gatewayApi.getBill(id).then(function (bill) { self.bill(bill); });
      }, 'Bill loaded.').catch(function () {});
    };

    this.connected = function () { document.title = 'Billing lookup | MoneyBag'; };
  }

  return BillingViewModel;
});
