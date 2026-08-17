define(['services/api/client'], function (client) {
  'use strict';

  function applications(cifId) {
    return client.request('/api/credit-cards/applications/cif/' + client.encode(cifId));
  }
  function accounts(cifId) {
    return client.request('/api/credit-cards/accounts/cif/' + client.encode(cifId));
  }

  return {
    submitCreditCardApplication: function (body) {
      return client.mutate('/api/credit-cards/applications', 'POST', body, { idempotent: false });
    },
    listCreditCardApplications: applications,
    listCardApplicationsByCif: applications,
    getCardApplication: function (id) { return client.request('/api/credit-cards/applications/' + client.encode(id)); },
    approveCardApplication: function (id) {
      return client.mutate('/api/credit-cards/applications/' + client.encode(id) + '/approve', 'POST', null, { idempotent: false });
    },
    rejectCardApplication: function (id) {
      return client.mutate('/api/credit-cards/applications/' + client.encode(id) + '/reject', 'POST', null, { idempotent: false });
    },
    listCreditCardAccounts: accounts,
    listCardAccountsByCif: accounts,
    getCreditCardAccount: function (id) { return client.request('/api/credit-cards/accounts/' + client.encode(id)); },
    getCardAccount: function (id) { return client.request('/api/credit-cards/accounts/' + client.encode(id)); },
    closeCardAccount: function (id) {
      return client.mutate('/api/credit-cards/accounts/' + client.encode(id) + '/close', 'POST', null, { idempotent: false });
    },
    getCreditCardEodReadiness: function () { return client.request('/api/credit-cards/accounts/eod/readiness'); }
  };
});
