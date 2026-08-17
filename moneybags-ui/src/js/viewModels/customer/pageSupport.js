define(['knockout', 'services/auth/session', 'css!views/customer/customer'], function (ko, session) {
  'use strict';

  function messageFrom(error) {
    if (!error) return 'Something went wrong. Please try again.';
    if (typeof error === 'string') return error;
    if (error.problem && error.problem.message) return error.problem.message;
    if (error.body && error.body.message) return error.body.message;
    return error.message || 'Something went wrong. Please try again.';
  }

  function asArray(value) {
    if (Array.isArray(value)) return value;
    return value && Array.isArray(value.content) ? value.content : [];
  }

  function user() {
    return typeof session.getUser === 'function' ? session.getUser() : null;
  }

  function customerId() {
    var current = user();
    return current && (current.cifId || current.customerId);
  }

  function create(viewModel, params, title) {
    viewModel.router = params.router;
    viewModel.loading = ko.observable(false);
    viewModel.submitting = ko.observable(false);
    viewModel.errorMessage = ko.observable('');
    viewModel.successMessage = ko.observable('');
    viewModel.currentUser = ko.pureComputed(user);
    viewModel.customerId = ko.pureComputed(customerId);

    viewModel.navigate = function (path) {
      return viewModel.router.go({ path: path });
    };

    viewModel.clearMessages = function () {
      viewModel.errorMessage('');
      viewModel.successMessage('');
    };

    viewModel.fail = function (error) {
      viewModel.errorMessage(messageFrom(error));
    };

    viewModel.requireCustomerId = function () {
      var id = customerId();
      if (!id) {
        viewModel.errorMessage('Your sign-in is not linked to a customer profile yet. Complete onboarding or contact MoneyBag support.');
      }
      return id;
    };

    viewModel.connected = function () {
      document.title = title + ' | MoneyBag';
    };

    return viewModel;
  }

  function money(value, currency) {
    var amount = Number(value);
    if (!Number.isFinite(amount)) return '-';
    try {
      return new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: currency || 'INR',
        maximumFractionDigits: 2
      }).format(amount);
    } catch (ignore) {
      return amount.toFixed(2) + ' ' + (currency || 'INR');
    }
  }

  function date(value) {
    if (!value) return '-';
    var parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' }).format(parsed);
  }

  function dateTime(value) {
    if (!value) return '-';
    var parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat('en-IN', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(parsed);
  }

  function label(value) {
    return value ? String(value).replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, function (letter) {
      return letter.toUpperCase();
    }) : '-';
  }

  function statusClass(value) {
    var normalized = String(value || '').toLowerCase();
    if (/active|approved|eligible|settled|sent|verified|paid/.test(normalized)) return 'is-success';
    if (/reject|fail|block|cancel|mismatch|closed/.test(normalized)) return 'is-danger';
    if (/pending|review|flag|due|processing/.test(normalized)) return 'is-warning';
    return 'is-neutral';
  }

  function mask(value, visible) {
    var text = String(value || '');
    var keep = visible || 4;
    if (!text) return '-';
    return text.length <= keep ? text : new Array(text.length - keep + 1).join('*') + text.slice(-keep);
  }

  return {
    asArray: asArray,
    create: create,
    date: date,
    dateTime: dateTime,
    label: label,
    mask: mask,
    messageFrom: messageFrom,
    money: money,
    statusClass: statusClass
  };
});
