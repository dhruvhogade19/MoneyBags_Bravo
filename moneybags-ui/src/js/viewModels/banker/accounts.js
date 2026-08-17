define([
  'knockout',
  'services/api/gatewayApi',
  'services/auth/session',
  'viewModels/banker/support',
  'css!views/banker/banker'
], function (ko, gatewayApi, session, support) {
  'use strict';

  function AccountsViewModel() {
    var self = this;
    this.searchState = support.createState();
    this.openState = support.createState();
    this.actionState = support.createState();
    this.accessDenied = ko.pureComputed(function () { return !support.isAdmin(session); });
    this.activeTab = ko.observable('search');
    this.customerFilter = ko.observable('');
    this.statusFilter = ko.observable('');
    this.accountId = ko.observable('');
    this.accounts = ko.observableArray([]);
    this.selectedAccount = ko.observable(null);
    this.products = ko.observableArray([]);
    this.primaryCustomerId = ko.observable('');
    this.productId = ko.observable('');
    this.openingAmount = ko.observable('1000');
    this.currency = ko.observable('INR');
    this.branchId = ko.observable('BRANCH-001');
    this.operatingInstruction = ko.observable('SINGLE');
    this.externalReference = ko.observable('');
    this.reasonCode = ko.observable('BANKER_REQUEST');
    this.reasonText = ko.observable('');
    this.money = support.money;
    this.date = support.date;

    this.productVersion = ko.pureComputed(function () {
      var match = self.products().find(function (item) { return item.productCode === self.productId(); });
      return match ? match.version : null;
    });

    this.availableCommands = ko.pureComputed(function () {
      var item = self.selectedAccount();
      if (!item) return [];
      return {
        PENDING_ACTIVATION: [{ code: 'activate', label: 'Activate' }],
        ACTIVE: [{ code: 'block', label: 'Block' }, { code: 'freeze', label: 'Freeze' }, { code: 'mark-dormant', label: 'Mark dormant' }],
        BLOCKED: [{ code: 'unblock', label: 'Unblock' }, { code: 'freeze', label: 'Freeze' }],
        FROZEN: [{ code: 'release-freeze', label: 'Release freeze' }],
        DORMANT: [{ code: 'reactivate', label: 'Reactivate' }, { code: 'freeze', label: 'Freeze' }]
      }[item.status] || [];
    });

    this.loadProducts = function () {
      return gatewayApi.listProducts({ category: 'DEPOSIT', status: 'ACTIVE', page: 0, size: 100 })
        .then(function (page) {
          self.products(support.content(page));
          if (!self.productId() && self.products().length) self.productId(self.products()[0].productCode);
        })
        .catch(function (error) { self.openState.error(support.errorMessage(error)); });
    };

    this.search = function () {
      var filters = { page: 0, size: 50 };
      if (String(self.customerFilter() || '').trim()) filters.customerId = String(self.customerFilter()).trim();
      if (self.statusFilter()) filters.status = self.statusFilter();
      return support.run(self.searchState, function () {
        return gatewayApi.searchDepositAccounts(filters).then(function (page) {
          self.accounts(support.content(page));
        });
      }).catch(function () {});
    };

    this.lookup = function () {
      var id = String(self.accountId() || '').trim();
      if (!id) { self.searchState.error('Enter an account ID.'); return Promise.resolve(); }
      return support.run(self.searchState, function () {
        return gatewayApi.getDepositAccount(id).then(function (account) { self.selectedAccount(account); });
      }, 'Account loaded.').catch(function () {});
    };

    this.selectAccount = function (account) {
      self.accountId(account.accountId);
      return self.lookup();
    };

    this.openAccount = function () {
      var customerId = String(self.primaryCustomerId() || '').trim();
      var version = self.productVersion();
      if (!customerId || !self.productId() || !version) {
        self.openState.error('Customer and an active deposit product are required.');
        return Promise.resolve();
      }
      var body = {
        customerIds: [customerId],
        primaryCustomerId: customerId,
        productId: self.productId(),
        productVersion: version,
        currency: String(self.currency() || 'INR').toUpperCase(),
        openingAmount: support.number(self.openingAmount()),
        servicingBranchId: String(self.branchId() || '').trim(),
        operatingInstruction: self.operatingInstruction(),
        nominees: [],
        channel: 'BANKER',
        externalReference: String(self.externalReference() || '').trim() || null
      };
      return support.run(self.openState, function () {
        return gatewayApi.openDepositAccount(body).then(function (account) {
          self.selectedAccount(account);
          self.accountId(account.accountId);
          self.activeTab('search');
          return self.search().then(function () { return account; });
        });
      }, function (account) { return 'Deposit account ' + account.accountId + ' created.'; }).catch(function () {});
    };

    this.runCommand = function (command) {
      var account = self.selectedAccount();
      if (!account) return Promise.resolve();
      return support.run(self.actionState, function () {
        return gatewayApi.commandDepositAccount(account.accountId, command.code, {
          reasonCode: String(self.reasonCode() || 'BANKER_REQUEST').trim(),
          reasonText: String(self.reasonText() || '').trim() || null,
          effectiveAt: null
        }, account.version).then(function (updated) { self.selectedAccount(updated); });
      }, command.label + ' completed.').catch(function () {});
    };

    this.connected = function () {
      document.title = 'Deposit account operations | MoneyBag';
      if (!self.accessDenied()) {
        self.loadProducts();
        self.search();
      }
    };
  }

  return AccountsViewModel;
});
