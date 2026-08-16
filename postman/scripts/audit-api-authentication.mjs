import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const oauthScript = join(scriptDir, "local-oauth.mjs");
const gateway = "http://localhost:8080";
const services = [
  { name: "cif-service", baseUrl: "http://localhost:8081", gatewayPrefixes: ["/api/v1/cifs"] },
  { name: "kyc-service", baseUrl: "http://localhost:8082", gatewayPrefixes: ["/api/v1/kycs"] },
  { name: "product-master-service", baseUrl: "http://localhost:8083", gatewayPrefixes: ["/api/products", "/api/v1/products", "/api/benchmarks"] },
  { name: "payments-service", baseUrl: "http://localhost:8085", gatewayPrefixes: ["/api/v1/payments"] },
  { name: "deposit-account-service", baseUrl: "http://localhost:8086", gatewayPrefixes: ["/api/deposit-accounts"] },
  { name: "credit-card-service", baseUrl: "http://localhost:8087", gatewayPrefixes: ["/api/credit-cards"] },
  { name: "accounting-service", baseUrl: "http://localhost:8088", gatewayPrefixes: ["/api/v1/journals", "/api/v1/gl-accounts", "/api/v1/accounting-rules", "/api/v1/subledger-mappings", "/api/v1/trial-balances", "/api/v1/reconciliation", "/api/v1/accounting-periods"] },
  { name: "notification-service", baseUrl: "http://localhost:8090", gatewayPrefixes: ["/api/notifications"] }
];

function adminToken() {
  const output = execFileSync(process.execPath, [oauthScript, "admin"], {
    encoding: "utf8",
    env: process.env
  });
  return JSON.parse(output).access_token;
}

function materializePath(path) {
  return path.replaceAll(/\{[^}]+\}/g, "1");
}

function isGatewayRoute(service, path) {
  return service.gatewayPrefixes.some(prefix => path === prefix || path.startsWith(`${prefix}/`));
}

async function fetchJson(url, token) {
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Tenant-ID": "moneybags",
      "X-Correlation-ID": randomUUID()
    }
  });
  if (!response.ok) {
    throw new Error(`Could not read ${url}: HTTP ${response.status} ${await response.text()}`);
  }
  return response.json();
}

async function unauthenticatedStatus(method, url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(url, {
      method,
      headers: {
        "Content-Type": "application/json",
        "X-Tenant-ID": "moneybags",
        "X-Correlation-ID": randomUUID(),
        "Idempotency-Key": randomUUID()
      },
      body: ["GET", "HEAD", "DELETE"].includes(method) ? undefined : "{}",
      redirect: "manual",
      signal: controller.signal
    });
    await response.arrayBuffer();
    return response.status;
  } finally {
    clearTimeout(timer);
  }
}

function operations(openApi) {
  const allowed = new Set(["get", "post", "put", "patch", "delete"]);
  return Object.entries(openApi.paths ?? {}).flatMap(([path, pathItem]) =>
    Object.keys(pathItem)
      .filter(method => allowed.has(method.toLowerCase()))
      .map(method => ({ method: method.toUpperCase(), path }))
  );
}

async function auditTarget(label, baseUrl, operationList) {
  const failures = [];
  const rejectedByStatus = new Map();
  for (const operation of operationList) {
    const path = materializePath(operation.path);
    const status = await unauthenticatedStatus(operation.method, `${baseUrl}${path}`);
    if (![401, 403].includes(status)) failures.push({ ...operation, path, status });
    else rejectedByStatus.set(status, (rejectedByStatus.get(status) ?? 0) + 1);
  }
  const statusSummary = [...rejectedByStatus.entries()].map(([status, count]) => `${count}x HTTP ${status}`).join(", ");
  console.log(`${failures.length === 0 ? "PASS" : "FAIL"}  ${label}: ${operationList.length - failures.length}/${operationList.length} operations rejected unauthenticated access (${statusSummary})`);
  for (const failure of failures) {
    console.log(`      ${failure.method} ${failure.path} returned HTTP ${failure.status}`);
  }
  return { tested: operationList.length, failures };
}

async function main() {
  const token = adminToken();
  let directTested = 0;
  let gatewayTested = 0;
  const allFailures = [];

  for (const service of services) {
    const openApi = await fetchJson(`${service.baseUrl}/v3/api-docs`, token);
    const documented = operations(openApi);
    const direct = await auditTarget(`${service.name} direct`, service.baseUrl, documented);
    directTested += direct.tested;
    allFailures.push(...direct.failures.map(failure => ({ target: `${service.name} direct`, ...failure })));

    const gatewayOperations = documented.filter(operation => isGatewayRoute(service, operation.path));
    const viaGateway = await auditTarget(`${service.name} via gateway`, gateway, gatewayOperations);
    gatewayTested += viaGateway.tested;
    allFailures.push(...viaGateway.failures.map(failure => ({ target: `${service.name} via gateway`, ...failure })));
  }

  const identityOperations = [
    { method: "POST", path: "/api/v1/identity/users" },
    { method: "GET", path: "/api/v1/identity/users/1" },
    { method: "PUT", path: "/internal/v1/identity/users/1/customer-link" }
  ];
  const identityDirect = await auditTarget("identity-access-service direct", "http://localhost:8093", identityOperations);
  directTested += identityDirect.tested;
  allFailures.push(...identityDirect.failures.map(failure => ({ target: "identity-access-service direct", ...failure })));
  const identityGateway = await auditTarget("identity-access-service via gateway", gateway, identityOperations.filter(operation => operation.path.startsWith("/api/v1/identity/")));
  gatewayTested += identityGateway.tested;
  allFailures.push(...identityGateway.failures.map(failure => ({ target: "identity-access-service via gateway", ...failure })));

  console.log(`\nAuthentication audit total: ${directTested} direct operations and ${gatewayTested} gateway operations.`);
  if (allFailures.length > 0) {
    throw new Error(`${allFailures.length} operation(s) did not reject unauthenticated access with HTTP 401/403`);
  }
  console.log("All audited API operations rejected unauthenticated access.");
}

main().catch(error => {
  console.error(`\nAUTHENTICATION AUDIT FAILED: ${error.stack ?? error.message}`);
  process.exitCode = 1;
});
