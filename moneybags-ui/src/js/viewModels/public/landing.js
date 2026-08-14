define([], function () {
  'use strict';

  function LandingViewModel(params) {
    var router = params.router;

    this.navigate = function (path) {
      return router.go({ path: path });
    };

    this.connected = function () {
      document.title = 'MoneyBag | Banking, thoughtfully organised';
    };
  }

  return LandingViewModel;
});
