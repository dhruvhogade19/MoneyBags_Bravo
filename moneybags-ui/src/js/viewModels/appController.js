define([
  'knockout',
  'ojs/ojcorerouter',
  'ojs/ojmodulerouter-adapter',
  'ojs/ojknockoutrouteradapter',
  'ojs/ojurlparamadapter',
  'services/themeManager'
], function (ko, CoreRouter, ModuleRouterAdapter, KnockoutRouterAdapter, UrlParamAdapter, themeManager) {
  'use strict';

  function route(path, module, eyebrow, title, description, nextLabel, nextPath) {
    return {
      path: path,
      detail: {
        module: module,
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
      route('landing', 'public/landing', 'Banking, thoughtfully organised', 'MoneyBag', 'A calmer way to manage everyday money.', 'Explore products', 'products'),
      route('products', 'public/placeholder', 'Public catalogue', 'Products', 'Compare savings, deposits, and credit-card products in one approachable catalogue.', 'Explore customer space', 'customer-dashboard'),
      route('security', 'public/placeholder', 'Your security', 'Security centre', 'A dedicated home for privacy, device controls, transaction alerts, and account protection.', 'Back to home', 'landing'),
      route('about', 'public/placeholder', 'About MoneyBag', 'Built for clearer banking', 'This page will introduce the people, principles, and support behind MoneyBag.', 'Back to home', 'landing'),
      route('customer-dashboard', 'customer/placeholder', 'Customer workspace', 'Your dashboard', 'An at-a-glance view of accounts, cards, balances, bills, and recent activity.', 'View accounts', 'customer-accounts'),
      route('customer-profile', 'customer/placeholder', 'Customer workspace', 'Profile and personal details', 'Manage contact information, preferences, and the profile information connected to your CIF.', 'Open KYC', 'customer-kyc'),
      route('customer-kyc', 'customer/placeholder', 'Customer workspace', 'KYC and verification', 'Start a KYC case, upload documents, and track verification progress here.', 'View profile', 'customer-profile'),
      route('customer-accounts', 'customer/placeholder', 'Customer workspace', 'Accounts', 'Savings, current, and deposit-account details will live here, with balance and activity views.', 'Explore deposits', 'customer-deposits'),
      route('customer-deposits', 'customer/placeholder', 'Customer workspace', 'Fixed deposits', 'Create, review, and manage fixed-deposit journeys from this dedicated space.', 'Back to accounts', 'customer-accounts'),
      route('customer-cards', 'customer/placeholder', 'Customer workspace', 'Cards', 'Apply for cards, follow an application, and view your active credit-card account here.', 'Go to payments', 'customer-payments'),
      route('customer-payments', 'customer/placeholder', 'Customer workspace', 'Payments', 'Book transfers, card repayments, status tracking, and safe cancellation will be organised here.', 'View bills', 'customer-bills'),
      route('customer-bills', 'customer/placeholder', 'Customer workspace', 'Bills', 'Your card bill, due-date reminders, and repayment actions will appear in this page.', 'View statements', 'customer-statements'),
      route('customer-statements', 'customer/placeholder', 'Customer workspace', 'Statements', 'Request, track, and download account and card statements from one place.', 'View notifications', 'customer-notifications'),
      route('customer-notifications', 'customer/placeholder', 'Customer workspace', 'Notifications', 'A calm inbox for account, payment, KYC, bill, and security updates.', 'Back to dashboard', 'customer-dashboard'),
      route('banker-dashboard', 'banker/placeholder', 'Banker workspace', 'Operations dashboard', 'A focused landing page for customer servicing, work queues, control checks, and operational action.', 'Find a customer', 'banker-customers'),
      route('banker-customers', 'banker/placeholder', 'Banker workspace', 'Customer servicing', 'Search customer records and begin authorised servicing journeys here.', 'Open KYC queue', 'banker-kyc'),
      route('banker-kyc', 'banker/placeholder', 'Banker workspace', 'KYC queue', 'Verify documents and make reviewer decisions with auditable customer context.', 'Open accounts', 'banker-accounts'),
      route('banker-accounts', 'banker/placeholder', 'Banker workspace', 'Account operations', 'Authorised account servicing, lifecycle actions, and deposit support will live here.', 'Open cards', 'banker-cards'),
      route('banker-cards', 'banker/placeholder', 'Banker workspace', 'Credit operations', 'Review applications, approve or reject requests, and open approved card accounts.', 'Open catalogue', 'banker-catalogue'),
      route('banker-catalogue', 'banker/placeholder', 'Banker workspace', 'Catalogue and pricing', 'Product drafts, lifecycle, interest policies, and benchmarks will be managed here.', 'Open accounting', 'banker-accounting'),
      route('banker-accounting', 'banker/placeholder', 'Banker workspace', 'Accounting controls', 'Journals, GL controls, rules, mappings, trial balances, and reconciliation exceptions belong here.', 'Open EOD', 'banker-eod'),
      route('banker-eod', 'banker/placeholder', 'Banker workspace', 'End-of-day controls', 'Run, monitor, resume, and resolve end-of-day work safely from this operational cockpit.', 'Open IAM', 'banker-iam'),
      route('banker-iam', 'banker/placeholder', 'Banker workspace', 'Access management', 'User roles and permission administration will be restricted to authorised IAM operators.', 'Back to operations', 'banker-dashboard')
    ];

    var router = new CoreRouter(routes, { urlAdapter: new UrlParamAdapter() });
    this.moduleAdapter = new ModuleRouterAdapter(router, { pathKey: 'module' });
    this.currentPath = new KnockoutRouterAdapter(router).path;
    this.mobileMenuOpen = ko.observable(false);

    var activeTheme = themeManager.restore();
    this.isDarkMode = ko.observable(activeTheme === 'dark');
    this.themeToggleText = ko.pureComputed(function () {
      return self.isDarkMode() ? 'Dark' : 'Light';
    });
    this.themeIcon = ko.pureComputed(function () {
      return self.isDarkMode() ? '☾' : '☀';
    });
    this.themeToggleLabel = ko.pureComputed(function () {
      return self.isDarkMode() ? 'Switch to light mode' : 'Switch to dark mode';
    });
    this.year = new Date().getFullYear();

    this.navigate = function (path) {
      self.mobileMenuOpen(false);
      return router.go({ path: path });
    };

    this.toggleMobileMenu = function () {
      self.mobileMenuOpen(!self.mobileMenuOpen());
    };

    this.toggleTheme = function () {
      var selectedTheme = themeManager.toggle();
      self.isDarkMode(selectedTheme === 'dark');
    };

    router.sync().catch(function (error) {
      console.error('MoneyBag could not synchronise its route.', error);
    });
  }

  return new AppControllerViewModel();
});
