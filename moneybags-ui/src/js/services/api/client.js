define(['services/api/http'], function (http) {
  'use strict';

  function idempotencyKey(prefix) {
    var suffix = window.crypto && window.crypto.randomUUID ? window.crypto.randomUUID() :
      Date.now().toString(36) + '-' + Math.random().toString(36).slice(2);
    return (prefix || 'ui') + '-' + suffix;
  }

  function encode(value) { return encodeURIComponent(String(value)); }

  function request(path, options) {
    var settings = Object.assign({}, options || {});
    var params = settings.params;
    delete settings.params;
    return http.request('/api/proxy' + path + http.query(params), settings);
  }

  function publicRequest(path, options) {
    var settings = Object.assign({}, options || {});
    var params = settings.params;
    delete settings.params;
    return http.request('/api/public' + path + http.query(params), settings);
  }

  function mutate(path, method, body, options) {
    var settings = Object.assign({}, options || {}, { method: method, body: body });
    settings.headers = Object.assign({}, settings.headers || {});
    if (settings.idempotent !== false && !settings.headers['Idempotency-Key']) {
      settings.headers['Idempotency-Key'] = idempotencyKey(settings.idempotencyPrefix);
    }
    delete settings.idempotent;
    delete settings.idempotencyPrefix;
    return request(path, settings);
  }

  return {
    encode: encode,
    idempotencyKey: idempotencyKey,
    mutate: mutate,
    publicRequest: publicRequest,
    request: request
  };
});
