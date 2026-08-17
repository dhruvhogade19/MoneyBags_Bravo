define([
  'knockout',
  'services/api/gatewayApi',
  'services/auth/session',
  'viewModels/banker/support',
  'css!views/banker/banker'
], function (ko, gatewayApi, session, support) {
  'use strict';

  function DashboardViewModel(params) {
    var self = this;
    this.state = support.createState();
    this.accessDenied = ko.pureComputed(function () { return !support.isAdmin(session); });
    this.kycCount = ko.observable('—');
    this.accountCount = ko.observable('—');
    this.productCount = ko.observable('—');
    this.journalCount = ko.observable('—');
    this.lastRefreshed = ko.observable('Not refreshed');

    this.navigate = function (path) { return support.navigate(params, path); };

    this.refresh = function () {
      if (self.accessDenied()) return Promise.resolve();
      self.state.loading(true);
      self.state.error('');
      self.state.success('');
      var failures = 0;
      function safe(promise) {
        return promise.catch(function () { failures += 1; return null; });
      }
      return Promise.all([
        safe(gatewayApi.getKycQueue({ statuses: ['PENDING', 'FLAGGED'], page: 0, size: 1 })),
        safe(gatewayApi.searchDepositAccounts({ page: 0, size: 1 })),
        safe(gatewayApi.listProducts({ page: 0, size: 1 })),
        safe(gatewayApi.listJournals({ page: 0, size: 1 }))
      ]).then(function (results) {
        self.kycCount(results[0] ? (results[0].totalElements || 0) : '—');
        self.accountCount(results[1] ? (results[1].totalElements || 0) : '—');
        self.productCount(results[2] ? (results[2].totalElements || 0) : '—');
        self.journalCount(results[3] ? (results[3].totalElements || 0) : '—');
        self.lastRefreshed(new Date().toLocaleTimeString('en-IN'));
        if (failures) self.state.error(failures + ' dashboard metric' + (failures === 1 ? ' is' : 's are') + ' temporarily unavailable.');
        else self.state.success('Operations snapshot refreshed.');
      }).finally(function () {
        self.state.loading(false);
      });
    };

    this.connected = function () {
      document.title = 'Operations dashboard | MoneyBag';
      self.refresh();
    };
  }

  return DashboardViewModel;
});
