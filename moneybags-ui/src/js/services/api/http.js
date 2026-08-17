define([], function () {
  'use strict';

  function cookie(name) {
    var prefix = encodeURIComponent(name) + '=';
    var values = document.cookie ? document.cookie.split(';') : [];
    for (var i = 0; i < values.length; i += 1) {
      var value = values[i].trim();
      if (value.indexOf(prefix) === 0) return decodeURIComponent(value.slice(prefix.length));
    }
    return '';
  }

  function isBodyObject(value) {
    return value !== null && typeof value === 'object' &&
      !(value instanceof FormData) && !(value instanceof Blob) && !(value instanceof URLSearchParams);
  }

  function problemMessage(response, body) {
    if (body && typeof body === 'object') {
      var validationErrors = body.validationErrors;
      if (validationErrors && typeof validationErrors === 'object') {
        var messages = Object.keys(validationErrors).map(function (field) {
          return field + ': ' + validationErrors[field];
        });
        if (messages.length) return messages.join(' ');
      }
      return body.detail || body.message || body.title || body.error || ('Request failed (' + response.status + ')');
    }
    return (typeof body === 'string' && body.trim()) || ('Request failed (' + response.status + ')');
  }

  function request(url, options) {
    var settings = Object.assign({ credentials: 'same-origin' }, options || {});
    settings.method = (settings.method || 'GET').toUpperCase();
    settings.headers = new Headers(settings.headers || {});
    if (!settings.headers.has('Accept')) settings.headers.set('Accept', 'application/json');

    if (isBodyObject(settings.body)) {
      settings.headers.set('Content-Type', 'application/json');
      settings.body = JSON.stringify(settings.body);
    }

    if (!/^(GET|HEAD|OPTIONS)$/.test(settings.method)) {
      var csrf = cookie('XSRF-TOKEN');
      if (csrf) settings.headers.set('X-XSRF-TOKEN', csrf);
    }

    return fetch(url, settings).then(function (response) {
      if (response.status === 204) return response.ok ? null : Promise.reject(new Error('Request failed (' + response.status + ')'));
      var contentType = response.headers.get('content-type') || '';
      var parsed = contentType.indexOf('application/json') >= 0 ? response.json() : response.text();
      return parsed.catch(function () { return null; }).then(function (body) {
        if (response.ok) return body;
        var error = new Error(problemMessage(response, body));
        error.status = response.status;
        error.body = body;
        error.problem = body;
        throw error;
      });
    });
  }

  function query(parameters) {
    var values = new URLSearchParams();
    Object.keys(parameters || {}).forEach(function (key) {
      var value = parameters[key];
      if (value === undefined || value === null || value === '') return;
      if (Array.isArray(value)) value.forEach(function (item) { values.append(key, item); });
      else values.append(key, value);
    });
    var encoded = values.toString();
    return encoded ? '?' + encoded : '';
  }

  return { cookie: cookie, query: query, request: request };
});
