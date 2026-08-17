define([
  'knockout',
  'services/api/gatewayApi',
  'services/auth/session',
  'viewModels/banker/support',
  'css!views/banker/banker'
], function (ko, gatewayApi, session, support) {
  'use strict';

  function CustomersViewModel(params) {
    var self = this;
    this.state = support.createState();
    this.accessDenied = ko.pureComputed(function () { return !support.isAdmin(session); });
    this.cifId = ko.observable('');
    this.customer = ko.observable(null);
    this.kycs = ko.observableArray([]);
    this.depositAccounts = ko.observableArray([]);
    this.cardApplications = ko.observableArray([]);
    this.cardAccounts = ko.observableArray([]);
    this.payments = ko.observableArray([]);
    this.partialWarning = ko.observable('');
    this.money = support.money;
    this.date = support.date;
    this.mask = support.mask;
    this.navigate = function (path) { return support.navigate(params, path); };

    function related(work, fallback) {
      return Promise.resolve().then(work).catch(function () {
        self.partialWarning('Some related customer information could not be loaded. The CIF profile is still shown.');
        return fallback;
      });
    }

    this.lookup = function () {
      var id = String(self.cifId() || '').trim();
      if (!/^\d+$/.test(id)) {
        self.state.error('Enter a numeric CIF ID.');
        return Promise.resolve();
      }
      self.customer(null);
      self.kycs([]);
      self.depositAccounts([]);
      self.cardApplications([]);
      self.cardAccounts([]);
      self.payments([]);
      self.partialWarning('');
      return support.run(self.state, function () {
        return gatewayApi.getCif(Number(id)).then(function (customer) {
          self.customer(customer);
          return Promise.all([
            related(function () { return gatewayApi.getKycQueue({ cifId: Number(id), page: 0, size: 10 }); }, { content: [] }),
            related(function () { return gatewayApi.searchDepositAccounts({ customerId: id, page: 0, size: 10 }); }, { content: [] }),
            related(function () { return gatewayApi.listCardApplicationsByCif(Number(id)); }, []),
            related(function () { return gatewayApi.listCardAccountsByCif(Number(id)); }, []),
            related(function () { return gatewayApi.listPayments(Number(id), 0, 10); }, { content: [] })
          ]).then(function (results) {
            self.kycs(support.content(results[0]));
            self.depositAccounts(support.content(results[1]));
            self.cardApplications(support.content(results[2]));
            self.cardAccounts(support.content(results[3]));
            self.payments(support.content(results[4]));
          });
        });
      }, 'Customer 360 loaded.').catch(function () {});
    };

    this.connected = function () {
      document.title = 'Customer 360 | MoneyBag';
    };
  }

  return CustomersViewModel;
});
