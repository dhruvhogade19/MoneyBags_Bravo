define(['knockout'], function (ko) {
  'use strict';

  function unwrap(value) {
    return ko.unwrap(value);
  }

  function sessionUser(session) {
    if (!session) return {};
    if (typeof session.currentUser === 'function') return unwrap(session.currentUser) || {};
    if (typeof session.user === 'function') return unwrap(session.user) || {};
    return unwrap(session.user) || {};
  }

  function isAdmin(session) {
    if (session && typeof session.hasRole === 'function') {
      return Boolean(session.hasRole('BANK_ADMIN'));
    }
    var user = sessionUser(session);
    var roles = unwrap(user.roles || user.role || []);
    if (typeof roles === 'string') roles = roles.split(/[ ,]+/);
    return Array.isArray(roles) && roles.indexOf('BANK_ADMIN') >= 0;
  }

  function createState() {
    return {
      loading: ko.observable(false),
      error: ko.observable(''),
      success: ko.observable('')
    };
  }

  function errorMessage(error) {
    if (!error) return 'The operation could not be completed.';
    var body = error.body || error.data || error.problem || {};
    return body.detail || body.message || body.title || error.message || 'The operation could not be completed.';
  }

  function run(state, work, successMessage) {
    state.loading(true);
    state.error('');
    state.success('');
    return Promise.resolve()
      .then(work)
      .then(function (result) {
        if (successMessage) {
          state.success(typeof successMessage === 'function' ? successMessage(result) : successMessage);
        }
        return result;
      })
      .catch(function (error) {
        state.error(errorMessage(error));
        throw error;
      })
      .finally(function () {
        state.loading(false);
      });
  }

  function content(value) {
    if (!value) return [];
    if (Array.isArray(value)) return value;
    return Array.isArray(value.content) ? value.content : [];
  }

  function number(value, fallback) {
    var parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : (fallback === undefined ? 0 : fallback);
  }

  function money(value, currency) {
    if (value === null || value === undefined || value === '') return '—';
    try {
      return new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: currency || 'INR',
        maximumFractionDigits: 2
      }).format(number(value));
    } catch (ignore) {
      return (currency || 'INR') + ' ' + number(value).toFixed(2);
    }
  }

  function date(value) {
    if (!value) return '—';
    var parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? String(value) : parsed.toLocaleString('en-IN');
  }

  function today() {
    var now = new Date();
    var offset = now.getTimezoneOffset();
    return new Date(now.getTime() - offset * 60000).toISOString().slice(0, 10);
  }

  function mask(value, visible) {
    var text = value === null || value === undefined ? '' : String(value);
    var keep = visible || 4;
    if (text.length <= keep) return text || '—';
    return '•'.repeat(Math.min(8, text.length - keep)) + text.slice(-keep);
  }

  function navigate(params, path) {
    if (params && params.router && typeof params.router.go === 'function') {
      return params.router.go({ path: path });
    }
    return Promise.resolve();
  }

  function actor(session) {
    var user = sessionUser(session);
    return user.username || user.userName || user.sub || user.id || 'bank-admin';
  }

  function idempotencyKey(prefix) {
    var suffix = (window.crypto && window.crypto.randomUUID) ? window.crypto.randomUUID() :
      Date.now().toString(36) + '-' + Math.random().toString(36).slice(2);
    return prefix + '-' + suffix;
  }

  return {
    actor: actor,
    content: content,
    createState: createState,
    date: date,
    errorMessage: errorMessage,
    idempotencyKey: idempotencyKey,
    isAdmin: isAdmin,
    mask: mask,
    money: money,
    navigate: navigate,
    number: number,
    run: run,
    sessionUser: sessionUser,
    today: today,
    unwrap: unwrap
  };
});
