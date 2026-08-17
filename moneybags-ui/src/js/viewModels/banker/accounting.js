define([
  'knockout',
  'services/api/gatewayApi',
  'services/auth/session',
  'viewModels/banker/support',
  'css!views/banker/banker'
], function (ko, gatewayApi, session, support) {
  'use strict';

  function AccountingViewModel() {
    var self = this;
    this.state = support.createState();
    this.createState = support.createState();
    this.accessDenied = ko.pureComputed(function () { return !support.isAdmin(session); });
    this.activeTab = ko.observable('journals');
    this.journals = ko.observableArray([]);
    this.glAccounts = ko.observableArray([]);
    this.rules = ko.observableArray([]);
    this.mappings = ko.observableArray([]);
    this.businessDate = ko.observable('');
    this.sourceService = ko.observable('');
    this.eventType = ko.observable('');
    this.externalReference = ko.observable('');
    this.glCode = ko.observable(''); this.glName = ko.observable(''); this.glType = ko.observable('ASSET');
    this.normalBalance = ko.observable('DEBIT'); this.currency = ko.observable('INR'); this.parentGlCode = ko.observable('');
    this.ruleCode = ko.observable(''); this.ruleEventType = ko.observable('PAYMENT_SETTLED'); this.componentType = ko.observable('PRINCIPAL');
    this.ruleProductCode = ko.observable(''); this.ruleVersion = ko.observable('1'); this.debitMappingCode = ko.observable(''); this.creditMappingCode = ko.observable('');
    this.mappingCode = ko.observable(''); this.mappingProductCode = ko.observable(''); this.mappingGlCode = ko.observable('');
    this.effectiveFrom = ko.observable(support.today()); this.effectiveTo = ko.observable('');
    this.money = support.money;
    this.date = support.date;
    this.totalDebits = ko.pureComputed(function () { return self.journals().reduce(function (sum, item) { return sum + support.number(item.totalDebit); }, 0); });
    this.totalCredits = ko.pureComputed(function () { return self.journals().reduce(function (sum, item) { return sum + support.number(item.totalCredit); }, 0); });

    this.loadJournals = function () {
      var filters = { page: 0, size: 100 };
      if (self.businessDate()) filters.businessDate = self.businessDate();
      if (self.sourceService()) filters.sourceService = String(self.sourceService()).trim();
      if (self.eventType()) filters.eventType = String(self.eventType()).trim();
      if (self.externalReference()) filters.externalReference = String(self.externalReference()).trim();
      return support.run(self.state, function () {
        return gatewayApi.listJournals(filters).then(function (page) { self.journals(support.content(page)); });
      }).catch(function () {});
    };

    this.loadGl = function () { return support.run(self.state, function () { return gatewayApi.listGlAccounts(0, 200).then(function (page) { self.glAccounts(support.content(page)); }); }, null).catch(function () {}); };
    this.loadRules = function () { return support.run(self.state, function () { return gatewayApi.listAccountingRules(0, 200).then(function (page) { self.rules(support.content(page)); }); }, null).catch(function () {}); };
    this.loadMappings = function () { return support.run(self.state, function () { return gatewayApi.listSubledgerMappings(0, 200).then(function (page) { self.mappings(support.content(page)); }); }, null).catch(function () {}); };

    this.showTab = function (tab) {
      self.activeTab(tab);
      if (tab === 'gl') self.loadGl();
      if (tab === 'rules') self.loadRules();
      if (tab === 'mappings') self.loadMappings();
    };

    this.createGl = function () {
      return support.run(self.createState, function () {
        return gatewayApi.createGlAccount({
          glCode: String(self.glCode()).trim().toUpperCase(), name: String(self.glName()).trim(),
          accountType: self.glType(), normalBalance: self.normalBalance(),
          currencyCode: String(self.currency()).trim().toUpperCase(), parentGlCode: String(self.parentGlCode() || '').trim() || null
        }, support.actor(session)).then(function () { return self.loadGl(); });
      }, 'GL account created.').catch(function () {});
    };

    this.createRule = function () {
      return support.run(self.createState, function () {
        return gatewayApi.createAccountingRule({
          ruleCode: String(self.ruleCode()).trim().toUpperCase(), eventType: String(self.ruleEventType()).trim().toUpperCase(),
          componentType: String(self.componentType()).trim().toUpperCase(), productCode: String(self.ruleProductCode() || '').trim().toUpperCase() || null,
          currencyCode: String(self.currency()).trim().toUpperCase(), version: support.number(self.ruleVersion(), 1),
          debitMappingCode: String(self.debitMappingCode()).trim().toUpperCase(), creditMappingCode: String(self.creditMappingCode()).trim().toUpperCase(),
          effectiveFrom: self.effectiveFrom(), effectiveTo: self.effectiveTo() || null
        }, support.actor(session)).then(function () { return self.loadRules(); });
      }, 'Accounting rule created.').catch(function () {});
    };

    this.createMapping = function () {
      return support.run(self.createState, function () {
        return gatewayApi.createSubledgerMapping({
          mappingCode: String(self.mappingCode()).trim().toUpperCase(),
          productCode: String(self.mappingProductCode() || '').trim().toUpperCase() || null,
          glCode: String(self.mappingGlCode()).trim().toUpperCase(),
          currencyCode: String(self.currency()).trim().toUpperCase(),
          effectiveFrom: self.effectiveFrom(), effectiveTo: self.effectiveTo() || null
        }, support.actor(session)).then(function () { return self.loadMappings(); });
      }, 'Subledger mapping created.').catch(function () {});
    };

    this.connected = function () {
      document.title = 'Accounting controls | MoneyBag';
      if (!self.accessDenied()) self.loadJournals();
    };
  }

  return AccountingViewModel;
});
