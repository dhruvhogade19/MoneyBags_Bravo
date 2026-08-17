define([
  'knockout',
  'services/auth/session',
  'ojs/ojbutton',
  'ojs/ojinputtext',
  'css!views/public/public'
], function (ko, session) {
  'use strict';

  function RegisterViewModel() {
    var self = this;
    this.submitting = ko.observable(false);
    this.errorMessage = ko.observable('');
    this.success = ko.observable(null);
    this.email = ko.observable('');
    this.password = ko.observable('');
    this.confirmPassword = ko.observable('');

    this.submit = function () {
      self.errorMessage('');
      if (!self.email().trim() || self.password().length < 12 || self.password() !== self.confirmPassword()) {
        self.errorMessage('Enter a valid email, a password of at least 12 characters, and matching confirmation.');
        return;
      }
      self.submitting(true);
      return session.register({ email: self.email().trim().toLowerCase(), password: self.password() })
        .then(function (result) { self.success(result || { status: 'PENDING_PROFILE' }); })
        .catch(function (error) { self.errorMessage(error.message || 'Registration could not be completed.'); })
        .finally(function () { self.submitting(false); });
    };

    this.signIn = function () { return session.signIn('customer-profile'); };
    this.connected = function () { document.title = 'Create your sign-in | MoneyBag'; };
  }

  return RegisterViewModel;
});
