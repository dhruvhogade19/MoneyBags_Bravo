define(['services/api/client'], function (client) {
  'use strict';

  function listDepositAccounts(filters) { return client.request('/api/deposit-accounts', { params: filters }); }

  return {
    listDepositAccounts: listDepositAccounts,
    searchDepositAccounts: listDepositAccounts,
    getDepositAccount: function (accountId) {
      return client.request('/api/deposit-accounts/' + client.encode(accountId));
    },
    checkDepositEligibility: function (body) {
      return client.mutate('/api/deposit-accounts/eligibility-check', 'POST', body, { idempotent: false });
    },
    openDepositAccount: function (body) {
      return client.mutate('/api/deposit-accounts', 'POST', body, { idempotencyPrefix: 'open-account' });
    },
    commandDepositAccount: function (accountId, command, body, version) {
      var headers = {};
      if (version !== undefined && version !== null) headers['If-Match'] = String(version);
      return client.mutate('/api/deposit-accounts/' + client.encode(accountId) + '/commands/' + client.encode(command), 'POST', body, {
        headers: headers,
        idempotencyPrefix: 'account-command'
      });
    },
    listFixedDeposits: function (filters) {
      return client.request('/api/deposit-accounts/fixed-deposits', { params: filters });
    },
    getFixedDeposit: function (fixedDepositId) {
      return client.request('/api/deposit-accounts/fixed-deposits/' + client.encode(fixedDepositId));
    },
    quoteFixedDeposit: function (body) {
      return client.mutate('/api/deposit-accounts/fixed-deposits/quotes', 'POST', body, { idempotent: false });
    },
    bookFixedDeposit: function (body) {
      return client.mutate('/api/deposit-accounts/fixed-deposits', 'POST', body, { idempotencyPrefix: 'book-fd' });
    }
  };
});
