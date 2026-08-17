define([
  'knockout',
  'services/api/gatewayApi',
  'services/auth/session',
  'viewModels/customer/pageSupport',
  'ojs/ojbutton',
  'ojs/ojinputtext',
  'ojs/ojinputnumber',
  'ojs/ojdatetimepicker',
  'ojs/ojprogress-circle'
], function (ko, gatewayApi, session, support) {
  'use strict';

  function ProfileViewModel(params) {
    var self = support.create(this, params, 'Profile and personal details');
    this.profile = ko.observable(null);
    this.editing = ko.observable(false);
    this.onboarding = ko.observable(false);
    this.identityRefreshRequired = ko.observable(false);
    this.firstName = ko.observable(''); this.lastName = ko.observable(''); this.dob = ko.observable('');
    this.number = ko.observable('');
    this.address = ko.observable(''); this.employmentType = ko.observable('SALARIED'); this.salary = ko.observable(null);
    this.panNumber = ko.observable(''); this.aadhaarNumber = ko.observable('');
    this.date = support.date; this.money = support.money; this.label = support.label; this.mask = support.mask;
    this.statusClass = support.statusClass;

    function copy(profile) {
      self.firstName(profile.firstName || ''); self.lastName(profile.lastName || ''); self.dob(profile.dob || '');
      self.number(profile.number || ''); self.address(profile.address || '');
      self.employmentType(profile.employmentType || 'SALARIED'); self.salary(profile.salary);
    }

    this.load = function () {
      var id = self.customerId();
      if (!id) {
        self.onboarding(true); self.editing(true); self.loading(false); self.errorMessage('');
        return Promise.resolve();
      }
      self.loading(true); self.errorMessage('');
      return gatewayApi.getCustomerProfile(Number(id)).then(function (profile) { self.profile(profile); copy(profile); })
        .catch(self.fail).finally(function () { self.loading(false); });
    };

    this.startEdit = function () { self.clearMessages(); copy(self.profile()); self.editing(true); };
    this.cancelEdit = function () { self.editing(false); self.clearMessages(); };

    this.validateProfile = function (payload) {
      if (!payload.firstName || !payload.lastName) return 'Enter your first and last name.';
      if (!payload.dob || Number.isNaN(new Date(payload.dob).getTime()) || new Date(payload.dob) >= new Date()) {
        return 'Enter a valid date of birth in the past.';
      }
      if (!/^\d{10,15}$/.test(payload.number)) return 'Mobile number must contain 10 to 15 digits.';
      if (!payload.address) return 'Enter your current address.';
      if (self.onboarding() && !/^[A-Z]{5}\d{4}[A-Z]$/.test(payload.panNumber)) {
        return 'Enter a valid PAN, for example ABCDE1234F.';
      }
      if (self.onboarding() && !/^\d{12}$/.test(payload.aadhaarNumber)) {
        return 'Aadhaar number must contain exactly 12 digits.';
      }
      return null;
    };

    this.save = function () {
      var id = self.customerId(); var existing = self.profile(); self.submitting(true); self.clearMessages();
      var payload = {
        firstName: self.firstName().trim(), lastName: self.lastName().trim(), dob: self.dob(),
        number: self.number().trim(), address: self.address().trim(),
        employmentType: self.employmentType(), salary: self.salary(),
        panNumber: self.onboarding() ? self.panNumber().trim().toUpperCase() : existing.panNumber,
        aadhaarNumber: self.onboarding() ? self.aadhaarNumber().trim() : existing.aadhaarNumber
      };
      var validationError = self.validateProfile(payload);
      if (validationError) {
        self.submitting(false);
        self.errorMessage(validationError);
        return Promise.resolve();
      }
      var request = self.onboarding() ? gatewayApi.createCustomerProfile(payload) : gatewayApi.updateCustomerProfile(Number(id), payload);
      return request.then(function (profile) {
        self.profile(profile); copy(profile); self.editing(false); self.successMessage('Your profile has been updated.');
        if (self.onboarding()) {
          self.onboarding(false); self.identityRefreshRequired(true);
          self.successMessage('Your customer profile was created and KYC has started. Sign out and sign in again to refresh your customer access.');
        }
      }).catch(self.fail).finally(function () { self.submitting(false); });
    };

    this.refreshIdentity = function () { return session.signOut(); };

    var baseConnected = this.connected;
    this.connected = function () { baseConnected(); return self.load(); };
  }

  return ProfileViewModel;
});
