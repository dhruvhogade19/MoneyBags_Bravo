define(['services/api/client'], function (client) {
  'use strict';

  return {
    listPublicProducts: function (filters) { return client.publicRequest('/products', { params: filters }); },
    getPublicProduct: function (code) { return client.publicRequest('/products/' + client.encode(code)); },
    listCreditCardProducts: function () {
      return client.publicRequest('/products', { params: { category: 'CREDIT_CARD', status: 'ACTIVE', page: 0, size: 100 } });
    },
    listProducts: function (filters) { return client.request('/api/products', { params: filters }); },
    getProduct: function (code) { return client.request('/api/products/' + client.encode(code)); },
    createProduct: function (body) { return client.mutate('/api/products', 'POST', body, { idempotent: false }); },
    updateProduct: function (code, body) {
      return client.mutate('/api/products/' + client.encode(code), 'PUT', body, { idempotent: false });
    },
    changeProductStatus: function (code, body) {
      return client.mutate('/api/products/' + client.encode(code) + '/status', 'PATCH', body, { idempotent: false });
    },
    getRateQuote: function (code, filters) {
      return client.request('/api/products/' + client.encode(code) + '/rate-quote', { params: filters });
    },
    listInterestPolicies: function (code) {
      return client.request('/api/products/' + client.encode(code) + '/interest-policies');
    },
    addInterestPolicy: function (code, body) {
      return client.mutate('/api/products/' + client.encode(code) + '/interest-policies', 'POST', body, { idempotent: false });
    },
    getBenchmarkHistory: function (code) {
      return client.request('/api/benchmarks/' + client.encode(code) + '/history');
    },
    getEffectiveBenchmark: function (code, effectiveOn) {
      return client.request('/api/benchmarks/' + client.encode(code) + '/effective', { params: { effectiveOn: effectiveOn } });
    },
    createBenchmark: function (body) { return client.mutate('/api/benchmarks', 'POST', body, { idempotent: false }); }
  };
});
