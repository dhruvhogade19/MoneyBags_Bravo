define([
  'knockout',
  'services/api/gatewayApi',
  'services/auth/session',
  'viewModels/banker/support',
  'css!views/banker/banker'
], function (ko, gatewayApi, session, support) {
  'use strict';

  function EodViewModel() {
    var self = this;
    this.state = support.createState();
    this.lookupState = support.createState();
    this.accessDenied = ko.pureComputed(function () { return !support.isAdmin(session); });
    this.businessDate = ko.observable(support.today());
    this.cardReadiness = ko.observable(null);
    this.accountingPeriod = ko.observable(null);
    this.periodUnavailable = ko.observable(false);
    this.trialBalanceRunId = ko.observable('');
    this.reconciliationRunId = ko.observable('');
    this.trialBalance = ko.observable(null);
    this.reconciliation = ko.observable(null);
    this.money = support.money;
    this.date = support.date;

    this.refresh = function () {
      if (!self.businessDate()) { self.state.error('Choose a business date.'); return Promise.resolve(); }
      self.state.loading(true);
      self.state.error('');
      self.state.success('');
      self.cardReadiness(null);
      self.accountingPeriod(null);
      self.periodUnavailable(false);
      return Promise.all([
        gatewayApi.getCreditCardEodReadiness().then(function (value) { self.cardReadiness(value); })
          .catch(function (error) { self.state.error('Credit-card readiness is unavailable: ' + support.errorMessage(error)); }),
        gatewayApi.getAccountingPeriod(self.businessDate()).then(function (value) { self.accountingPeriod(value); })
          .catch(function () { self.periodUnavailable(true); })
      ]).then(function () {
        self.state.success('Available read-only EOD checks refreshed.');
      }).finally(function () { self.state.loading(false); });
    };

    this.lookupTrialBalance = function () {
      var id = String(self.trialBalanceRunId() || '').trim();
      if (!id) { self.lookupState.error('Enter a trial-balance run ID.'); return Promise.resolve(); }
      return support.run(self.lookupState, function () {
        return gatewayApi.getTrialBalance(id).then(function (value) { self.trialBalance(value); });
      }, 'Trial balance loaded.').catch(function () {});
    };

    this.lookupReconciliation = function () {
      var id = String(self.reconciliationRunId() || '').trim();
      if (!id) { self.lookupState.error('Enter a reconciliation run ID.'); return Promise.resolve(); }
      return support.run(self.lookupState, function () {
        return gatewayApi.getReconciliation(id).then(function (value) { self.reconciliation(value); });
      }, 'Reconciliation run loaded.').catch(function () {});
    };

    this.connected = function () {
      document.title = 'End-of-day cockpit | MoneyBag';
      if (!self.accessDenied()) self.refresh();
    };
  }

  return EodViewModel;
});
