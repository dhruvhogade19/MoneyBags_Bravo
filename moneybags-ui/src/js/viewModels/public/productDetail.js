define([
  'knockout',
  'services/api/gatewayApi',
  'ojs/ojbutton',
  'ojs/ojprogress-circle',
  'css!views/public/public'
], function (ko, gatewayApi) {
  'use strict';

  function ProductDetailViewModel(params) {
    var self = this;
    var routeParams = params.routerState.params || {};
    this.router = params.router;
    this.loading = ko.observable(true);
    this.errorMessage = ko.observable('');
    this.product = ko.observable(null);
    this.productCode = routeParams.productCode || window.sessionStorage.getItem('moneybags:selectedProductCode');

    this.money = function (value, currency) {
      if (value === null || value === undefined) return '-';
      return new Intl.NumberFormat('en-IN', { style: 'currency', currency: currency || 'INR', maximumFractionDigits: 2 }).format(value);
    };

    this.label = function (value) {
      return value ? String(value).replace(/_/g, ' ') : '-';
    };

    this.rate = function () {
      var rule = self.product() && self.product().interestRule;
      return rule && rule.annualInterestRate !== null ? Number(rule.annualInterestRate).toFixed(2) + '% p.a.' : 'See applicable rate at application';
    };

    this.start = function () {
      var product = self.product();
      if (product) window.sessionStorage.setItem('moneybags:selectedProductCode', product.productCode);
      return self.router.go({ path: 'register' });
    };

    this.back = function () { return self.router.go({ path: 'products' }); };

    this.load = function () {
      if (!self.productCode) {
        self.errorMessage('Choose a product from the catalogue first.');
        self.loading(false);
        return Promise.resolve();
      }
      return gatewayApi.getPublicProduct(self.productCode)
        .then(function (product) { self.product(product); })
        .catch(function (error) { self.errorMessage(error.message || 'This product could not be loaded.'); })
        .finally(function () { self.loading(false); });
    };

    this.connected = function () {
      document.title = 'Product details | MoneyBag';
      return self.load();
    };
  }

  return ProductDetailViewModel;
});
