define([
  'knockout',
  'services/api/gatewayApi',
  'viewModels/customer/pageSupport',
  'ojs/ojbutton',
  'ojs/ojprogress-circle'
], function (ko, gatewayApi, support) {
  'use strict';

  function DashboardViewModel(params) {
    var self = support.create(this, params, 'Your dashboard');
    this.accounts = ko.observableArray([]);
    this.fixedDeposits = ko.observableArray([]);
    this.cards = ko.observableArray([]);
    this.bills = ko.observableArray([]);
    this.payments = ko.observableArray([]);
    this.notifications = ko.observableArray([]);
    this.money = support.money;
    this.date = support.date;
    this.dateTime = support.dateTime;
    this.label = support.label;
    this.statusClass = support.statusClass;

    this.firstName = ko.pureComputed(function () {
      var current = self.currentUser();
      return (current && (current.firstName || current.name || current.username)) || 'there';
    });

    this.hasAnyData = ko.pureComputed(function () {
      return self.accounts().length || self.fixedDeposits().length || self.cards().length || self.bills().length || self.payments().length;
    });

    this.load = function () {
      var id = self.requireCustomerId();
      if (!id) return Promise.resolve();
      self.loading(true);
      self.errorMessage('');
      return Promise.all([
        gatewayApi.listDepositAccounts({ customerId: String(id), page: 0, size: 5 }),
        gatewayApi.listFixedDeposits({ customerId: String(id), page: 0, size: 5 }),
        gatewayApi.listCreditCardAccounts(Number(id)),
        gatewayApi.listBills({ cifId: Number(id), page: 0, size: 5 }),
        gatewayApi.listPayments({ customerId: Number(id), page: 0, size: 5 }),
        gatewayApi.listNotifications({ cifId: Number(id), page: 0, size: 5 })
      ]).then(function (results) {
        self.accounts(support.asArray(results[0]));
        self.fixedDeposits(support.asArray(results[1]));
        self.cards(support.asArray(results[2]));
        self.bills(support.asArray(results[3]));
        self.payments(support.asArray(results[4]));
        self.notifications(support.asArray(results[5]));
      }).catch(self.fail).finally(function () { self.loading(false); });
    };

    var baseConnected = this.connected;
    this.connected = function () { baseConnected(); return self.load(); };
  }

  return DashboardViewModel;
});
