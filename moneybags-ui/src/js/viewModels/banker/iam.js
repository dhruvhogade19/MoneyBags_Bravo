define([
  'knockout',
  'services/api/gatewayApi',
  'services/auth/session',
  'viewModels/banker/support',
  'css!views/banker/banker'
], function (ko, gatewayApi, session, support) {
  'use strict';

  function IamViewModel() {
    var self = this;
    this.lookupState = support.createState();
    this.createState = support.createState();
    this.accessDenied = ko.pureComputed(function () { return !support.isAdmin(session); });
    this.userId = ko.observable('');
    this.user = ko.observable(null);
    this.username = ko.observable('');
    this.password = ko.observable('');
    this.role = ko.observable('CONSUMER');
    this.customerId = ko.observable('');
    this.tenantId = ko.observable('moneybags');

    this.lookup = function () {
      var id = String(self.userId() || '').trim();
      if (!id) { self.lookupState.error('Enter an identity user ID.'); return Promise.resolve(); }
      return support.run(self.lookupState, function () {
        return gatewayApi.getIdentityUser(id).then(function (user) { self.user(user); });
      }, 'Identity user loaded.').catch(function () {});
    };

    this.create = function () {
      if (String(self.password() || '').length < 12) {
        self.createState.error('Password must contain at least 12 characters.');
        return Promise.resolve();
      }
      return support.run(self.createState, function () {
        return gatewayApi.createIdentityUser({
          username: String(self.username() || '').trim().toLowerCase(),
          password: self.password(),
          customerId: self.role() === 'CONSUMER' ? (String(self.customerId() || '').trim() || null) : null,
          tenantId: String(self.tenantId() || 'moneybags').trim(),
          role: self.role()
        }).then(function (created) {
          self.user(created);
          self.userId(created.id);
          self.password('');
          return created;
        });
      }, function (created) { return 'Identity user ' + created.username + ' created.'; }).catch(function () {});
    };

    this.connected = function () { document.title = 'Identity access management | MoneyBag'; };
  }

  return IamViewModel;
});
