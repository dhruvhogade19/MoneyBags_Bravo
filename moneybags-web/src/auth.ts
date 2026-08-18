import { config } from "./config";

export type Persona = "consumer" | "admin";

export type JwtClaims = Readonly<{
  sub?: string;
  name?: string;
  user_id?: string;
  tenant_id?: string;
  customer_id?: string | number;
  roles?: string[];
  scope?: string | string[];
  exp?: number;
  aud?: string | string[];
}>;

export type Session = Readonly<{
  accessToken: string;
  refreshToken?: string;
  idToken?: string;
  expiresAt: number;
  claims: JwtClaims;
  persona: Persona;
}>;

type TokenResponse = {
  access_token: string;
  refresh_token?: string;
  id_token?: string;
  expires_in: number;
};

type PendingLogin = {
  state: string;
  verifier: string;
  persona: Persona;
};

const PENDING_KEY = "moneybags.pkce";
const SESSION_KEY = "moneybags.session.v1";
const LOGOUT_EVENT_KEY = "moneybags.logout.event";
const AUTH_CHANNEL = "moneybags.auth";

function base64Url(bytes: Uint8Array): string {
  let value = "";
  bytes.forEach((byte) => { value += String.fromCharCode(byte); });
  return btoa(value).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function randomValue(size = 32): string {
  const bytes = new Uint8Array(size);
  crypto.getRandomValues(bytes);
  return base64Url(bytes);
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return base64Url(new Uint8Array(digest));
}

function decodeClaims(token: string): JwtClaims {
  const part = token.split(".")[1];
  if (!part) throw new Error("Identity returned an invalid access token");
  const padded = part.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(part.length / 4) * 4, "=");
  return JSON.parse(atob(padded)) as JwtClaims;
}

function requiredScopes(persona: Persona): string[] {
  const consumer = ["openid", "profile", "product:read", "cif:read", "cif:write", "kyc:read", "kyc:write",
    "account:read", "account:open", "account:write", "account:close", "fd:read", "fd:open", "fd:close",
    "payment:read", "payment:write", "card:read", "card:apply", "billing:read", "notification:read"];
  const admin = ["openid", "profile", "product:read", "product:admin", "cif:read", "cif:admin", "kyc:read",
    "kyc:review", "account:read", "account:admin", "fd:read", "fd:admin", "payment:read", "payment:admin",
    "card:read", "card:admin", "accounting:read", "accounting:admin", "notification:read",
    "notification:admin", "billing:read", "billing:admin"];
  return persona === "admin" ? admin : consumer;
}

function scopes(persona: Persona): string {
  return requiredScopes(persona).join(" ");
}

function grantedScopes(claims: JwtClaims): Set<string> {
  const claim = claims.scope;
  return new Set(Array.isArray(claim) ? claim : (claim ?? "").split(" ").filter(Boolean));
}

function missingRequiredScopes(claims: JwtClaims, persona: Persona): string[] {
  const granted = grantedScopes(claims);
  return requiredScopes(persona).filter((scope) => !granted.has(scope));
}

export class AuthService {
  private current?: Session;
  private listeners = new Set<() => void>();
  private refreshInFlight?: Promise<Session>;
  private refreshTimer?: number;
  private channel?: BroadcastChannel;

  constructor() {
    if (typeof BroadcastChannel !== "undefined") {
      this.channel = new BroadcastChannel(AUTH_CHANNEL);
      this.channel.addEventListener("message", (event) => {
        if (event.data?.type === "logout") this.acceptRemoteLogout();
      });
    }
    window.addEventListener("storage", (event) => {
      if (event.key === LOGOUT_EVENT_KEY && event.newValue) this.acceptRemoteLogout();
    });
  }

  get session(): Session | undefined { return this.current; }
  get isAuthenticated(): boolean { return Boolean(this.current); }

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  async initialize(): Promise<void> {
    const params = new URLSearchParams(window.location.search);
    if (params.has("code") || params.has("error")) {
      try {
        await this.completeLogin(params);
      } finally {
        // Authorization codes are single-use and must never remain visible in
        // the address bar, including after an authorization failure.
        history.replaceState({}, "", "/");
      }
      return;
    }
    this.restoreSession();
    // A tab can retain a valid token issued before a newly introduced API
    // permission existed. Do not restore that partially capable session: it
    // otherwise appears signed in and fails only when the new feature is used.
    if (this.current && missingRequiredScopes(this.current.claims, this.current.persona).length) {
      this.clearSession();
      return;
    }
    if (this.current?.expiresAt && this.current.expiresAt <= Date.now() + 30_000) {
      if (!this.current.refreshToken) {
        this.clearSession();
        return;
      }
      try {
        await this.refreshSession();
      } catch {
        this.clearSession();
      }
    }
  }

  async login(persona: Persona): Promise<void> {
    const verifier = randomValue(48);
    const state = randomValue(24);
    const pending: PendingLogin = { verifier, state, persona };
    sessionStorage.setItem(PENDING_KEY, JSON.stringify(pending));
    const challenge = await sha256(verifier);
    const clientId = persona === "admin" ? config.adminClientId : config.consumerClientId;
    const query = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: config.redirectUri,
      scope: scopes(persona),
      prompt: "login",
      mb_switch: "1",
      state,
      code_challenge: challenge,
      code_challenge_method: "S256"
    });
    window.location.assign(`${config.issuer}/oauth2/authorize?${query.toString()}`);
  }

  logout(): void {
    this.clearSession();
    sessionStorage.removeItem(PENDING_KEY);
    this.channel?.postMessage({ type: "logout" });
    // Storage events provide a fallback for browsers without BroadcastChannel.
    localStorage.setItem(LOGOUT_EVENT_KEY, String(Date.now()));
    localStorage.removeItem(LOGOUT_EVENT_KEY);
    // Clear the shared Identity Service browser cookie as well as this tab's token.
    // This endpoint deliberately does not require an ID-token hint, so logout also
    // works after a token has expired or the Identity Service has restarted.
    window.location.replace(`${config.issuer}/session/logout`);
  }

  hasRole(role: string): boolean {
    return this.current?.claims.roles?.includes(role) ?? false;
  }

  hasScope(scope: string): boolean {
    const claim = this.current?.claims.scope;
    const values = Array.isArray(claim) ? claim : (claim ?? "").split(" ");
    return values.includes(scope);
  }

  async accessToken(): Promise<string> {
    if (!this.current) throw new Error("Sign in is required");
    if (this.current.expiresAt > Date.now() + 30_000) return this.current.accessToken;
    if (!this.current.refreshToken) {
      this.clearSession();
      throw new Error("Your session has expired. Please sign in again.");
    }
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.refresh(this.current).finally(() => { this.refreshInFlight = undefined; });
    }
    try {
      return (await this.refreshInFlight).accessToken;
    } catch {
      this.clearSession();
      throw new Error("Your session has expired. Please sign in again.");
    }
  }

  async refreshSession(): Promise<Session> {
    if (!this.current?.refreshToken) {
      throw new Error("Your secure session cannot be refreshed. Please sign in again.");
    }
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.refresh(this.current).finally(() => { this.refreshInFlight = undefined; });
    }
    return this.refreshInFlight;
  }

  private async completeLogin(params: URLSearchParams): Promise<void> {
    const raw = sessionStorage.getItem(PENDING_KEY);
    sessionStorage.removeItem(PENDING_KEY);
    if (!raw) throw new Error("The sign-in request could not be verified. Please start again.");
    const pending = JSON.parse(raw) as PendingLogin;
    if (params.get("error")) throw new Error(params.get("error_description") ?? "Sign-in was not completed");
    if (params.get("state") !== pending.state) throw new Error("The sign-in response state is invalid");
    const code = params.get("code");
    if (!code) throw new Error("Identity did not return an authorization code");
    const clientId = pending.persona === "admin" ? config.adminClientId : config.consumerClientId;
    const token = await this.tokenRequest(new URLSearchParams({
      grant_type: "authorization_code",
      code,
      client_id: clientId,
      redirect_uri: config.redirectUri,
      code_verifier: pending.verifier
    }));
    this.setSession(token, pending.persona);
    if (pending.persona === "admin" && this.current?.persona !== "admin") {
      this.clearSession();
      throw new Error("This identity does not have bank-operations access. Sign in with a BANK_ADMIN account.");
    }
  }

  private async refresh(session: Session): Promise<Session> {
    const clientId = session.persona === "admin" ? config.adminClientId : config.consumerClientId;
    const token = await this.tokenRequest(new URLSearchParams({
      grant_type: "refresh_token",
      refresh_token: session.refreshToken!,
      client_id: clientId
    }));
    this.setSession(token, session.persona, session.refreshToken, session.idToken);
    return this.current!;
  }

  private async tokenRequest(body: URLSearchParams): Promise<TokenResponse> {
    const response = await fetch(`${config.issuer}/oauth2/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body
    });
    if (!response.ok) throw new Error("Identity could not complete the session");
    return response.json() as Promise<TokenResponse>;
  }

  private setSession(token: TokenResponse, persona: Persona, oldRefresh?: string, oldIdToken?: string): void {
    const claims = decodeClaims(token.access_token);
    const audience = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
    if (!audience.includes("moneybags-api")) throw new Error("The access token has an invalid audience");
    // Workspace access is derived from the signed token, never from the button
    // selected before authentication.
    const actualPersona: Persona = claims.roles?.includes("BANK_ADMIN") ? "admin" : "consumer";
    const missing = missingRequiredScopes(claims, actualPersona);
    if (missing.length) {
      this.clearSession();
      throw new Error(`Identity did not grant the required application permissions: ${missing.join(", ")}`);
    }
    this.current = {
      accessToken: token.access_token,
      refreshToken: token.refresh_token ?? oldRefresh,
      idToken: token.id_token ?? oldIdToken,
      expiresAt: Date.now() + token.expires_in * 1000,
      claims,
      persona: actualPersona
    };
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(this.current));
    this.scheduleRefresh();
    this.emit();
  }

  private restoreSession(): void {
    const raw = sessionStorage.getItem(SESSION_KEY);
    if (!raw) return;
    try {
      const restored = JSON.parse(raw) as Session;
      if (!restored.accessToken || !restored.expiresAt || !restored.persona || !restored.claims) {
        throw new Error("Incomplete saved session");
      }
      const actualPersona: Persona = restored.claims.roles?.includes("BANK_ADMIN") ? "admin" : "consumer";
      this.current = { ...restored, persona: actualPersona };
      if (restored.persona !== actualPersona) {
        sessionStorage.setItem(SESSION_KEY, JSON.stringify(this.current));
      }
      this.scheduleRefresh();
    } catch {
      sessionStorage.removeItem(SESSION_KEY);
    }
  }

  private clearSession(): void {
    if (this.refreshTimer !== undefined) window.clearTimeout(this.refreshTimer);
    this.refreshTimer = undefined;
    this.current = undefined;
    sessionStorage.removeItem(SESSION_KEY);
    this.emit();
  }

  private acceptRemoteLogout(): void {
    if (!this.current && !sessionStorage.getItem(PENDING_KEY)) return;
    this.clearSession();
    sessionStorage.removeItem(PENDING_KEY);
    window.location.replace("/");
  }

  private scheduleRefresh(): void {
    if (this.refreshTimer !== undefined) window.clearTimeout(this.refreshTimer);
    if (!this.current?.refreshToken) return;
    const delay = Math.max(1_000, this.current.expiresAt - Date.now() - 60_000);
    this.refreshTimer = window.setTimeout(() => {
      this.refreshSession().catch(() => {
        // Keep the current session visible during a transient network failure.
        // A later protected request will perform the authoritative refresh check.
      });
    }, delay);
  }

  private emit(): void { this.listeners.forEach((listener) => listener()); }
}

export const auth = new AuthService();
