define([
  'knockout',
  'ojs/ojcorerouter',
  'ojs/ojmodulerouter-adapter',
  'ojs/ojknockoutrouteradapter',
  'ojs/ojurlparamadapter',
  'services/themeManager',
  'services/auth/session'
], function (ko, CoreRouter, ModuleRouterAdapter, KnockoutRouterAdapter, UrlParamAdapter, themeManager, session) {
  'use strict';

  function route(path, module, access, eyebrow, title, description, nextLabel, nextPath) {
    return {
      path: path,
      detail: {
        module: module,
        access: access,
        eyebrow: eyebrow,
        title: title,
        description: description,
        nextLabel: nextLabel,
        nextPath: nextPath
      }
    };
  }

  function AppControllerViewModel() {
    var self = this;
    var routes = [
      { path: '', redirect: 'landing' },
      route('landing', 'public/landing', 'public', 'Banking, thoughtfully organised', 'MoneyBag', 'A calmer way to manage everyday money.', 'Explore products', 'products'),
      route('products', 'public/products', 'public', 'Public catalogue', 'Products', 'Compare active deposits and credit-card products.', 'Create an account', 'register'),
      route('product-detail', 'public/productDetail', 'public', 'Public catalogue', 'Product details', 'Review product pricing, features, and eligibility.', 'Back to products', 'products'),
      route('register', 'public/register', 'public', 'Customer onboarding', 'Create your login', 'Register with Identity, then sign in to create your customer profile and start KYC.', 'Back to home', 'landing'),
      route('security', 'public/placeholder', 'public', 'Your security', 'Security centre', 'MoneyBag uses Identity sign-in, short-lived tokens, a server-side BFF session, tenant checks, and customer ownership controls.', 'Back to home', 'landing'),
      route('about', 'public/placeholder', 'public', 'About MoneyBag', 'Built for clearer banking', 'A modular core-banking experience for customers and bank administrators.', 'Back to home', 'landing'),

      route('customer-dashboard', 'customer/dashboard', 'customer-approved', 'Customer workspace', 'Your dashboard', 'Accounts, cards, bills, payments, and recent updates.', 'View accounts', 'customer-accounts'),
      route('customer-profile', 'customer/profile', 'customer', 'Customer workspace', 'Profile and onboarding', 'Create or maintain the CIF profile linked to your Identity login.', 'Open KYC', 'customer-kyc'),
      route('customer-kyc', 'customer/kyc', 'customer', 'Customer workspace', 'KYC and verification', 'Upload documents and follow administrator review.', 'View profile', 'customer-profile'),
      route('customer-accounts', 'customer/accounts', 'customer-approved', 'Customer workspace', 'Deposit accounts', 'Open an eligible savings or current account and inspect balances.', 'Explore fixed deposits', 'customer-deposits'),
      route('customer-deposits', 'customer/deposits', 'customer-approved', 'Customer workspace', 'Fixed deposits', 'Quote and book a fixed deposit against an eligible source account.', 'Back to accounts', 'customer-accounts'),
      route('customer-cards', 'customer/cards', 'customer-approved', 'Customer workspace', 'Credit cards', 'Apply, track status, and inspect active card accounts.', 'Go to payments', 'customer-payments'),
      route('customer-payments', 'customer/payments', 'customer-approved', 'Customer workspace', 'Payments', 'Make book transfers, card purchases, and bill repayments.', 'View bills', 'customer-bills'),
      route('customer-bills', 'customer/bills', 'customer-approved', 'Customer workspace', 'Bills', 'Review generated card bills and start a repayment.', 'View statements', 'customer-statements'),
      route('customer-statements', 'customer/statements', 'customer-approved', 'Customer workspace', 'Statements', 'Statement generation is intentionally pending; this page shows the integration boundary.', 'View notifications', 'customer-notifications'),
      route('customer-notifications', 'customer/notifications', 'customer', 'Customer workspace', 'Notifications', 'Account, payment, KYC, and bill updates.', 'Back to dashboard', 'customer-dashboard'),

      route('banker-dashboard', 'banker/dashboard', 'admin', 'Bank admin workspace', 'Operations dashboard', 'Work queues and operational controls.', 'Find a customer', 'banker-customers'),
      route('banker-customers', 'banker/customers', 'admin', 'Bank admin workspace', 'Customer 360', 'Search customer records and begin an authorised servicing journey.', 'Open KYC queue', 'banker-kyc'),
      route('banker-kyc', 'banker/kyc', 'admin', 'Bank admin workspace', 'KYC queue', 'Verify documents and record approval or rejection.', 'Open accounts', 'banker-accounts'),
      route('banker-accounts', 'banker/accounts', 'admin', 'Bank admin workspace', 'Deposit operations', 'Open, find, and service deposit accounts.', 'Open cards', 'banker-cards'),
      route('banker-cards', 'banker/cards', 'admin', 'Bank admin workspace', 'Credit operations', 'Review card applications and inspect card accounts.', 'Open catalogue', 'banker-catalogue'),
      route('banker-catalogue', 'banker/catalogue', 'admin', 'Bank admin workspace', 'Product master', 'Create and maintain catalogue, interest, and benchmark definitions.', 'Open payments', 'banker-payments'),
      route('banker-catalogue-editor', 'banker/productEditor', 'admin', 'Product master', 'Product editor', 'Create a complete draft or edit an existing product definition.', 'Back to catalogue', 'banker-catalogue'),
      route('banker-catalogue-pricing', 'banker/productPricing', 'admin', 'Product master', 'Pricing studio', 'Inspect interest policies and calculate explainable quotes.', 'Back to catalogue', 'banker-catalogue'),
      route('banker-catalogue-benchmarks', 'banker/productBenchmarks', 'admin', 'Product master', 'Treasury benchmarks', 'Maintain dated benchmark rates for linked product pricing.', 'Back to pricing', 'banker-catalogue-pricing'),
      route('banker-payments', 'banker/payments', 'admin', 'Bank admin workspace', 'Payment operations', 'Review payment status and permitted lifecycle actions.', 'Open billing', 'banker-billing'),
      route('banker-billing', 'banker/billing', 'admin', 'Bank admin workspace', 'Billing operations', 'Inspect generated bills and customer repayment context.', 'Open accounting', 'banker-accounting'),
      route('banker-accounting', 'banker/accounting', 'admin', 'Bank admin workspace', 'Accounting controls', 'Journals, GL accounts, rules, mappings, balances, and reconciliation.', 'Open EOD', 'banker-eod'),
      route('banker-eod', 'banker/eod', 'admin', 'Bank admin workspace', 'End-of-day cockpit', 'Assess readiness through available provider controls while EOD orchestration remains pending.', 'Open IAM', 'banker-iam'),
      route('banker-iam', 'banker/iam', 'admin', 'Bank admin workspace', 'Identity access', 'Create and inspect the two supported user roles.', 'Back to operations', 'banker-dashboard')
    ];

    var router = new CoreRouter(routes, { urlAdapter: new UrlParamAdapter() });
    this.currentPath = new KnockoutRouterAdapter(router).path;
    this.mobileMenuOpen = ko.observable(false);
    this.routeNotice = ko.observable('');
    this.currentUser = session.currentUser;
    this.sessionLoading = session.loading;
    this.isAuthenticated = ko.pureComputed(session.isAuthenticated);
    this.isAdmin = ko.pureComputed(session.isAdmin);
    this.isCustomer = ko.pureComputed(session.isCustomer);
    this.needsProfile = ko.pureComputed(function () {
      var user = session.getUser();
      return Boolean(user && session.isCustomer() && !user.customerId);
    });
    this.needsApproval = ko.pureComputed(function () {
      return session.isCustomer() && !self.needsProfile() && !session.isApproved();
    });
    this.displayName = ko.pureComputed(function () {
      var user = session.getUser();
      if (!user || !user.username) return '';
      return user.username.split('@')[0].replace(/[._-]+/g, ' ');
    });
    this.workspaceLabel = ko.pureComputed(function () {
      if (session.isAdmin()) return 'Bank admin';
      if (session.isCustomer()) {
        if (self.needsProfile()) return 'Onboarding';
        return self.needsApproval() ? 'KYC pending' : 'Customer';
      }
      return '';
    });
    this.pathStartsWith = function (prefix) {
      return String(self.currentPath() || '').indexOf(prefix) === 0;
    };

    function fallbackFor(access) {
      if (!session.isAuthenticated()) return 'landing';
      if (session.isAdmin()) return 'banker-dashboard';
      if (self.needsProfile()) return 'customer-profile';
      if (self.needsApproval()) return 'customer-kyc';
      return 'customer-dashboard';
    }

    function allowed(access) {
      if (!access || access === 'public') return true;
      if (access === 'admin') return session.isAdmin();
      if (access === 'customer') return session.isCustomer();
      if (access === 'customer-approved') return session.isCustomer() && session.isApproved();
      return false;
    }

    router.beforeStateChange.subscribe(function (args) {
      args.accept(session.ready().then(function () {
        var access = args.state && args.state.detail && args.state.detail.access;
        if (allowed(access)) return true;
        var fallback = fallbackFor(access);
        self.routeNotice(!session.isAuthenticated()
          ? 'Sign in to open that workspace.'
          : (access === 'customer-approved'
            ? (self.needsProfile()
              ? 'Create your customer profile before opening banking products.'
              : 'A bank administrator must approve your KYC before banking products are available.')
            : 'That page is not available for your role.'));
        window.setTimeout(function () { router.go({ path: fallback }); }, 0);
        return Promise.reject({ code: 'MONEYBAGS_ACCESS_REDIRECT' });
      }));
    });

    this.moduleAdapter = new ModuleRouterAdapter(router, { pathKey: 'module' });

    var activeTheme = themeManager.restore();
    this.isDarkMode = ko.observable(activeTheme === 'dark');
    this.themeToggleText = ko.pureComputed(function () { return self.isDarkMode() ? 'Dark' : 'Light'; });
    this.themeIcon = ko.pureComputed(function () { return self.isDarkMode() ? 'Moon' : 'Sun'; });
    this.themeToggleLabel = ko.pureComputed(function () {
      return self.isDarkMode() ? 'Switch to light mode' : 'Switch to dark mode';
    });
    this.year = new Date().getFullYear();

    this.navigate = function (path) {
      self.mobileMenuOpen(false);
      self.routeNotice('');
      return router.go({ path: path }).catch(function (error) {
        if (!error || error.code !== 'MONEYBAGS_ACCESS_REDIRECT') throw error;
      });
    };
    this.signInCustomer = function () { return session.signIn('customer', 'customer-dashboard'); };
    this.signInAdmin = function () { return session.signIn('admin', 'banker-dashboard'); };
    this.register = function () { return self.navigate('register'); };
    this.openAccount = function () {
      if (!session.isAuthenticated()) return self.navigate('register');
      if (self.needsProfile()) return self.navigate('customer-profile');
      if (self.needsApproval()) return self.navigate('customer-kyc');
      return self.navigate('customer-accounts');
    };
    this.signOut = function () {
      self.routeNotice('');
      return session.signOut().catch(function (error) {
        self.routeNotice(error.message || 'Sign out could not be completed.');
      });
    };
    this.dismissNotice = function () { self.routeNotice(''); };
    this.toggleMobileMenu = function () { self.mobileMenuOpen(!self.mobileMenuOpen()); };
    this.toggleTheme = function () {
      var selectedTheme = themeManager.toggle();
      self.isDarkMode(selectedTheme === 'dark');
    };

    session.ready().then(function () {
      return router.sync();
    }).then(function () {
      var returnPath = session.consumeReturnPath();
      if (returnPath && session.isAuthenticated()) return self.navigate(returnPath);
      return null;
    }).catch(function (error) {
      if (!error || error.code !== 'MONEYBAGS_ACCESS_REDIRECT') {
        console.error('MoneyBag could not synchronise its route.', error);
        self.routeNotice('The requested page could not be opened.');
      }
    });
  }

  return new AppControllerViewModel();
});
