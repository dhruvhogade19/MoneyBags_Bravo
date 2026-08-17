define(['services/api/client'], function (client) {
  'use strict';

  function actorOptions(options) {
    var settings = Object.assign({}, options || {});
    settings.headers = Object.assign({}, settings.headers || {}, { 'X-Actor-Id': settings.actor || 'bank-admin' });
    delete settings.actor;
    return settings;
  }

  return {
    listJournals: function (filters) { return client.request('/api/v1/journals', { params: filters }); },
    getJournal: function (number) { return client.request('/api/v1/journals/' + client.encode(number)); },
    listGlAccounts: function (page, size) { return client.request('/api/v1/gl-accounts', { params: { page: page || 0, size: size || 50 } }); },
    createGlAccount: function (body, actor) { return client.mutate('/api/v1/gl-accounts', 'POST', body, actorOptions({ actor: actor, idempotencyPrefix: 'gl-account' })); },
    listAccountingRules: function (page, size) { return client.request('/api/v1/accounting-rules', { params: { page: page || 0, size: size || 50 } }); },
    createAccountingRule: function (body, actor) { return client.mutate('/api/v1/accounting-rules', 'POST', body, actorOptions({ actor: actor, idempotencyPrefix: 'accounting-rule' })); },
    listSubledgerMappings: function (page, size) { return client.request('/api/v1/subledger-mappings', { params: { page: page || 0, size: size || 50 } }); },
    createSubledgerMapping: function (body, actor) { return client.mutate('/api/v1/subledger-mappings', 'POST', body, actorOptions({ actor: actor, idempotencyPrefix: 'subledger-map' })); },
    getAccountingPeriod: function (date) { return client.request('/api/v1/accounting-periods/' + client.encode(date)); },
    getTrialBalance: function (runId) { return client.request('/api/v1/trial-balances/' + client.encode(runId)); },
    getReconciliation: function (runId) { return client.request('/api/v1/reconciliation/runs/' + client.encode(runId)); }
  };
});
