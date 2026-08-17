define([
  'knockout',
  'services/api/gatewayApi',
  'viewModels/customer/pageSupport',
  'ojs/ojbutton',
  'ojs/ojfilepicker',
  'ojs/ojprogress-circle'
], function (ko, gatewayApi, support) {
  'use strict';

  function KycViewModel(params) {
    var self = support.create(this, params, 'KYC and verification');
    this.cases = ko.observableArray([]);
    this.currentCase = ko.observable(null);
    this.documents = ko.observableArray([]);
    this.requiredDocuments = [
      { code: 'PAN', label: 'PAN card' },
      { code: 'AADHAAR', label: 'Aadhaar card' },
      { code: 'ADDRESS_PROOF', label: 'Address proof' },
      { code: 'SALARY_PROOF', label: 'Salary proof' }
    ];
    this.documentType = ko.observable('PAN');
    this.selectedFile = ko.observable(null);
    this.dateTime = support.dateTime; this.label = support.label; this.statusClass = support.statusClass;

    this.fileName = ko.pureComputed(function () { return self.selectedFile() ? self.selectedFile().name : 'No file selected'; });
    this.isUploaded = function (documentType) {
      return self.documents().some(function (document) { return document.documentType === documentType; });
    };
    this.missingDocumentCount = ko.pureComputed(function () {
      return self.requiredDocuments.filter(function (document) { return !self.isUploaded(document.code); }).length;
    });
    this.documentProgress = ko.pureComputed(function () {
      return (self.requiredDocuments.length - self.missingDocumentCount()) + ' of ' + self.requiredDocuments.length + ' required documents uploaded';
    });
    this.canUpload = ko.pureComputed(function () {
      var item = self.currentCase();
      return item && ['APPROVED', 'REJECTED'].indexOf(item.kycStatus) === -1 && self.selectedFile() && !self.submitting();
    });

    this.selectFiles = function (event) {
      var files = event.detail.files;
      self.selectedFile(files && files.length ? files[0] : null);
    };

    this.loadDocuments = function () {
      var item = self.currentCase();
      if (!item) { self.documents([]); return Promise.resolve(); }
      return gatewayApi.listKycDocuments(item.kycId).then(function (documents) { self.documents(support.asArray(documents)); });
    };

    this.load = function () {
      var id = self.requireCustomerId(); if (!id) return Promise.resolve();
      self.loading(true); self.errorMessage('');
      return gatewayApi.listKycCases(Number(id)).then(function (cases) {
        var values = support.asArray(cases);
        self.cases(values); self.currentCase(values.length ? values[0] : null);
        return self.loadDocuments();
      }).catch(self.fail).finally(function () { self.loading(false); });
    };

    this.upload = function () {
      if (!self.canUpload()) return;
      self.submitting(true); self.clearMessages();
      return gatewayApi.uploadKycDocuments(self.currentCase().kycId, [{ documentType: self.documentType(), file: self.selectedFile() }])
        .then(function (documents) {
          self.documents(support.asArray(documents)); self.selectedFile(null); self.successMessage('Document uploaded for review.');
        }).catch(self.fail).finally(function () { self.submitting(false); });
    };

    var baseConnected = this.connected;
    this.connected = function () { baseConnected(); return self.load(); };
  }

  return KycViewModel;
});
