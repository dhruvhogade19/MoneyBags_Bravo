define([], function () {
  'use strict';

  function PlaceholderViewModel(params) {
    var details = params.routerState.detail || {};
    var router = params.router;

    this.eyebrow = details.eyebrow || 'MoneyBag';
    this.title = details.title || 'A thoughtful page is on its way';
    this.description = details.description || 'This workspace is ready for its feature implementation.';
    this.nextLabel = details.nextLabel || 'Back to home';
    this.nextPath = details.nextPath || 'landing';
    this.routeName = params.routerState.path;

    this.navigate = function (path) {
      return router.go({ path: path });
    };

    this.connected = function () {
      document.title = this.title + ' | MoneyBag';
    }.bind(this);
  }

  return PlaceholderViewModel;
});
