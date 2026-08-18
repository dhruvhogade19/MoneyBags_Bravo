import { h } from "preact";
import { useState } from "preact/hooks";
import { auth, Persona } from "../auth";
import { Icon } from "../components/common";
import { navigate } from "../router";

export function LandingPage({ initialError }: { initialError?: string }) {
  const [busy, setBusy] = useState<Persona>();
  const start = async (persona: Persona) => {
    setBusy(persona);
    try { await auth.login(persona); } catch { setBusy(undefined); }
  };
  return <main class="mb-landing"><div class="mb-landing-shell">
    <section class="mb-landing-story">
      <div class="mb-brand"><span class="mb-brand-mark">✦</span><strong>moneybags</strong></div>
      <h1>Your money.<br/>One calm place.</h1><p>Accounts, deposits, cards and payments—connected securely across every MoneyBags service.</p>
      <div class="mb-landing-orb" aria-hidden="true"></div>
    </section>
    <section class="mb-login-panel" aria-labelledby="sign-in-title">
      <h2 id="sign-in-title">Welcome back</h2><p>Choose your secure MoneyBags workspace.</p>
      {initialError && <div class="mb-error" role="alert">{initialError}</div>}
      <div class="mb-login-choice">
        <button type="button" disabled={Boolean(busy)} onClick={() => start("consumer")}><Icon name="accounts" /><span><strong>{busy === "consumer" ? "Redirecting…" : "Customer banking"}</strong><small>Accounts, cards, deposits and payments</small></span><b>→</b></button>
        <button type="button" disabled={Boolean(busy)} onClick={() => start("admin")}><Icon name="operations" /><span><strong>{busy === "admin" ? "Redirecting…" : "Bank operations"}</strong><small>KYC, products, payments and accounting</small></span><b>→</b></button>
      </div>
      <div class="mb-signup-prompt"><span>New to MoneyBags?</span><button type="button" onClick={() => navigate("/signup")}>Create an account →</button></div>
      <p class="mb-login-footnote">Protected by OAuth 2.0 Authorization Code with PKCE. Your session is restored only in this browser tab and ends when you sign out or close the tab.</p>
    </section>
  </div></main>;
}
