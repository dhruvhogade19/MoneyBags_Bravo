/**
 * Root bootstrap for the MoneyBag JavaScript/MVVM application.
 */
require([
  'ojs/ojbootstrap',
  'ojs/ojcontext',
  'knockout',
  './viewModels/appController',
  'ojs/ojknockout',
  'ojs/ojmodule-element'
], function (Bootstrap, Context, ko, appController) {
  Bootstrap.whenDocumentReady().then(function () {
    function init() {
      var globalBody = document.getElementById('globalBody');
      if (!globalBody) {
        throw new Error('The MoneyBag application shell (#globalBody) is missing.');
      }

      ko.applyBindings(appController, globalBody);
    }

    if (document.body.classList.contains('oj-hybrid')) {
      document.addEventListener('deviceready', init);
    } else {
      init();
    }

    Context.getPageContext().getBusyContext().applicationBootstrapComplete();
  });
});
