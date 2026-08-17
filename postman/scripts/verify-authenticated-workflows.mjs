import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const oauthScript = join(scriptDir, "local-oauth.mjs");
const base = {
  gateway: "http://localhost:8080",
  discovery: "http://localhost:8761",
  identity: "http://localhost:8093",
  cif: "http://localhost:8081",
  kyc: "http://localhost:8082",
  product: "http://localhost:8083",
  payments: "http://localhost:8085",
  deposit: "http://localhost:8086",
  card: "http://localhost:8084",
  accounting: "http://localhost:8088",
  notification: "http://localhost:8090"
};
const m2mSecret = process.env.M2M_CLIENT_SECRET || "local-service-secret-change-me";
const results = [];

function record(name, detail = "OK") {
  results.push({ name, detail });
  console.log(`PASS  ${name}${detail === "OK" ? "" : ` — ${detail}`}`);
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function tokenClaim(token, name) {
  if (!token) return null;
  return JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString("utf8"))[name] ?? null;
}

function humanToken(role, overrides = {}) {
  const env = { ...process.env, ...overrides };
  const output = execFileSync(process.execPath, [oauthScript, role], { encoding: "utf8", env });
  return JSON.parse(output).access_token;
}

async function serviceToken(clientId, scope) {
  const response = await fetch(`${base.identity}/oauth2/token`, {
    method: "POST",
    headers: {
      Authorization: `Basic ${Buffer.from(`${clientId}:${m2mSecret}`).toString("base64")}`,
      "Content-Type": "application/x-www-form-urlencoded"
    },
    body: new URLSearchParams({ grant_type: "client_credentials", scope })
  });
  const body = await response.json();
  assert(response.ok && body.access_token, `${clientId} token request failed: ${response.status} ${JSON.stringify(body)}`);
  return body.access_token;
}

async function request(method, url, { token, body, form, headers = {}, expected = [200] } = {}) {
  const requestHeaders = new Headers(headers);
  if (token) requestHeaders.set("Authorization", `Bearer ${token}`);
  requestHeaders.set("X-Correlation-ID", randomUUID());
  requestHeaders.set("X-Tenant-ID", tokenClaim(token, "tenant_id") || "moneybags");
  let payload;
  if (form) {
    payload = form;
  } else if (body !== undefined) {
    requestHeaders.set("Content-Type", "application/json");
    payload = JSON.stringify(body);
  }
  const response = await fetch(url, { method, headers: requestHeaders, body: payload, redirect: "manual" });
  const contentType = response.headers.get("content-type") || "";
  let responseBody;
  if (contentType.includes("json")) responseBody = await response.json();
  else if (contentType.startsWith("text/")) responseBody = await response.text();
  else responseBody = Buffer.from(await response.arrayBuffer());
  if (!expected.includes(response.status)) {
    const printable = Buffer.isBuffer(responseBody) ? `<${responseBody.length} bytes>` : JSON.stringify(responseBody);
    throw new Error(`${method} ${url} expected ${expected.join("/")} but received ${response.status}: ${printable}`);
  }
  return { status: response.status, body: responseBody, headers: response.headers };
}

async function poll(name, action, predicate, attempts = 20) {
  for (let attempt = 0; attempt < attempts; attempt++) {
    const value = await action();
    if (predicate(value)) return value;
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  throw new Error(`${name} did not reach the expected state`);
}

async function verifyHealth() {
  for (const [name, url] of Object.entries(base)) {
    const response = await request("GET", `${url}/actuator/health`, { expected: [200] });
    assert(response.body.status === "UP", `${name} health was not UP`);
  }
  record("all eleven service health endpoints", "HTTP 200 / UP");
}

async function verifyIdentityAndKyc() {
  const admin = humanToken("admin");
  const seededConsumer = humanToken("consumer");
  const runId = Date.now();
  const username = `workflow.${runId}@example.test`;
  const password = `Workflow-${runId}!Aa`;

  await request("GET", `${base.identity}/api/v1/identity/users/not-a-user`, { expected: [401] });
  await request("POST", `${base.identity}/api/v1/identity/users`, {
    token: seededConsumer,
    body: { username, password, customerId: null, tenantId: "moneybags", role: "CONSUMER" },
    expected: [403]
  });
  record("identity authentication and admin role boundary", "401 without JWT, 403 for consumer");

  const createdUser = await request("POST", `${base.gateway}/api/v1/identity/users`, {
    token: admin,
    body: { username, password, customerId: null, tenantId: "moneybags", role: "CONSUMER" },
    expected: [201]
  });
  const userId = createdUser.body.id;
  assert(userId, "Identity creation did not return a user ID");
  record("admin creates consumer identity through gateway", userId);

  let consumer = humanToken("consumer", {
    LOCAL_CONSUMER_USERNAME: username,
    LOCAL_CONSUMER_PASSWORD: password
  });
  const suffix = String(runId).slice(-8).padStart(8, "0");
  const initialEmail = `kyc.${runId}@example.test`;
  const cifRequest = {
    firstName: "Workflow",
    lastName: "Customer",
    dob: "1990-01-15",
    age: 36,
    email: initialEmail,
    number: `98${String(runId).slice(-8)}`,
    address: "100 Integration Street, Pune, Maharashtra 411001, IN",
    employmentType: "SALARIED",
    salary: 75000,
    panNumber: `ABCDE${suffix.slice(-4)}F`,
    aadhaarNumber: String(runId).slice(-12).padStart(12, "0")
  };
  const cifCreated = await request("POST", `${base.gateway}/api/v1/cifs`, {
    token: consumer,
    body: cifRequest,
    expected: [201]
  });
  const cifId = cifCreated.body.cifId;
  assert(cifId && cifCreated.body.kycStatus === "PENDING", "CIF was not created in PENDING state");
  record("consumer creates CIF through gateway", `CIF ${cifId}`);

  await poll("identity-to-CIF link", async () =>
    (await request("GET", `${base.identity}/api/v1/identity/users/${userId}`, { token: admin })).body,
  body => String(body.customerId) === String(cifId));
  consumer = humanToken("consumer", {
    LOCAL_CONSUMER_USERNAME: username,
    LOCAL_CONSUMER_PASSWORD: password
  });
  record("CIF asynchronously links identity", `customer_id=${cifId}`);

  const kycs = await poll("CIF-to-KYC initiation", async () =>
    (await request("GET", `${base.gateway}/api/v1/kycs?cifId=${cifId}`, { token: consumer })).body,
  body => Array.isArray(body) && body.length === 1);
  const kycId = kycs[0].kycId;
  assert(kycs[0].email === initialEmail && kycs[0].kycStatus === "PENDING", "KYC snapshot was not created correctly");
  record("CIF asynchronously creates immutable KYC snapshot", `KYC ${kycId}`);

  await request("GET", `${base.gateway}/api/v1/cifs/${cifId}`, { expected: [401] });
  await request("GET", `${base.gateway}/api/v1/cifs/${cifId + 999999}`, { token: consumer, expected: [403] });
  await request("PATCH", `${base.gateway}/api/v1/kycs/${kycId}/decision`, {
    token: consumer,
    body: { decision: "APPROVED", rejectionReason: null },
    expected: [403]
  });
  record("consumer ownership and KYC reviewer restrictions", "own records allowed; foreign/admin actions denied");

  const updatedEmail = `updated.${runId}@example.test`;
  await request("PUT", `${base.gateway}/api/v1/cifs/${cifId}`, {
    token: consumer,
    body: { ...cifRequest, email: updatedEmail, salary: 80000 }
  });
  const snapshotAfterUpdate = await request("GET", `${base.gateway}/api/v1/kycs/${kycId}`, { token: consumer });
  assert(snapshotAfterUpdate.body.email === initialEmail, "KYC snapshot changed after CIF update");
  record("KYC snapshot remains unchanged after CIF update");

  const form = new FormData();
  for (const type of ["PAN", "AADHAAR", "ADDRESS_PROOF", "SALARY_PROOF"]) {
    form.append("documentTypes", type);
    form.append("files", new Blob([`%PDF-1.4\n${type} integration fixture\n%%EOF`], { type: "application/pdf" }), `${type.toLowerCase()}.pdf`);
  }
  const uploaded = await request("POST", `${base.gateway}/api/v1/kycs/${kycId}/documents`, {
    token: consumer,
    form,
    expected: [201]
  });
  assert(uploaded.body.length === 4, "KYC did not store all four required documents");
  record("consumer uploads all required KYC documents", "PAN, Aadhaar, address, salary");

  const documents = (await request("GET", `${base.gateway}/api/v1/kycs/${kycId}/documents`, { token: consumer })).body;
  const downloaded = await request("GET", `${base.gateway}/api/v1/kycs/${kycId}/documents/${documents[0].documentId}`, { token: consumer });
  assert(downloaded.body.length > 0, "Downloaded KYC document was empty");
  await request("PATCH", `${base.gateway}/api/v1/kycs/${kycId}/documents/${documents[0].documentId}/verification`, {
    token: consumer,
    body: { status: "VERIFIED", remarks: "consumer must not verify" },
    expected: [403]
  });
  record("document metadata/download and reviewer boundary");

  const workQueue = await request("GET", `${base.gateway}/api/v1/kycs/admin/work-queue?cifId=${cifId}`, { token: admin });
  assert(workQueue.body.content.some(item => String(item.kycId) === String(kycId)), "Admin work queue did not contain the KYC case");

  await request("PATCH", `${base.gateway}/api/v1/kycs/${kycId}/documents/${documents[0].documentId}/verification`, {
    token: admin,
    body: { status: "MISMATCH", remarks: "deliberate workflow mismatch" }
  });
  const flagged = await request("GET", `${base.gateway}/api/v1/kycs/${kycId}`, { token: admin });
  assert(flagged.body.kycStatus === "FLAGGED", "MISMATCH did not flag the KYC case");
  await request("PATCH", `${base.gateway}/api/v1/kycs/${kycId}/decision`, {
    token: admin,
    body: { decision: "APPROVED", rejectionReason: null },
    expected: [400]
  });
  record("mismatch flags KYC and pending documents block final decision");

  for (const document of documents.slice(1)) {
    await request("PATCH", `${base.gateway}/api/v1/kycs/${kycId}/documents/${document.documentId}/verification`, {
      token: admin,
      body: { status: "VERIFIED", remarks: "verified by authenticated workflow" }
    });
  }
  const decision = await request("PATCH", `${base.gateway}/api/v1/kycs/${kycId}/decision`, {
    token: admin,
    body: { decision: "APPROVED", rejectionReason: null }
  });
  assert(decision.body.kycStatus === "APPROVED", "KYC final status was not APPROVED");
  assert(decision.body.cifSyncStatus === "SYNCED", `CIF sync was ${decision.body.cifSyncStatus}`);
  assert(decision.body.notificationSyncStatus === "SENT", `Notification sync was ${decision.body.notificationSyncStatus}`);
  record("admin approves flagged KYC after all reviews", "CIF SYNCED, notification SENT");

  const syncedCif = await request("GET", `${base.gateway}/api/v1/cifs/${cifId}`, { token: consumer });
  assert(syncedCif.body.kycStatus === "APPROVED", "CIF did not receive final KYC status");
  const history = await request("GET", `${base.gateway}/api/notifications?cifId=${cifId}&page=0&size=20`, { token: consumer });
  assert(history.body.content?.some(item => item.notificationType === "KYC_APPROVED"), "KYC approval notification was not persisted");
  const notification = history.body.content.find(item => item.notificationType === "KYC_APPROVED");
  await request("GET", `${base.gateway}/api/notifications/${notification.notificationId}`, { token: consumer });
  record("customer reads persisted KYC approval notification");

  const cifServiceToken = await serviceToken("notification-service", "cif:service");
  const contact = await request("GET", `${base.cif}/api/v1/cifs/${cifId}/customer-contact-details`, { token: cifServiceToken });
  assert(contact.body.email === updatedEmail, "CIF service data-minimization endpoint returned wrong contact details");
  await request("GET", `${base.cif}/api/v1/cifs/${cifId}/customer-contact-details`, { token: consumer, expected: [403] });
  record("private CIF projection requires service scope");

  return { admin, consumer, cifId, kycId, runId };
}

async function verifyProductDepositPaymentAndCard(context) {
  const { admin, consumer, cifId, runId } = context;
  const product = await request("GET", `${base.gateway}/api/v1/products/SAV-REG-001`, { token: consumer });
  assert(product.body.productCode === "SAV-REG-001", "Versioned Product Master route returned the wrong product");
  const productServiceToken = await serviceToken("deposit-account-service", "product:read product:validate");
  const validation = await request("POST", `${base.product}/internal/v1/products/SAV-REG-001/validate-account-opening`, {
    token: productServiceToken,
    body: {
      customerId: String(cifId), productVersion: 1, currency: "INR", openingAmount: 2000,
      age: 36, customerType: "INDIVIDUAL", customerCategory: "REGULAR", kycVerified: true
    }
  });
  assert(validation.body.eligible === true, "Product Master rejected an eligible opening");
  record("Product Master public and private validation APIs");

  const opening = {
    customerIds: [String(cifId)],
    primaryCustomerId: String(cifId),
    productId: "SAV-REG-001",
    productVersion: 1,
    currency: "INR",
    openingAmount: 2000,
    servicingBranchId: "BR-001",
    operatingInstruction: "SINGLE",
    nominees: [],
    channel: "POSTMAN",
    externalReference: `AUTH-WORKFLOW-${runId}`
  };
  const eligible = await request("POST", `${base.gateway}/api/deposit-accounts/eligibility-check`, {
    token: consumer,
    body: { customerId: String(cifId), productId: "SAV-REG-001", productVersion: 1, currency: "INR", openingAmount: 2000 }
  });
  assert(eligible.body.eligible === true, `Deposit eligibility failed: ${JSON.stringify(eligible.body)}`);
  const opened = await request("POST", `${base.gateway}/api/deposit-accounts`, {
    token: consumer,
    headers: { "Idempotency-Key": `AUTH-OPEN-${runId}` },
    body: opening,
    expected: [201]
  });
  const accountId = opened.body.accountId;
  assert(accountId && opened.body.product.productId === "SAV-REG-001", "Deposit account did not use the real product snapshot");
  await request("POST", `${base.gateway}/api/deposit-accounts/${accountId}/commands/activate`, {
    token: admin,
    headers: { "Idempotency-Key": `AUTH-ACTIVATE-${runId}` },
    body: { reasonCode: "KYC_APPROVED", reasonText: "Activated by authenticated workflow", effectiveAt: null }
  });
  const balanceBefore = await request("GET", `${base.gateway}/api/deposit-accounts/${accountId}/balance`, { token: consumer });
  assert(Number(balanceBefore.body.available) >= 2000, "Opening balance was not applied");
  record("CIF + Product Master → Deposit account opening", accountId);

  const accountingToken = await serviceToken("payments-service", "accounting:service");
  const sourceRegistration = await request("GET", `${base.accounting}/internal/v1/account-balances/${accountId}`, {
    token: accountingToken,
    expected: [200]
  });
  assert(sourceRegistration.body.accountReference === accountId, "Deposit did not register its accounting subledger");
  const targetOpened = await request("POST", `${base.gateway}/api/deposit-accounts`, {
    token: consumer,
    headers: { "Idempotency-Key": `AUTH-OPEN-TARGET-${runId}` },
    body: { ...opening, openingAmount: 1500, externalReference: `AUTH-WORKFLOW-TARGET-${runId}` },
    expected: [201]
  });
  const targetAccount = targetOpened.body.accountId;
  await request("POST", `${base.gateway}/api/deposit-accounts/${targetAccount}/commands/activate`, {
    token: admin,
    headers: { "Idempotency-Key": `AUTH-ACTIVATE-TARGET-${runId}` },
    body: { reasonCode: "KYC_APPROVED", reasonText: "Activated by authenticated workflow", effectiveAt: null }
  });
  const targetRegistration = await request("GET", `${base.accounting}/internal/v1/account-balances/${targetAccount}`, {
    token: accountingToken,
    expected: [200]
  });
  assert(targetRegistration.body.accountReference === targetAccount,
    "Target deposit did not register its accounting subledger");
  record("Deposit → Accounting lifecycle integration");

  const transfer = await request("POST", `${base.gateway}/api/v1/payments/book-transfers`, {
    token: consumer,
    headers: { "Idempotency-Key": `AUTH-PAY-${runId}` },
    body: {
      requestorCustomerId: String(cifId), sourceAccountId: accountId, targetAccountId: targetAccount,
      amount: 100, currencyCode: "INR", reference: `Authenticated workflow ${runId}`
    },
    expected: [201]
  });
  assert(transfer.body.status === "SETTLED", `Payment status was ${transfer.body.status}`);
  const replay = await request("POST", `${base.gateway}/api/v1/payments/book-transfers`, {
    token: consumer,
    headers: { "Idempotency-Key": `AUTH-PAY-${runId}` },
    body: {
      requestorCustomerId: String(cifId), sourceAccountId: accountId, targetAccountId: targetAccount,
      amount: 100, currencyCode: "INR", reference: `Authenticated workflow ${runId}`
    },
    expected: [200, 201]
  });
  assert(String(replay.body.paymentId) === String(transfer.body.paymentId), "Payment idempotency returned a new payment");
  const balanceAfter = await request("GET", `${base.gateway}/api/deposit-accounts/${accountId}/balance`, { token: consumer });
  assert(Number(balanceAfter.body.available) === Number(balanceBefore.body.available) - 100,
    "Settled payment did not debit the source exactly once");
  if (transfer.body.accountingJournalNumber) {
    await request("GET", `${base.gateway}/api/v1/journals/${transfer.body.accountingJournalNumber}`, { token: admin });
  }
  record("Deposit → Payments → Accounting → Notification transfer", `payment ${transfer.body.paymentId}`);

  const application = await request("POST", `${base.gateway}/api/credit-cards/applications`, {
    token: consumer,
    body: { cifId, productCode: "CC-PLAT-001", requestedCreditLimit: 100000 },
    expected: [201]
  });
  assert(application.body.applicationStatus === "APPROVED", `Card application was ${application.body.applicationStatus}`);
  const accounts = await request("GET", `${base.gateway}/api/credit-cards/accounts/cif/${cifId}`, { token: consumer });
  const cardAccount = accounts.body.find(item => String(item.applicationId) === String(application.body.applicationId));
  assert(cardAccount, "Approved card application did not create an account");
  await request("GET", `${base.gateway}/api/credit-cards/accounts/${cardAccount.accountId}/available-limit`, { token: consumer });
  await request("GET", `${base.gateway}/api/credit-cards/accounts/${cardAccount.accountId}/interest-rate`, { token: consumer });
  await request("POST", `${base.gateway}/api/credit-cards/applications/${application.body.applicationId}/approve`, {
    token: consumer,
    expected: [403]
  });
  record("CIF + Product Master + Accounting → Credit Card workflow", `account ${cardAccount.accountId}`);

  const merchantPaymentRequest = {
    requestorCustomerId: Number(cifId),
    creditCardAccountId: String(cardAccount.accountId),
    merchantId: `MERCHANT-${runId}`,
    amount: 250,
    currencyCode: "INR",
    reference: `Authenticated card purchase ${runId}`
  };
  const merchantPayment = await request("POST", `${base.gateway}/api/v1/payments/credit-card-payment/merchant-payment`, {
    token: consumer,
    headers: { "Idempotency-Key": `AUTH-CARD-PAY-${runId}` },
    body: merchantPaymentRequest,
    expected: [201]
  });
  assert(merchantPayment.body.status === "SETTLED", `Merchant payment status was ${merchantPayment.body.status}`);
  assert(merchantPayment.body.accountingJournalNumber, "Merchant payment did not create an accounting journal");
  const merchantReplay = await request("POST", `${base.gateway}/api/v1/payments/credit-card-payment/merchant-payment`, {
    token: consumer,
    headers: { "Idempotency-Key": `AUTH-CARD-PAY-${runId}` },
    body: merchantPaymentRequest,
    expected: [200, 201]
  });
  assert(String(merchantReplay.body.paymentId) === String(merchantPayment.body.paymentId),
    "Merchant-payment idempotency returned a new payment");
  await request("GET", `${base.gateway}/api/v1/journals/${merchantPayment.body.accountingJournalNumber}`, { token: admin });
  record("Credit Card → Payments → Accounting → Notification merchant purchase",
    `payment ${merchantPayment.body.paymentId}`);
}

async function main() {
  await verifyHealth();
  const context = await verifyIdentityAndKyc();
  await verifyProductDepositPaymentAndCard(context);
  console.log(`\n${results.length} authenticated workflow groups passed.`);
}

main().catch(error => {
  console.error(`\nFAIL  ${error.stack || error.message}`);
  process.exitCode = 1;
});
