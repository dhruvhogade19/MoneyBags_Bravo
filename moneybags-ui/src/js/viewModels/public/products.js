define([
  'knockout',
  'services/api/gatewayApi',
  'ojs/ojbutton',
  'ojs/ojinputsearch',
  'ojs/ojprogress-circle',
  'css!views/public/public'
], function (ko, gatewayApi) {
  'use strict';

  function ProductsViewModel(params) {
    var self = this;
    this.router = params.router;
    this.loading = ko.observable(true);
    this.errorMessage = ko.observable('');
    this.products = ko.observableArray([]);
    this.searchText = ko.observable('');
    this.category = ko.observable('');

    this.visibleProducts = ko.pureComputed(function () {
      var term = self.searchText().trim().toLowerCase();
      var category = self.category();
      return self.products().filter(function (product) {
        var matchesCategory = !category || product.category === category;
        var haystack = [product.productName, product.productCode, product.description, product.subtype]
          .join(' ').toLowerCase();
        return matchesCategory && (!term || haystack.indexOf(term) >= 0);
      });
    });

    this.formatRate = function (product) {
      var rate = product && product.interestRule && product.interestRule.annualInterestRate;
      return rate === null || rate === undefined ? 'Rate shown in details' : Number(rate).toFixed(2) + '% p.a.';
    };

    this.productType = function (product) {
      return String(product.subtype || product.category || 'Product').replace(/_/g, ' ');
    };

    this.openProduct = function (product) {
      window.sessionStorage.setItem('moneybags:selectedProductCode', product.productCode);
      return self.router.go({ path: 'product-detail' });
    };

    this.load = function () {
      self.loading(true);
      self.errorMessage('');
      return gatewayApi.listPublicProducts({ status: 'ACTIVE', page: 0, size: 100 })
        .then(function (response) {
          self.products(Array.isArray(response) ? response : (response.content || []));
        })
        .catch(function (error) {
          self.errorMessage(error.message || 'We could not load the product catalogue.');
        })
        .finally(function () { self.loading(false); });
    };

    this.connected = function () {
      document.title = 'Products | MoneyBag';
      return self.load();
    };
  }

  return ProductsViewModel;
});
