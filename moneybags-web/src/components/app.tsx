import { registerCustomElement } from "ojs/ojvcomponent";
import { h, ComponentChildren } from "preact";
import { useEffect, useMemo, useState } from "preact/hooks";
import Context = require("ojs/ojcontext");
import { ApiError } from "../api";
import { auth, Persona, Session } from "../auth";
import { Icon, Loading } from "./common";
import { navigate, usePath } from "../router";
import { LandingPage } from "../pages/landing";
import { SignupPage } from "../pages/signup";
import {
  BillPayPage, CardApplyPage, CardsPage, CustomerDashboard,
  KycPage, MerchantPaymentPage, NotificationsPage, PaymentDetailPage, PaymentsPage,
  ProductsPage, ProfilePage, RepaymentsPage, StatementsPage, TransferPage
} from "../pages/customer";
import { BillingDashboardPage } from "../pages/billing";
import { AdminBillingStatementWizard } from "../pages/admin-billing";
import {
  DepositAccountClosePage, DepositAccountDetailPage, DepositAccountManagePage,
  DepositAccountOpenPage, DepositAccountsPage, DepositRequestsPage,
  FixedDepositBookPage, FixedDepositCalculatorPage, FixedDepositDetailPage,
  FixedDepositPrematureClosurePage, FixedDepositsPortfolioPage
} from "../pages/deposits";
import {
  AccountingCollection, CardBillingOperations, CustomerNotificationOperations, JournalSearch, KycWorkQueue,
  OperationsDashboard, OperationsPlaceholder, PaymentOperations, ProductAdministration
} from "../pages/operations";
import { DepositCustomerSearch, DepositOperationsDashboard, FixedDepositEodConsole } from "../pages/deposit-operations";
import { AccountingConfigurationPage, AccountingOverviewPage, AccountingTransactionsPage } from "../pages/accounting";
import { EodOperationsPage } from "../pages/eod";
import { isNavigationPathActive } from "../utils";
import { services } from "../services";

type NavigationItem = Readonly<{ label: string; icon: string; path: string }>;

const customerNavigation: NavigationItem[] = [
  { label: "Overview", icon: "home", path: "/app/overview" },
  { label: "Profile & KYC", icon: "person", path: "/app/profile" },
  { label: "Products", icon: "inventory_2", path: "/app/products" },
  { label: "Accounts", icon: "account_balance", path: "/app/accounts" },
  { label: "Fixed deposits", icon: "savings", path: "/app/fixed-deposits" },
  { label: "Cards & bills", icon: "credit_card", path: "/app/cards" },
  { label: "Payments", icon: "swap_horiz", path: "/app/payments" },
  { label: "Statements", icon: "description", path: "/app/statements" },
  { label: "Notifications", icon: "notifications", path: "/app/notifications" }
];

const operationsNavigation: NavigationItem[] = [
  { label: "Overview", icon: "dashboard", path: "/ops/overview" },
  { label: "KYC queue", icon: "verified_user", path: "/ops/kyc" },
  { label: "Products", icon: "inventory_2", path: "/ops/products" },
  { label: "Deposit operations", icon: "account_balance", path: "/ops/deposits" },
  { label: "Account / FD search", icon: "search", path: "/ops/deposits/search" },
  { label: "FD EOD console", icon: "fact_check", path: "/ops/deposits/eod" },
  { label: "Cards & billing", icon: "credit_card", path: "/ops/cards" },
  { label: "Generate bill", icon: "description", path: "/ops/bills/generate" },
  { label: "Payments", icon: "payments", path: "/ops/payments" },
  { label: "EOD control", icon: "fact_check", path: "/ops/eod" },
  { label: "Customers & email", icon: "contact_mail", path: "/ops/customers" },
  { label: "Accounting overview", icon: "dashboard", path: "/ops/accounting/overview" },
  { label: "Journals & ledger", icon: "receipt_long", path: "/ops/accounting/transactions" },
  { label: "GL configuration", icon: "schema", path: "/ops/accounting/configuration" },
  { label: "Access admin", icon: "admin_panel_settings", path: "/ops/access" }
];

function matchRoute(path: string): ComponentChildren {
  const exact: Record<string, ComponentChildren> = {
    "/app/overview": <CustomerDashboard />, "/app/profile": <ProfilePage />,
    "/app/kyc": <KycPage />, "/app/products": <ProductsPage />,
    "/app/accounts": <DepositAccountsPage />, "/app/accounts/open": <DepositAccountOpenPage />,
    "/app/fixed-deposits": <FixedDepositsPortfolioPage />,
    "/app/fixed-deposits/open": <FixedDepositBookPage />,
    "/app/fixed-deposits/book": <FixedDepositBookPage />,
    "/app/fixed-deposits/calculator": <FixedDepositCalculatorPage />,
    "/app/requests": <DepositRequestsPage />,
    "/app/cards": <CardsPage />, "/app/cards/apply": <CardApplyPage />,
    "/app/payments": <PaymentsPage />, "/app/payments/transfer": <TransferPage />,
    "/app/payments/merchant": <MerchantPaymentPage />, "/app/payments/repay": <RepaymentsPage />,
    "/app/bills": <BillingDashboardPage />, "/app/statements": <StatementsPage />,
    "/app/notifications": <NotificationsPage />,
    "/ops/overview": <OperationsDashboard />, "/ops/kyc": <KycWorkQueue />,
    "/ops/products": <ProductAdministration />,
    "/ops/accounting/overview": <AccountingOverviewPage />,
    "/ops/accounting/transactions": <AccountingTransactionsPage />,
    "/ops/accounting/configuration": <AccountingConfigurationPage />,
    "/ops/accounting/journals": <AccountingTransactionsPage />,
    "/ops/accounting/gl": <AccountingConfigurationPage />,
    "/ops/accounting/rules": <AccountingConfigurationPage />,
    "/ops/accounting/mappings": <AccountingConfigurationPage />,
    "/ops/deposits": <DepositOperationsDashboard />,
    "/ops/deposits/search": <DepositCustomerSearch />,
    "/ops/deposits/eod": <FixedDepositEodConsole />,
    "/ops/cards": <CardBillingOperations />,
    "/ops/bills/generate": <AdminBillingStatementWizard />,
    "/ops/payments": <PaymentOperations />,
    "/ops/customers": <CustomerNotificationOperations />,
    "/ops/eod": <EodOperationsPage />,
    "/ops/access": <OperationsPlaceholder title="Access administration" description="Manage users, roles and client access." blocker="Identity supports user creation and lookup by ID, but it does not yet expose the list, search and role-lifecycle APIs required for an administration console." />
  };
  if (exact[path]) return exact[path];
  let matched = path.match(/^\/app\/accounts\/([^/]+)\/manage$/);
  if (matched) return <DepositAccountManagePage accountId={decodeURIComponent(matched[1])} />;
  matched = path.match(/^\/app\/accounts\/([^/]+)\/close$/);
  if (matched) return <DepositAccountClosePage accountId={decodeURIComponent(matched[1])} />;
  matched = path.match(/^\/app\/accounts\/([^/]+)$/);
  if (matched) return <DepositAccountDetailPage accountId={decodeURIComponent(matched[1])} />;
  matched = path.match(/^\/app\/fixed-deposits\/([^/]+)\/premature-closure$/);
  if (matched) return <FixedDepositPrematureClosurePage fdId={decodeURIComponent(matched[1])} />;
  matched = path.match(/^\/app\/fixed-deposits\/([^/]+)$/);
  if (matched) return <FixedDepositDetailPage fdId={decodeURIComponent(matched[1])} />;
  matched = path.match(/^\/app\/bills\/([^/]+)\/pay$/);
  if (matched) return <BillPayPage billId={decodeURIComponent(matched[1])} />;
  matched = path.match(/^\/app\/payments\/([^/]+)$/);
  if (matched) return <PaymentDetailPage paymentId={decodeURIComponent(matched[1])} />;
  return <div class="mb-not-found"><span>404</span><h1>We could not find that page.</h1><button class="mb-button mb-button-primary" onClick={() => navigate(path.startsWith("/ops") ? "/ops/overview" : "/app/overview")}>Back to overview</button></div>;
}

function initials(session: Session) {
  return (session.claims.name || session.claims.sub || "MB").split(/[ ._-]/).filter(Boolean).slice(0, 2).map(v => v[0]).join("").toUpperCase();
}

function hasCustomerProfile(session: Session): boolean {
  const customerId = session.claims.customer_id;
  return customerId !== undefined && customerId !== null && String(customerId).trim() !== "";
}

function AppShell({ session, path, children }: { session: Session; path: string; children: ComponentChildren }) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [search, setSearch] = useState("");
  const onboarding = session.persona === "consumer" && !hasCustomerProfile(session);
  const navigation = session.persona === "admin" ? operationsNavigation : customerNavigation;
  const label = session.persona === "admin" ? "Operations" : "Your money";
  const searchResults = search.trim() ? navigation.filter(item => item.label.toLowerCase().includes(search.trim().toLowerCase())) : navigation.slice(0, 5);
  const choose = (target: string) => { navigate(target); setSearchOpen(false); setSearch(""); };
  return <div class="mb-app-shell">
    <aside class={`mb-sidebar ${mobileOpen ? "is-open" : ""}`}>
      <a class="mb-brand" href={navigation[0].path} onClick={(event) => { event.preventDefault(); navigate(navigation[0].path); setMobileOpen(false); }}><span class="mb-brand-mark">✦</span><strong>moneybags</strong></a>
      <span class="mb-nav-label">{label}</span>
      <nav aria-label="Main navigation">{navigation.map(item => <a key={item.path} class={isNavigationPathActive(item.path, path, navigation[0].path) ? "active" : ""} href={item.path} onClick={(event) => { event.preventDefault(); navigate(item.path); setMobileOpen(false); }}><Icon name={item.icon} /><span>{item.label}</span></a>)}</nav>
      <div class="mb-sidebar-help"><span>Need a hand?</span><small>MoneyBags support is here for you.</small></div>
    </aside>
    {mobileOpen && <button class="mb-nav-scrim" aria-label="Close menu" onClick={() => setMobileOpen(false)} />}
    <section class="mb-workspace">
      <header class="mb-topbar">
        <button class="mb-icon-button mb-menu" aria-label="Open navigation" onClick={() => setMobileOpen(true)}><Icon name="menu" /></button>
        <div class="mb-topbar-spacer" />
        <button class="mb-search-button" onClick={() => setSearchOpen(value => !value)}><Icon name="search" /><span>Search</span></button>
        {session.persona === "consumer" && !onboarding && <button class="mb-button mb-button-primary" onClick={() => navigate("/app/payments/transfer")}>+ Send money</button>}
        <div class="mb-profile-control">
          <button class="mb-avatar" aria-label="Open profile menu" aria-expanded={profileOpen} onClick={() => setProfileOpen(value => !value)}>{initials(session)}</button>
          {profileOpen && <div class="mb-profile-menu" role="menu">
            <span>Signed in as</span>
            <strong>{session.claims.name || session.claims.sub || "MoneyBags user"}</strong>
            <small>{session.persona === "admin" ? "Bank operations" : "Customer banking"}</small>
            <button type="button" role="menuitem" onClick={() => auth.logout()}><Icon name="logout"/><span>Sign out securely</span></button>
          </div>}
        </div>
      </header>
      {searchOpen && <div class="mb-search-popover"><form class="mb-global-search" onSubmit={(event) => { event.preventDefault(); if (searchResults[0]) choose(searchResults[0].path); }}><Icon name="search" /><input autoFocus aria-label="Search MoneyBags" value={search} onInput={(event) => setSearch((event.currentTarget as HTMLInputElement).value)} placeholder="Search pages and services" /><button type="button" onClick={() => { setSearchOpen(false); setSearch(""); }}>Close</button></form><div class="mb-search-results">{searchResults.map(item => <button key={item.path} onClick={() => choose(item.path)}><Icon name={item.icon}/><span>{item.label}</span><b>→</b></button>)}{!searchResults.length && <p>No matching MoneyBags page.</p>}</div></div>}
      <main class="mb-main">{children}</main>
    </section>
  </div>;
}

export const App = registerCustomElement("app-root", () => {
  const path = usePath();
  const [session, setSession] = useState<Session | undefined>();
  const [ready, setReady] = useState(false);
  const [error, setError] = useState<string>();
  const [profileState, setProfileState] = useState<"idle" | "resolving" | "missing" | "ready" | "error">("idle");
  const [profileError, setProfileError] = useState<string>();
  const [profileRetry, setProfileRetry] = useState(0);

  useEffect(() => {
    let live = true;
    const unsubscribe = auth.subscribe(() => { if (live) setSession(auth.session); });
    auth.initialize().then(() => {
      if (live) { setSession(auth.session); setReady(true); }
    }).catch(reason => { if (live) { setError(reason instanceof Error ? reason.message : String(reason)); setReady(true); } });
    Context.getPageContext().getBusyContext().applicationBootstrapComplete();
    return () => { live = false; unsubscribe(); };
  }, []);

  useEffect(() => {
    if (!session || session.persona !== "consumer") {
      setProfileState("idle");
      setProfileError(undefined);
      return;
    }
    if (hasCustomerProfile(session)) {
      setProfileState("ready");
      setProfileError(undefined);
      return;
    }
    const controller = new AbortController();
    let live = true;
    setProfileState("resolving");
    setProfileError(undefined);
    services.cif.me(controller.signal).then(async () => {
      await services.cif.repairIdentityLink();
      const refreshed = await auth.refreshSession();
      if (!hasCustomerProfile(refreshed)) {
        throw new Error("Your customer profile exists, but the secure session could not be linked to it.");
      }
      if (live) setProfileState("ready");
    }).catch(reason => {
      if (!live || controller.signal.aborted) return;
      if (reason instanceof ApiError && reason.problem.status === 404) {
        setProfileState("missing");
        return;
      }
      setProfileState("error");
      setProfileError(reason instanceof Error ? reason.message : "MoneyBags could not prepare your customer workspace.");
    });
    return () => { live = false; controller.abort(); };
  }, [session, profileRetry]);

  useEffect(() => {
    if (!ready || !session) return;
    const validArea = session.persona === "admin" ? path.startsWith("/ops/") : path.startsWith("/app/");
    if (path === "/" || !validArea) {
      navigate(session.persona === "admin" ? "/ops/overview" : "/app/overview", true);
      return;
    }
    if (session.persona === "consumer" && !hasCustomerProfile(session) && path !== "/app/overview" && path !== "/app/profile") {
      navigate("/app/profile", true);
    }
  }, [ready, session, path]);

  const effectivePath = session?.persona === "consumer" && !hasCustomerProfile(session) && path !== "/app/overview" && path !== "/app/profile"
    ? "/app/profile"
    : path;
  const page = useMemo(() => matchRoute(effectivePath), [effectivePath]);
  if (!ready) return <Loading label="Opening MoneyBags" />;
  if (!session) return path === "/signup" ? <SignupPage /> : <LandingPage initialError={error} />;
  if (session.persona === "consumer" && !hasCustomerProfile(session) && (profileState === "idle" || profileState === "resolving")) {
    return <Loading label="Preparing your secure customer workspace" />;
  }
  if (session.persona === "consumer" && !hasCustomerProfile(session) && profileState === "error") {
    return <main class="mb-session-recovery"><section><span class="mb-brand-mark">✦</span><h1>We couldn’t open your banking workspace</h1><p>{profileError}</p><div><button class="mb-button mb-button-primary" onClick={() => setProfileRetry(value => value + 1)}>Try again</button><button class="mb-button mb-button-secondary" onClick={() => auth.logout()}>Sign out</button></div></section></main>;
  }
  return <AppShell session={session} path={effectivePath}>{page}</AppShell>;
});
