define(['services/api/client'], function (client) {
  'use strict';
  return {
    getIdentityUser: function (id) { return client.request('/api/v1/identity/users/' + client.encode(id)); },
    createIdentityUser: function (body) {
      return client.mutate('/api/v1/identity/users', 'POST', body, { idempotent: false });
    }
  };
});
