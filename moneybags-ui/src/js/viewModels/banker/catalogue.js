define([
  'knockout',
  'services/api/gatewayApi',
  'services/auth/session',
  'viewModels/banker/support',
  'css!views/banker/banker'
], function (ko, gatewayApi, session, support) {
  'use strict';

  function CatalogueViewModel(params) {
    var self = this;
    this.state = support.createState();
    this.actionState = support.createState();
    this.accessDenied = ko.pureComputed(function () { return !support.isAdmin(session); });
    this.category = ko.observable('');
    this.subtype = ko.observable('');
    this.status = ko.observable('');
    this.productName = ko.observable('');
    this.products = ko.observableArray([]);
    this.money = support.money;

    this.navigate = function (path) { return support.navigate(params, path); };
    this.remember = function (item) {
      try { window.sessionStorage.setItem('moneybags-admin-product-code', item.productCode); } catch (ignore) {}
    };
    this.openEditor = function (item) { self.remember(item); return self.navigate('banker-catalogue-editor'); };
    this.openPricing = function (item) { self.remember(item); return self.navigate('banker-catalogue-pricing'); };

    this.load = function () {
      var filters = { page: 0, size: 100 };
      if (self.category()) filters.category = self.category();
      if (self.subtype()) filters.subtype = self.subtype();
      if (self.status()) filters.status = self.status();
      if (String(self.productName() || '').trim()) filters.productName = String(self.productName()).trim();
      return support.run(self.state, function () {
        return gatewayApi.listProducts(filters).then(function (page) { self.products(support.content(page)); });
      }).catch(function () {});
    };

    this.changeStatus = function (item, status) {
      return support.run(self.actionState, function () {
        return gatewayApi.changeProductStatus(item.productCode, {
          status: status,
          changedBy: support.actor(session)
        }).then(function () { return self.load(); });
      }, 'Product status changed to ' + status + '.').catch(function () {});
    };

    this.connected = function () {
      document.title = 'Product Master | MoneyBag';
      if (!self.accessDenied()) self.load();
    };
  }

  return CatalogueViewModel;
});
