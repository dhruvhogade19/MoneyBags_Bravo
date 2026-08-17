define(['viewModels/customer/pageSupport', 'ojs/ojbutton'], function (support) {
  'use strict';

  function StatementsViewModel(params) {
    var self = support.create(this, params, 'Statements');
    this.goToPayments = function () { return self.navigate('customer-payments'); };
  }

  return StatementsViewModel;
});
