define(['services/api/client'], function (client) {
  'use strict';

  function paymentFilters(first, page, size) {
    if (first && typeof first === 'object') return first;
    return { customerId: first, page: page === undefined ? 0 : page, size: size === undefined ? 20 : size };
  }

  return {
    listPayments: function (first, page, size) {
      return client.request('/api/v1/payments', { params: paymentFilters(first, page, size) });
    },
    getPayment: function (id) { return client.request('/api/v1/payments/' + client.encode(id)); },
    createBookTransfer: function (body) {
      return client.mutate('/api/v1/payments/book-transfers', 'POST', body, { idempotencyPrefix: 'book-transfer' });
    },
    createMerchantPayment: function (body) {
      return client.mutate('/api/v1/payments/credit-card-payment/merchant-payment', 'POST', body, { idempotencyPrefix: 'merchant-payment' });
    },
    createCardRepayment: function (body) {
      return client.mutate('/api/v1/payments/credit-card-payment/repayment', 'POST', body, { idempotencyPrefix: 'card-repayment' });
    },
    fundFixedDeposit: function (body) {
      return client.mutate('/api/v1/payments/fixed-deposit-funding', 'POST', body, { idempotencyPrefix: 'fd-funding' });
    },
    cancelPayment: function (id) {
      return client.mutate('/api/v1/payments/' + client.encode(id) + '/cancel', 'POST', null, { idempotent: false });
    },
    listBills: function (filters) {
      var safe = Object.assign({}, filters || {});
      delete safe.cifId;
      return client.request('/api/v1/bills', { params: safe });
    },
    getBill: function (id) { return client.request('/api/v1/bills/' + client.encode(id)); }
  };
});
