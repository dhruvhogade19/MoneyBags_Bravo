define([
  'services/api/customerApi',
  'services/api/productApi',
  'services/api/depositApi',
  'services/api/cardApi',
  'services/api/paymentApi',
  'services/api/notificationApi',
  'services/api/accountingApi',
  'services/api/identityApi'
], function (customer, products, deposits, cards, payments, notifications, accounting, identity) {
  'use strict';
  return Object.assign({}, customer, products, deposits, cards, payments, notifications, accounting, identity);
});
