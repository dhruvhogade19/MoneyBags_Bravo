define([
  'knockout',
  'services/api/gatewayApi',
  'services/auth/session',
  'viewModels/banker/support',
  'css!views/banker/banker'
], function (ko, gatewayApi, session, support) {
  'use strict';

  function ProductBenchmarksViewModel(params) {
    var self = this;
    this.state = support.createState();
    this.createState = support.createState();
    this.accessDenied = ko.pureComputed(function () { return !support.isAdmin(session); });
    this.benchmarkCode = ko.observable('REPO');
    this.effectiveOn = ko.observable(support.today());
    this.annualRate = ko.observable('6.5');
    this.effectiveFrom = ko.observable(support.today());
    this.effectiveTo = ko.observable('');
    this.effectiveRate = ko.observable(null);
    this.history = ko.observableArray([]);
    this.navigate = function (path) { return support.navigate(params, path); };

    this.load = function () {
      var code = String(self.benchmarkCode() || '').trim().toUpperCase();
      if (!code) { self.state.error('Enter a benchmark code.'); return Promise.resolve(); }
      return support.run(self.state, function () {
        return Promise.all([
          gatewayApi.getBenchmarkHistory(code),
          gatewayApi.getEffectiveBenchmark(code, self.effectiveOn())
        ]).then(function (results) {
          self.history(support.content(results[0]));
          self.effectiveRate(results[1]);
        });
      }, 'Benchmark history loaded.').catch(function () {});
    };

    this.create = function () {
      var code = String(self.benchmarkCode() || '').trim().toUpperCase();
      return support.run(self.createState, function () {
        return gatewayApi.createBenchmark({
          benchmarkCode: code,
          annualRate: support.number(self.annualRate()),
          effectiveFrom: self.effectiveFrom(),
          effectiveTo: self.effectiveTo() || null,
          createdBy: support.actor(session)
        }).then(function () { return self.load(); });
      }, 'Benchmark rate created.').catch(function () {});
    };

    this.connected = function () { document.title = 'Benchmark rates | MoneyBag'; };
  }

  return ProductBenchmarksViewModel;
});
