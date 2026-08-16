import { createHash, randomBytes } from "node:crypto";

const identityBaseUrl = process.env.IDENTITY_BASE_URL || "http://localhost:8093";
const role = (process.argv[2] || "consumer").toLowerCase();
const definitions = {
  admin: {
    clientId: "moneybags-admin",
    redirectUri: "http://127.0.0.1:8001/login/oauth2/code/moneybags-admin",
    username: process.env.LOCAL_ADMIN_USERNAME || "admin@moneybags.local",
    password: process.env.LOCAL_ADMIN_PASSWORD || "ChangeThisAdminPassword!",
    scopes: [
      "openid", "profile", "product:read", "product:admin", "cif:read", "cif:admin",
      "kyc:read", "kyc:review", "account:read", "account:admin", "fd:read", "fd:admin",
      "payment:read", "payment:admin", "card:read", "card:admin", "accounting:read",
      "accounting:admin", "notification:read", "notification:admin"
    ]
  },
  consumer: {
    clientId: "moneybags-consumer",
    redirectUri: "http://127.0.0.1:8000/login/oauth2/code/moneybags-consumer",
    username: process.env.LOCAL_CONSUMER_USERNAME || "consumer@moneybags.local",
    password: process.env.LOCAL_CONSUMER_PASSWORD || "ChangeThisConsumerPassword!",
    scopes: [
      "openid", "profile", "product:read", "cif:read", "cif:write", "kyc:read", "kyc:write",
      "account:read", "account:open", "account:write", "account:close", "fd:read", "fd:open",
      "fd:close", "payment:read", "payment:write", "card:read", "card:apply", "notification:read"
    ]
  }
};

if (!definitions[role]) {
  throw new Error("Usage: node local-oauth.mjs <admin|consumer>");
}

const config = definitions[role];
const cookies = new Map();

function cookieHeader() {
  return [...cookies].map(([name, value]) => `${name}=${value}`).join("; ");
}

function rememberCookies(response) {
  const setCookies = response.headers.getSetCookie?.() ||
    (response.headers.get("set-cookie") ? [response.headers.get("set-cookie")] : []);
  for (const value of setCookies) {
    const firstPart = value.split(";", 1)[0];
    const separator = firstPart.indexOf("=");
    if (separator > 0) cookies.set(firstPart.slice(0, separator), firstPart.slice(separator + 1));
  }
}

async function send(url, options = {}) {
  const headers = new Headers(options.headers || {});
  const cookie = cookieHeader();
  if (cookie) headers.set("Cookie", cookie);
  const response = await fetch(url, { ...options, headers, redirect: "manual" });
  rememberCookies(response);
  return response;
}

function resolveLocation(response, currentUrl) {
  const location = response.headers.get("location");
  return location ? new URL(location, currentUrl).toString() : null;
}

function csrfToken(html) {
  const input = html.match(/<input[^>]*name=["']_csrf["'][^>]*>/i)?.[0];
  if (!input) throw new Error("Identity login form did not contain a CSRF token");
  const value = input.match(/value=["']([^"']+)["']/i)?.[1];
  if (!value) throw new Error("Identity login CSRF token was empty");
  return value;
}

async function acquireToken() {
  const verifier = randomBytes(48).toString("base64url");
  const challenge = createHash("sha256").update(verifier).digest("base64url");
  const authorize = new URL("/oauth2/authorize", identityBaseUrl);
  authorize.search = new URLSearchParams({
    response_type: "code",
    client_id: config.clientId,
    redirect_uri: config.redirectUri,
    scope: config.scopes.join(" "),
    code_challenge: challenge,
    code_challenge_method: "S256"
  });

  let currentUrl = authorize.toString();
  let response = await send(currentUrl);
  while (response.status >= 300 && response.status < 400) {
    currentUrl = resolveLocation(response, currentUrl);
    response = await send(currentUrl);
  }
  if (response.status !== 200 || !currentUrl.endsWith("/login")) {
    throw new Error(`Expected login page, received ${response.status} at ${currentUrl}`);
  }

  const csrf = csrfToken(await response.text());
  const loginUrl = new URL("/login", identityBaseUrl).toString();
  response = await send(loginUrl, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ username: config.username, password: config.password, _csrf: csrf })
  });

  currentUrl = loginUrl;
  for (let redirects = 0; redirects < 10; redirects++) {
    const next = resolveLocation(response, currentUrl);
    if (!next) throw new Error(`Login flow stopped with HTTP ${response.status} at ${currentUrl}`);
    if (next.startsWith(config.redirectUri)) {
      const code = new URL(next).searchParams.get("code");
      if (!code) throw new Error(`Authorization response did not contain a code: ${next}`);
      const tokenResponse = await fetch(new URL("/oauth2/token", identityBaseUrl), {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          grant_type: "authorization_code",
          client_id: config.clientId,
          redirect_uri: config.redirectUri,
          code,
          code_verifier: verifier
        })
      });
      const token = await tokenResponse.json();
      if (!tokenResponse.ok) throw new Error(`Token exchange failed: ${JSON.stringify(token)}`);
      return token;
    }
    currentUrl = next;
    response = await send(currentUrl);
  }
  throw new Error("Identity login exceeded the redirect limit");
}

process.stdout.write(JSON.stringify(await acquireToken()));
