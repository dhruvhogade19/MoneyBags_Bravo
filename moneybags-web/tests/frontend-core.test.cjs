const test = require("node:test");
const assert = require("node:assert/strict");
const {
  buildLogoutUrl,
  finiteNumber,
  formatDate,
  IdempotencyKeyStore,
  isNavigationPathActive
} = require("../test-output/utils.js");

test("money formatting accepts finite values and rejects malformed values", () => {
  assert.equal(finiteNumber("1250.50"), 1250.5);
  assert.equal(finiteNumber(0), 0);
  assert.equal(finiteNumber("not-money"), undefined);
  assert.equal(finiteNumber(Infinity), undefined);
});

test("date formatting never exposes an invalid date", () => {
  assert.equal(formatDate(undefined), "—");
  assert.equal(formatDate("not-a-date"), "—");
  assert.notEqual(formatDate("2026-08-16"), "—");
});

test("logical retries reuse their idempotency key", () => {
  let sequence = 0;
  const store = new IdempotencyKeyStore(() => `key-${++sequence}`);
  const command = { source: "A", target: "B", amount: 100 };
  assert.equal(store.keyFor(command), "key-1");
  assert.equal(store.keyFor({ ...command }), "key-1");
  assert.equal(store.keyFor({ ...command, amount: 101 }), "key-2");
  store.reset();
  assert.equal(store.keyFor({ ...command, amount: 101 }), "key-3");
});

test("OIDC logout targets Identity and returns to the configured frontend", () => {
  const result = new URL(buildLogoutUrl("http://localhost:8093/", "http://localhost:8000/", "id.token"));
  assert.equal(result.pathname, "/connect/logout");
  assert.equal(result.searchParams.get("id_token_hint"), "id.token");
  assert.equal(result.searchParams.get("post_logout_redirect_uri"), "http://localhost:8000/");
});

test("operations rules navigation remains active on the mappings view", () => {
  assert.equal(isNavigationPathActive("/ops/accounting/rules", "/ops/accounting/mappings", "/ops/overview"), true);
  assert.equal(isNavigationPathActive("/app/accounts", "/app/accounts/ACC-1", "/app/overview"), true);
  assert.equal(isNavigationPathActive("/ops/overview", "/ops/accounting/mappings", "/ops/overview"), false);
});
