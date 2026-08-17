define(['services/api/client'], function (client) {
  'use strict';
  return {
    listNotifications: function (filters) { return client.request('/api/notifications', { params: filters }); },
    getNotification: function (id) { return client.request('/api/notifications/' + client.encode(id)); }
  };
});
