import { h } from "preact";
import { useState } from "preact/hooks";
import { auth } from "../auth";
import { config } from "../config";
import { navigate } from "../router";

type RegistrationResult = Readonly<{ userId: string; username: string; role: string }>;

async function describeFailure(response: Response) {
  try {
    const body = await response.json() as { detail?: string; message?: string; error?: string };
    return body.detail || body.message || body.error || `Registration failed (${response.status})`;
  } catch {
    return `Registration failed (${response.status})`;
  }
}

export function SignupPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [created, setCreated] = useState<RegistrationResult>();

  const submit = async (event: Event) => {
    event.preventDefault();
    setError(undefined);
    if (password !== confirmation) { setError("Passwords do not match."); return; }
    setBusy(true);
    try {
      const response = await fetch(`${config.issuer}/api/v1/identity/registrations`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password })
      });
      if (!response.ok) throw new Error(await describeFailure(response));
      setCreated(await response.json() as RegistrationResult);
      setPassword(""); setConfirmation("");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Registration failed.");
    } finally { setBusy(false); }
  };

  return <main class="mb-landing"><div class="mb-landing-shell">
    <section class="mb-landing-story">
      <div class="mb-brand"><span class="mb-brand-mark">✦</span><strong>moneybags</strong></div>
      <h1>Start clearly.<br/>Grow calmly.</h1>
      <p>Create secure access first. After sign-in, MoneyBags guides you through your customer profile and KYC.</p>
      <div class="mb-landing-orb" aria-hidden="true"></div>
    </section>
    <section class="mb-login-panel" aria-labelledby="signup-title">
      {created ? <>
        <span class="mb-success-kicker">Account created</span>
        <h2 id="signup-title">You’re ready to sign in</h2>
        <p>{created.username} can now continue to customer onboarding.</p>
        <div class="mb-success-banner">Next, complete your profile and KYC to unlock accounts and products.</div>
        <button class="mb-button mb-button-primary mb-wide-button" onClick={() => auth.login("consumer")}>Continue to sign in</button>
      </> : <>
        <button class="mb-back-link" type="button" onClick={() => navigate("/")}>← Back to sign in</button>
        <h2 id="signup-title">Create your account</h2>
        <p>Use your email to create secure customer access.</p>
        {error && <div class="mb-error" role="alert">{error}</div>}
        <form class="mb-form mb-signup-form" onSubmit={submit}>
          <div class="mb-field"><label for="signup-email">Email address</label><input id="signup-email" type="email" required maxLength={100} autoComplete="email" value={username} onInput={e => setUsername(e.currentTarget.value)} /></div>
          <div class="mb-field"><label for="signup-password">Password</label><input id="signup-password" type="password" required minLength={12} maxLength={128} autoComplete="new-password" value={password} onInput={e => setPassword(e.currentTarget.value)} /><small>Use at least 12 characters.</small></div>
          <div class="mb-field"><label for="signup-confirmation">Confirm password</label><input id="signup-confirmation" type="password" required minLength={12} maxLength={128} autoComplete="new-password" value={confirmation} onInput={e => setConfirmation(e.currentTarget.value)} /></div>
          <button class="mb-button mb-button-primary mb-wide-button" type="submit" disabled={busy}>{busy ? "Creating account…" : "Create customer account"}</button>
        </form>
      </>}
    </section>
  </div></main>;
}
