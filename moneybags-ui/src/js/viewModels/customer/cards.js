define([
  'knockout',
  'services/api/gatewayApi',
  'viewModels/customer/pageSupport',
  'ojs/ojbutton',
  'ojs/ojinputnumber',
  'ojs/ojprogress-circle'
], function (ko, gatewayApi, support) {
  'use strict';

  function CardsViewModel(params) {
    var self = support.create(this, params, 'Credit cards');
    this.products = ko.observableArray([]); this.applications = ko.observableArray([]); this.accounts = ko.observableArray([]);
    this.selectedProduct = ko.observable(null); this.selectedAccount = ko.observable(null); this.requestedCreditLimit = ko.observable(null);
    this.mode = ko.observable('catalogue'); this.money = support.money; this.dateTime = support.dateTime;
    this.label = support.label; this.statusClass = support.statusClass; this.mask = support.mask;

    this.cardRate = function (product) {
      var value = product.interestRate;
      if ((value === null || value === undefined) && product.interestRule) value = product.interestRule.annualInterestRate;
      return value === null || value === undefined ? 'At application' : Number(value).toFixed(2) + '% p.a.';
    };
    this.productDescription = function (product) {
      var messages = product && Array.isArray(product.messages) ? product.messages : [];
      if (messages.length) return messages.join(' ');
      return product && product.description
        ? product.description
        : 'Eligibility is checked securely when you apply.';
    };

    this.load = function () {
      var id = self.requireCustomerId(); if (!id) return Promise.resolve(); self.loading(true); self.errorMessage('');
      return Promise.all([
        gatewayApi.listCreditCardProducts(), gatewayApi.listCreditCardApplications(Number(id)), gatewayApi.listCreditCardAccounts(Number(id))
      ]).then(function (results) {
        self.products(support.asArray(results[0])); self.applications(support.asArray(results[1])); self.accounts(support.asArray(results[2]));
      }).catch(self.fail).finally(function () { self.loading(false); });
    };

    this.show = function (mode) { self.mode(mode); self.selectedProduct(null); self.selectedAccount(null); self.clearMessages(); };
    this.startApplication = function (product) { self.selectedProduct(product); self.requestedCreditLimit(null); self.mode('apply'); self.clearMessages(); };
    this.submitApplication = function () {
      var id = self.requireCustomerId(); var product = self.selectedProduct();
      if (!id || !product || Number(self.requestedCreditLimit()) <= 0) { self.errorMessage('Choose a product and enter a positive requested limit.'); return; }
      self.submitting(true); self.clearMessages();
      return gatewayApi.submitCreditCardApplication({ cifId: Number(id), productCode: product.productCode,
        requestedCreditLimit: Number(self.requestedCreditLimit())
      }).then(function (application) {
        return Promise.all([
          gatewayApi.listCreditCardApplications(Number(id)),
          gatewayApi.listCreditCardAccounts(Number(id))
        ]).then(function (results) {
          self.applications(support.asArray(results[0]));
          self.accounts(support.asArray(results[1]));
          if (application.applicationStatus === 'APPROVED') {
            self.mode('accounts');
            self.successMessage('Application approved. Your new card account is ready below.');
          } else {
            self.mode('applications');
            self.successMessage('Your eligibility decision has been recorded.');
          }
        });
      }).catch(self.fail).finally(function () { self.submitting(false); });
    };

    this.openAccount = function (summary) {
      self.loading(true); self.clearMessages();
      return gatewayApi.getCreditCardAccount(summary.accountId).then(function (account) { self.selectedAccount(account); self.mode('detail'); })
        .catch(self.fail).finally(function () { self.loading(false); });
    };

    var baseConnected = this.connected; this.connected = function () { baseConnected(); return self.load(); };
  }

  return CardsViewModel;
});
