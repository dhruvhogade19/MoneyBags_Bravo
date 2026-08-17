define([
  'knockout',
  'services/api/gatewayApi',
  'services/auth/session',
  'viewModels/banker/support',
  'css!views/banker/banker'
], function (ko, gatewayApi, session, support) {
  'use strict';

  function CardsViewModel() {
    var self = this;
    this.state = support.createState();
    this.actionState = support.createState();
    this.accessDenied = ko.pureComputed(function () { return !support.isAdmin(session); });
    this.activeTab = ko.observable('applications');
    this.cifId = ko.observable('');
    this.applicationId = ko.observable('');
    this.accountId = ko.observable('');
    this.applications = ko.observableArray([]);
    this.accounts = ko.observableArray([]);
    this.selectedApplication = ko.observable(null);
    this.selectedAccount = ko.observable(null);
    this.money = support.money;
    this.date = support.date;
    this.mask = support.mask;

    this.findApplicationsByCif = function () {
      var id = String(self.cifId() || '').trim();
      if (!/^\d+$/.test(id)) { self.state.error('Enter a numeric CIF ID.'); return Promise.resolve(); }
      return support.run(self.state, function () {
        return gatewayApi.listCardApplicationsByCif(Number(id)).then(function (items) {
          self.applications(support.content(items));
        });
      }).catch(function () {});
    };

    this.findApplication = function () {
      var id = String(self.applicationId() || '').trim();
      if (!/^\d+$/.test(id)) { self.state.error('Enter a numeric application ID.'); return Promise.resolve(); }
      return support.run(self.state, function () {
        return gatewayApi.getCardApplication(Number(id)).then(function (item) { self.selectedApplication(item); });
      }, 'Application loaded.').catch(function () {});
    };

    this.selectApplication = function (item) {
      self.applicationId(item.applicationId);
      self.selectedApplication(item);
    };

    this.findAccountsByCif = function () {
      var id = String(self.cifId() || '').trim();
      if (!/^\d+$/.test(id)) { self.state.error('Enter a numeric CIF ID.'); return Promise.resolve(); }
      return support.run(self.state, function () {
        return gatewayApi.listCardAccountsByCif(Number(id)).then(function (items) {
          self.accounts(support.content(items));
        });
      }).catch(function () {});
    };

    this.findAccount = function () {
      var id = String(self.accountId() || '').trim();
      if (!/^\d+$/.test(id)) { self.state.error('Enter a numeric card account ID.'); return Promise.resolve(); }
      return support.run(self.state, function () {
        return gatewayApi.getCardAccount(Number(id)).then(function (item) { self.selectedAccount(item); });
      }, 'Card account loaded.').catch(function () {});
    };

    this.selectAccount = function (item) {
      self.accountId(item.accountId);
      self.selectedAccount(item);
    };

    this.closeAccount = function () {
      var item = self.selectedAccount();
      if (!item) return Promise.resolve();
      return support.run(self.actionState, function () {
        return gatewayApi.closeCardAccount(item.accountId).then(function (updated) { self.selectedAccount(updated); });
      }, 'Card account closure initiated.').catch(function () {});
    };

    this.connected = function () { document.title = 'Credit-card operations | MoneyBag'; };
  }

  return CardsViewModel;
});
