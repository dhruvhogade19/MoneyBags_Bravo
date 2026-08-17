define([
  'knockout',
  'services/api/gatewayApi',
  'services/auth/session',
  'viewModels/banker/support',
  'css!views/banker/banker'
], function (ko, gatewayApi, session, support) {
  'use strict';

  function KycViewModel() {
    var self = this;
    this.queueState = support.createState();
    this.caseState = support.createState();
    this.accessDenied = ko.pureComputed(function () { return !support.isAdmin(session); });
    this.statusFilter = ko.observable('PENDING');
    this.cifFilter = ko.observable('');
    this.queue = ko.observableArray([]);
    this.selectedCase = ko.observable(null);
    this.documents = ko.observableArray([]);
    this.verificationRemarks = ko.observable('');
    this.rejectionReason = ko.observable('');
    this.date = support.date;
    this.mask = support.mask;
    this.canDecide = ko.pureComputed(function () {
      var item = self.selectedCase();
      return item && item.kycStatus !== 'APPROVED' && item.kycStatus !== 'REJECTED';
    });

    this.loadQueue = function () {
      if (self.accessDenied()) return Promise.resolve();
      var filters = { page: 0, size: 50 };
      if (self.statusFilter() !== 'ALL') filters.statuses = [self.statusFilter()];
      if (String(self.cifFilter() || '').trim()) filters.cifId = Number(self.cifFilter());
      return support.run(self.queueState, function () {
        return gatewayApi.getKycQueue(filters).then(function (page) {
          self.queue(support.content(page));
        });
      }).catch(function () {});
    };

    this.openCase = function (item) {
      self.selectedCase(null);
      self.documents([]);
      self.verificationRemarks('');
      self.rejectionReason('');
      return support.run(self.caseState, function () {
        return Promise.all([
          gatewayApi.getKyc(item.kycId),
          gatewayApi.getKycDocuments(item.kycId)
        ]).then(function (results) {
          self.selectedCase(results[0]);
          self.documents(support.content(results[1]));
        });
      }, 'KYC case loaded.').catch(function () {});
    };

    this.verifyDocument = function (document, status) {
      var current = self.selectedCase();
      if (!current) return Promise.resolve();
      if (status === 'MISMATCH' && !String(self.verificationRemarks() || '').trim()) {
        self.caseState.error('Add reviewer remarks before marking a document as a mismatch.');
        return Promise.resolve();
      }
      return support.run(self.caseState, function () {
        return gatewayApi.verifyKycDocument(current.kycId, document.documentId, {
          status: status,
          remarks: String(self.verificationRemarks() || '').trim() || null
        }).then(function () { return self.openCase(current); });
      }, 'Document verification recorded.').catch(function () {});
    };

    this.decide = function (decision) {
      var current = self.selectedCase();
      if (!current) return Promise.resolve();
      var reason = String(self.rejectionReason() || '').trim();
      if (decision === 'REJECTED' && !reason) {
        self.caseState.error('A rejection reason is required.');
        return Promise.resolve();
      }
      return support.run(self.caseState, function () {
        return gatewayApi.decideKyc(current.kycId, {
          decision: decision,
          rejectionReason: decision === 'REJECTED' ? reason : null
        }).then(function (updated) {
          self.selectedCase(updated);
          return self.loadQueue();
        });
      }, 'Final KYC decision recorded.').catch(function () {});
    };

    this.connected = function () {
      document.title = 'KYC review | MoneyBag';
      self.loadQueue();
    };
  }

  return KycViewModel;
});
