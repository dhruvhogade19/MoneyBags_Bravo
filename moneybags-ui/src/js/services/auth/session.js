define(['knockout', 'services/api/http'], function (ko, http) {
  'use strict';

  var currentUser = ko.observable(null);
  var loading = ko.observable(true);
  var loginLinks = {
    customer: '/oauth2/authorization/moneybags-consumer',
    admin: '/oauth2/authorization/moneybags-admin'
  };

  function normalize(payload) {
    if (!payload || payload.authenticated === false) return null;
    var roles = payload.roles || [];
    if (typeof roles === 'string') roles = roles.split(/[ ,]+/).filter(Boolean);
    return Object.assign({}, payload, {
      authenticated: true,
      roles: roles,
      customerId: payload.customerId || payload.cifId || null,
      cifId: payload.cifId || payload.customerId || null,
      onboardingStatus: payload.onboardingStatus || (payload.customerId ? 'PENDING_KYC' : 'PENDING_PROFILE')
    });
  }

  function load() {
    loading(true);
    return Promise.all([
      http.request('/api/session').catch(function (error) {
        if (error.status === 401) return { authenticated: false };
        throw error;
      }),
      http.request('/api/session/login-links').catch(function () { return null; })
    ]).then(function (results) {
      var user = normalize(results[0]);
      currentUser(user);
      if (results[1]) {
        loginLinks.customer = results[1].customer || results[1].consumer || loginLinks.customer;
        loginLinks.admin = results[1].admin || results[1].banker || loginLinks.admin;
      }
      if (!user || !user.customerId || user.roles.indexOf('BANK_ADMIN') >= 0) return user;
      return http.request('/api/proxy/api/v1/cifs/' + encodeURIComponent(user.customerId))
        .then(function (profile) {
          var kycStatus = String(profile.kycStatus || 'PENDING').toUpperCase();
          var enriched = Object.assign({}, user, {
            firstName: profile.firstName || user.firstName,
            kycStatus: kycStatus,
            onboardingStatus: kycStatus === 'APPROVED' ? 'APPROVED' :
              (kycStatus === 'REJECTED' ? 'REJECTED' : 'PENDING_KYC')
          });
          currentUser(enriched);
          return enriched;
        }).catch(function () {
          currentUser(Object.assign({}, user, { kycStatus: 'UNKNOWN', onboardingStatus: 'PENDING_KYC' }));
          return currentUser();
        });
    }).finally(function () { loading(false); });
  }

  var readyPromise = load();

  function ready() { return readyPromise; }
  function getUser() { return currentUser(); }
  function isAuthenticated() { return Boolean(currentUser()); }
  function hasRole(role) {
    var user = currentUser();
    return Boolean(user && user.roles && user.roles.indexOf(role) >= 0);
  }
  function isCustomer() { return hasRole('CONSUMER') && !hasRole('BANK_ADMIN'); }
  function isAdmin() { return hasRole('BANK_ADMIN'); }
  function isApproved() {
    var user = currentUser();
    return Boolean(user && (hasRole('BANK_ADMIN') || user.onboardingStatus === 'APPROVED'));
  }

  function signIn(roleOrReturnPath, returnPath) {
    var role = returnPath === undefined ? 'customer' : roleOrReturnPath;
    var target = returnPath === undefined ? roleOrReturnPath : returnPath;
    var normalizedRole = /admin|banker/i.test(role || '') ? 'admin' : 'customer';
    if (target) window.sessionStorage.setItem('moneybags:returnPath', target);
    window.location.assign(loginLinks[normalizedRole]);
    return Promise.resolve();
  }

  function signOut() {
    var user = currentUser();
    var form = document.createElement('form');
    form.method = 'post';
    form.action = '/api/session/logout';
    form.hidden = true;
    if (user && user.csrf && user.csrf.token) {
      var token = document.createElement('input');
      token.type = 'hidden';
      token.name = user.csrf.parameterName || '_csrf';
      token.value = user.csrf.token;
      form.appendChild(token);
    }
    document.body.appendChild(form);
    currentUser(null);
    form.submit();
    return Promise.resolve();
  }

  function register(credentials) {
    var email = credentials.email || credentials.username;
    return http.request('/api/registration', {
      method: 'POST',
      body: { email: email, password: credentials.password }
    });
  }

  function consumeReturnPath() {
    var path = window.sessionStorage.getItem('moneybags:returnPath');
    if (path) window.sessionStorage.removeItem('moneybags:returnPath');
    return path;
  }

  return {
    consumeReturnPath: consumeReturnPath,
    currentUser: currentUser,
    getUser: getUser,
    hasRole: hasRole,
    isAdmin: isAdmin,
    isApproved: isApproved,
    isAuthenticated: isAuthenticated,
    isCustomer: isCustomer,
    loading: loading,
    ready: ready,
    refreshProfile: load,
    register: register,
    signIn: signIn,
    signOut: signOut,
    user: currentUser
  };
});
