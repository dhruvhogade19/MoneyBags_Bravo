define([
  'knockout',
  'services/api/gatewayApi',
  'viewModels/customer/pageSupport',
  'ojs/ojbutton',
  'ojs/ojprogress-circle'
], function (ko, gatewayApi, support) {
  'use strict';

  function NotificationsViewModel(params) {
    var self = support.create(this, params, 'Notifications');
    this.notifications = ko.observableArray([]);
    this.selectedNotification = ko.observable(null);
    this.filter = ko.observable('ALL');
    this.dateTime = support.dateTime; this.label = support.label; this.statusClass = support.statusClass;

    this.visibleNotifications = ko.pureComputed(function () {
      return self.notifications().filter(function (item) { return self.filter() === 'ALL' || item.status === self.filter(); });
    });

    this.load = function () {
      var id = self.requireCustomerId(); if (!id) return Promise.resolve(); self.loading(true); self.errorMessage('');
      return gatewayApi.listNotifications({ cifId: Number(id), page: 0, size: 100 })
        .then(function (response) { self.notifications(support.asArray(response)); })
        .catch(self.fail).finally(function () { self.loading(false); });
    };

    this.openNotification = function (summary) {
      self.loading(true); self.clearMessages();
      return gatewayApi.getNotification(summary.notificationId).then(function (notification) { self.selectedNotification(notification); })
        .catch(self.fail).finally(function () { self.loading(false); });
    };
    this.closeNotification = function () { self.selectedNotification(null); };

    var baseConnected = this.connected; this.connected = function () { baseConnected(); return self.load(); };
  }

  return NotificationsViewModel;
});
