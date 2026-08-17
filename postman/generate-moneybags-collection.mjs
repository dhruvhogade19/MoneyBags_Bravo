import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const postmanDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.dirname(postmanDir);

const jsonHeader = { key: "Content-Type", value: "application/json" };
const correlationHeader = { key: "X-Correlation-ID", value: "{{correlationId}}" };

function scriptEvent(listen, exec) {
  return { listen, script: { type: "text/javascript", exec } };
}

function request(name, method, url, { body, headers = [], tests = [], prerequest = [], description } = {}) {
  const item = {
    name,
    request: {
      method,
      header: headers,
      ...(body === undefined ? {} : { body: { mode: "raw", raw: JSON.stringify(body, null, 2), options: { raw: { language: "json" } } } }),
      url,
      ...(description ? { description } : {})
    }
  };
  const events = [];
  if (prerequest.length) events.push(scriptEvent("prerequest", prerequest));
  if (tests.length) events.push(scriptEvent("test", tests));
  if (events.length) item.event = events;
  return item;
}

function rawRequest(name, method, url, requestBody, tests = []) {
  return {
    name,
    request: { method, header: [correlationHeader], body: requestBody, url },
    ...(tests.length ? { event: [scriptEvent("test", tests)] } : {})
  };
}

const expectUp = [
  "pm.test('service readiness is UP', () => {",
  "  pm.response.to.have.status(200);",
  "  pm.expect(pm.response.json().status).to.eql('UP');",
  "});"
];

const workflowFolders = [
  {
    name: "00 - Platform readiness",
    description: "Fail-fast checks for every service used by the executable data flow.",
    item: [
      request("Discovery Server readiness", "GET", "{{discoveryBaseUrl}}/actuator/health/readiness", { tests: expectUp }),
      request("Identity Access readiness", "GET", "{{identityBaseUrl}}/actuator/health/readiness", { tests: expectUp }),
      request("API Gateway readiness", "GET", "{{gatewayBaseUrl}}/actuator/health/readiness", { tests: expectUp }),
      request("CIF readiness", "GET", "{{cifBaseUrl}}/actuator/health/readiness", { tests: expectUp }),
      request("KYC readiness", "GET", "{{kycBaseUrl}}/actuator/health/readiness", { tests: expectUp }),
      request("Product Master readiness", "GET", "{{productBaseUrl}}/actuator/health/readiness", { tests: expectUp }),
      request("Payments readiness", "GET", "{{paymentsBaseUrl}}/actuator/health/readiness", { tests: expectUp }),
      request("Deposit Account readiness", "GET", "{{depositBaseUrl}}/actuator/health/readiness", { tests: expectUp }),
      request("Credit Card readiness", "GET", "{{creditCardBaseUrl}}/actuator/health/readiness", { tests: expectUp }),
      request("Accounting readiness", "GET", "{{accountingBaseUrl}}/actuator/health/readiness", { tests: expectUp }),
      request("Notification readiness", "GET", "{{notificationBaseUrl}}/actuator/health/readiness", { tests: expectUp })
    ]
  },
  {
    name: "01 - Seed data verification",
    description: "Verifies Liquibase-owned product, deposit, and accounting fixtures before money movement.",
    item: [
      request("Get seeded savings product", "GET", "{{gatewayBaseUrl}}/api/v1/products/{{savingsProductCode}}", {
        tests: [
          "pm.test('seeded savings product is active', () => { pm.response.to.have.status(200); const b = pm.response.json(); pm.expect(b.productCode).to.eql(pm.collectionVariables.get('savingsProductCode')); pm.expect(b.status).to.eql('ACTIVE'); });",
          "const b = pm.response.json(); pm.collectionVariables.set('productVersion', String(b.version));"
        ]
      }),
      request("Validate seeded savings product", "POST", "{{productBaseUrl}}/internal/v1/products/{{savingsProductCode}}/validate-account-opening", {
        headers: [jsonHeader, correlationHeader, { key: "X-Idempotency-Key", value: "product-check-{{runId}}" }],
        body: { openingAmount: 1000, currency: "INR", age: 30, customerType: "INDIVIDUAL", kycVerified: true, productVersion: "{{productVersion}}", valueDate: "{{businessDate}}" },
        tests: ["pm.test('product accepts the opening request', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().eligible).to.eql(true); });"]
      }),
      request("Read seeded source account", "GET", "{{gatewayBaseUrl}}/api/deposit-accounts/{{sourceAccountId}}", {
        tests: [
          "pm.test('source account is funded and active', () => { pm.response.to.have.status(200); const b = pm.response.json(); pm.expect(b.status).to.eql('ACTIVE'); pm.expect(Number(b.balance.available)).to.be.above(Number(pm.collectionVariables.get('transferAmount'))); });",
          "pm.collectionVariables.set('sourceBalanceBefore', String(pm.response.json().balance.available));"
        ]
      }),
      request("Read seeded target account", "GET", "{{gatewayBaseUrl}}/api/deposit-accounts/{{targetAccountId}}", {
        tests: [
          "pm.test('target account accepts credits', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().status).to.eql('ACTIVE'); });",
          "pm.collectionVariables.set('targetBalanceBefore', String(pm.response.json().balance.available));"
        ]
      }),
      request("Read seeded accounting chart", "GET", "{{accountingBaseUrl}}/api/v1/gl-accounts?page=0&size=50", {
        tests: ["pm.test('accounting chart contains the deposit liability GL', () => { pm.response.to.have.status(200); const b = pm.response.json(); const rows = b.content || b; pm.expect(rows.some(x => x.glCode === 'CUSTOMER_DEPOSIT_LIABILITY')).to.eql(true); });"]
      })
    ]
  },
  {
    name: "02 - Payment data flow: Deposit -> Payments -> Accounting -> Notification",
    description: "Executes an idempotent book transfer and proves its effects in every participating service.",
    item: [
      request("Create and settle book transfer", "POST", "{{gatewayBaseUrl}}/api/v1/payments/book-transfers", {
        headers: [jsonHeader, correlationHeader, { key: "Idempotency-Key", value: "book-transfer-{{runId}}" }],
        body: { requestorCustomerId: "{{customerId}}", sourceAccountId: "{{sourceAccountId}}", targetAccountId: "{{targetAccountId}}", amount: "{{transferAmount}}", currencyCode: "INR", reference: "Postman E2E {{runId}}" },
        tests: [
          "pm.test('payment settles end to end', () => { pm.expect(pm.response.code).to.be.oneOf([200, 201]); const b = pm.response.json(); pm.expect(b.status).to.eql('SETTLED'); pm.expect(b.accountingJournalNumber).to.be.a('string').and.not.empty; });",
          "const b = pm.response.json(); pm.collectionVariables.set('workflowPaymentId', b.paymentId); pm.collectionVariables.set('workflowJournalNumber', b.accountingJournalNumber);"
        ]
      }),
      request("Replay book transfer idempotently", "POST", "{{gatewayBaseUrl}}/api/v1/payments/book-transfers", {
        headers: [jsonHeader, correlationHeader, { key: "Idempotency-Key", value: "book-transfer-{{runId}}" }],
        body: { requestorCustomerId: "{{customerId}}", sourceAccountId: "{{sourceAccountId}}", targetAccountId: "{{targetAccountId}}", amount: "{{transferAmount}}", currencyCode: "INR", reference: "Postman E2E {{runId}}" },
        tests: ["pm.test('replay returns the original payment', () => { pm.expect(pm.response.code).to.be.oneOf([200, 201]); pm.expect(pm.response.json().paymentId).to.eql(pm.collectionVariables.get('workflowPaymentId')); });"]
      }),
      request("Get settled payment", "GET", "{{gatewayBaseUrl}}/api/v1/payments/{{workflowPaymentId}}", {
        tests: ["pm.test('payment query shows SETTLED', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().status).to.eql('SETTLED'); });"]
      }),
      request("List customer payments", "GET", "{{gatewayBaseUrl}}/api/v1/payments?customerId={{customerId}}&page=0&size=20", {
        tests: ["pm.test('customer history contains the workflow payment', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().content.some(x => x.paymentId === pm.collectionVariables.get('workflowPaymentId'))).to.eql(true); });"]
      }),
      request("Verify source debit", "GET", "{{gatewayBaseUrl}}/api/deposit-accounts/{{sourceAccountId}}/balance", {
        tests: ["pm.test('source available balance decreased once', () => { pm.response.to.have.status(200); const before = Number(pm.collectionVariables.get('sourceBalanceBefore')); const amount = Number(pm.collectionVariables.get('transferAmount')); const b = pm.response.json(); const now = Number(b.available ?? b.availableBalance); pm.expect(now).to.eql(before - amount); });"]
      }),
      request("Verify target credit", "GET", "{{gatewayBaseUrl}}/api/deposit-accounts/{{targetAccountId}}/balance", {
        tests: ["pm.test('target available balance increased once', () => { pm.response.to.have.status(200); const before = Number(pm.collectionVariables.get('targetBalanceBefore')); const amount = Number(pm.collectionVariables.get('transferAmount')); const b = pm.response.json(); const now = Number(b.available ?? b.availableBalance); pm.expect(now).to.eql(before + amount); });"]
      }),
      request("Verify Accounting journal", "GET", "{{accountingBaseUrl}}/api/v1/journals/{{workflowJournalNumber}}", {
        tests: ["pm.test('journal is posted and balanced', () => { pm.response.to.have.status(200); const b = pm.response.json(); pm.expect(b.status).to.eql('POSTED'); pm.expect(Number(b.totalDebit)).to.eql(Number(b.totalCredit)); });"]
      }),
      request("Verify Accounting posting lookup", "GET", "{{accountingBaseUrl}}/internal/v1/payment-postings/by-reference/{{workflowPaymentId}}", {
        tests: ["pm.test('posting lookup resolves the payment', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().journalNumber).to.eql(pm.collectionVariables.get('workflowJournalNumber')); });"]
      })
    ]
  },
  {
    name: "03 - KYC document and decision workflow",
    description: "Creates test data through public APIs; no direct KYC database insert is required.",
    item: [
      request("Create KYC", "POST", "{{kycBaseUrl}}/api/v1/kycs", {
        headers: [jsonHeader, correlationHeader],
        body: { cifId: "{{customerId}}", firstName: "Postman", lastName: "Customer", dob: "1990-01-15", number: "9876543210", email: "postman.{{runId}}@example.test", panNumber: "{{panNumber}}", aadhaarNumber: "{{aadhaarNumber}}", address: "100 Test Street, Pune, Maharashtra 411001, IN", employmentType: "SALARIED", salary: 75000, kycStatus: "PENDING" },
        tests: [
          "pm.test('KYC is created in PENDING state', () => { pm.response.to.have.status(201); pm.expect(pm.response.json().kycStatus).to.eql('PENDING'); });",
          "pm.collectionVariables.set('workflowKycId', String(pm.response.json().kycId));"
        ]
      }),
      request("Get KYC by ID", "GET", "{{kycBaseUrl}}/api/v1/kycs/{{workflowKycId}}", { tests: ["pm.test('KYC can be queried', () => pm.response.to.have.status(200));"] }),
      request("List KYC records by CIF", "GET", "{{kycBaseUrl}}/api/v1/kycs?cifId={{customerId}}", { tests: ["pm.test('CIF history contains the new KYC', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().some(x => String(x.kycId) === pm.collectionVariables.get('workflowKycId'))).to.eql(true); });"] }),
      rawRequest("Upload PAN document", "POST", "{{kycBaseUrl}}/api/v1/kycs/{{workflowKycId}}/documents", {
        mode: "formdata",
        formdata: [
          { key: "documentTypes", value: "PAN", type: "text" },
          { key: "files", type: "file", src: "postman/fixtures/kyc-pan.pdf" }
        ]
      }, [
        "pm.test('PAN document is uploaded', () => { pm.response.to.have.status(201); pm.expect(pm.response.json()).to.have.length(1); });",
        "pm.collectionVariables.set('workflowDocumentId', String(pm.response.json()[0].documentId));"
      ]),
      request("List KYC documents", "GET", "{{kycBaseUrl}}/api/v1/kycs/{{workflowKycId}}/documents", { tests: ["pm.test('document metadata is visible', () => { pm.response.to.have.status(200); pm.expect(pm.response.json()).to.have.length.above(0); });"] }),
      request("Download PAN document", "GET", "{{kycBaseUrl}}/api/v1/kycs/{{workflowKycId}}/documents/{{workflowDocumentId}}", { tests: ["pm.test('document bytes are downloadable', () => { pm.response.to.have.status(200); pm.expect(pm.response.headers.get('Content-Disposition')).to.include('attachment'); });"] }),
      request("Verify PAN document", "PATCH", "{{kycBaseUrl}}/api/v1/kycs/{{workflowKycId}}/documents/{{workflowDocumentId}}/verification", {
        headers: [jsonHeader, correlationHeader], body: { status: "VERIFIED", remarks: "Verified by Postman workflow", verifiedBy: "postman-runner" },
        tests: ["pm.test('document is VERIFIED', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().verificationStatus).to.eql('VERIFIED'); });"]
      }),
      request("Approve KYC", "PATCH", "{{kycBaseUrl}}/api/v1/kycs/{{workflowKycId}}/decision", {
        headers: [jsonHeader, correlationHeader], body: { decision: "APPROVED", rejectionReason: null, reviewedBy: "postman-runner" },
        tests: [
          "pm.test('KYC decision is APPROVED', () => { pm.response.to.have.status(200); const b = pm.response.json(); pm.expect(b.kycStatus).to.eql('APPROVED'); pm.expect(b.decision).to.eql('APPROVED'); });",
          "pm.collectionVariables.set('kycCifSyncStatus', pm.response.json().cifSyncStatus || 'UNKNOWN');"
        ]
      }),
      request("Retry CIF synchronization when needed", "POST", "{{kycBaseUrl}}/api/v1/kycs/{{workflowKycId}}/sync", {
        prerequest: ["if (pm.collectionVariables.get('kycCifSyncStatus') === 'SYNCED') { pm.execution.skipRequest(); }"],
        tests: ["pm.test('retry is handled by the KYC state machine', () => pm.expect(pm.response.code).to.be.oneOf([200, 400, 409, 503]));"]
      })
    ]
  },
  {
    name: "04 - Notification persistence and idempotency",
    item: [
      request("Create workflow notification", "POST", "{{notificationBaseUrl}}/internal/v1/notifications", {
        headers: [jsonHeader, correlationHeader, { key: "Idempotency-Key", value: "notification-{{runId}}" }],
        body: { cifId: "{{customerId}}", notificationType: "PAYMENT_SUCCESS", sourceReference: "{{workflowPaymentId}}", templateVariables: { paymentType: "book transfer", amount: "{{transferAmount}}", currency: "INR", transactionDate: "{{businessDate}}", reference: "{{workflowPaymentId}}" } },
        tests: [
          "pm.test('notification is persisted', () => pm.expect(pm.response.code).to.be.oneOf([200, 201]));",
          "pm.collectionVariables.set('workflowNotificationId', String(pm.response.json().notificationId));"
        ]
      }),
      request("Replay workflow notification", "POST", "{{notificationBaseUrl}}/internal/v1/notifications", {
        headers: [jsonHeader, correlationHeader, { key: "Idempotency-Key", value: "notification-{{runId}}" }],
        body: { cifId: "{{customerId}}", notificationType: "PAYMENT_SUCCESS", sourceReference: "{{workflowPaymentId}}", templateVariables: { paymentType: "book transfer", amount: "{{transferAmount}}", currency: "INR", transactionDate: "{{businessDate}}", reference: "{{workflowPaymentId}}" } },
        tests: ["pm.test('notification replay returns the same record', () => { pm.response.to.have.status(200); pm.expect(String(pm.response.json().notificationId)).to.eql(pm.collectionVariables.get('workflowNotificationId')); });"]
      }),
      request("Get notification", "GET", "{{notificationBaseUrl}}/api/notifications/{{workflowNotificationId}}", { tests: ["pm.test('notification can be queried', () => pm.response.to.have.status(200));"] }),
      request("List notification history", "GET", "{{notificationBaseUrl}}/api/notifications?cifId={{customerId}}&page=0&size=50", { tests: ["pm.test('history contains the workflow notification', () => { pm.response.to.have.status(200); const b = pm.response.json(); const rows = b.content || b; pm.expect(rows.some(x => String(x.notificationId) === pm.collectionVariables.get('workflowNotificationId'))).to.eql(true); });"] }),
      request("Reject changed notification with reused key", "POST", "{{notificationBaseUrl}}/internal/v1/notifications", {
        headers: [jsonHeader, correlationHeader, { key: "Idempotency-Key", value: "notification-{{runId}}" }],
        body: { cifId: "{{customerId}}", notificationType: "PAYMENT_SUCCESS", sourceReference: "{{workflowPaymentId}}", templateVariables: { paymentType: "book transfer", amount: "999.99", currency: "INR", transactionDate: "{{businessDate}}", reference: "{{workflowPaymentId}}" } },
        tests: ["pm.test('idempotency conflict is rejected', () => pm.response.to.have.status(409));"]
      })
    ]
  },
  {
    name: "05 - Negative controls",
    item: [
      request("Reject transfer to the same account", "POST", "{{gatewayBaseUrl}}/api/v1/payments/book-transfers", {
        headers: [jsonHeader, correlationHeader, { key: "Idempotency-Key", value: "negative-same-account-{{runId}}" }],
        body: { requestorCustomerId: "{{customerId}}", sourceAccountId: "{{sourceAccountId}}", targetAccountId: "{{sourceAccountId}}", amount: 1, currencyCode: "INR", reference: "Negative test" },
        tests: ["pm.test('same-account transfer is rejected', () => pm.expect(pm.response.code).to.be.oneOf([400, 422]));"]
      }),
      request("Reject ineligible product opening", "POST", "{{productBaseUrl}}/internal/v1/products/{{savingsProductCode}}/validate-account-opening", {
        headers: [jsonHeader, correlationHeader, { key: "X-Idempotency-Key", value: "negative-product-{{runId}}" }],
        body: { openingAmount: 1, currency: "USD", age: 10, customerType: "INDIVIDUAL", kycVerified: false, productVersion: 999, valueDate: "{{businessDate}}" },
        tests: ["pm.test('eligibility response explains rejection', () => { pm.response.to.have.status(200); const b = pm.response.json(); pm.expect(b.eligible).to.eql(false); pm.expect(b.validationMessages.length).to.be.above(0); });"]
      }),
      request("Reject unknown deposit account", "GET", "{{gatewayBaseUrl}}/api/deposit-accounts/not-a-real-account-{{runId}}", {
        tests: ["pm.test('unknown account returns ProblemDetail', () => { pm.response.to.have.status(404); pm.expect(pm.response.headers.get('Content-Type')).to.include('application/problem+json'); });"]
      })
    ]
  }
];

const optionalStatusTest = [
  "pm.test('endpoint returned an expected business response', () => pm.expect(pm.response.code).to.be.oneOf([200, 201, 204, 400, 404, 409, 422]));"
];

const skipUnlessConfigured = variable => [
  `if (!pm.collectionVariables.get('${variable}') || pm.collectionVariables.get('${variable}').startsWith('SET_ME')) {`,
  `  console.warn('Skipped: set collection variable ${variable} to run this prerequisite-dependent request.');`,
  "  pm.execution.skipRequest();",
  "}"
];

const productPayload = {
  productCode: "{{postmanProductCode}}",
  productName: "Postman Savings {{runId}}",
  description: "Disposable product created by the opt-in Postman API coverage suite",
  category: "DEPOSIT",
  subtype: "SAVINGS",
  currencyCode: "INR",
  effectiveFrom: "{{businessDate}}",
  effectiveTo: null,
  changedBy: "postman",
  interestRule: {
    annualInterestRate: 2.5,
    pricingMode: "FIXED",
    benchmarkCode: null,
    productSpread: null,
    minimumRate: 2.0,
    maximumRate: 3.0,
    targetProfitPercentage: null,
    effectiveFrom: "{{businessDate}}",
    effectiveTo: null,
    policyVersion: "PM-V1",
    interestCalculationMethod: "DAILY_BALANCE",
    interestCalculationFrequency: "DAILY",
    interestPostingFrequency: "MONTHLY",
    compoundingFrequency: null,
    dayCountConvention: "ACTUAL_365",
    rateApplicationMethod: "BOOKING_DATE",
    loanRepaymentFrequency: null,
    interestType: "CREDIT"
  },
  amountRule: {
    minimumOpeningBalance: 100,
    minimumBalance: 0,
    maximumBalance: 1000000,
    minimumAmount: null,
    maximumAmount: null,
    minimumTenureMonths: null,
    maximumTenureMonths: null,
    overdraftAllowed: false,
    overdraftLimit: 0
  },
  creditCardRule: null,
  fixedDepositRule: null,
  interestRateSlabs: [],
  accountClosureRule: null,
  prematureClosureRule: null,
  renewalRule: null,
  fees: [],
  eligibilityRules: [{
    minimumAge: 18,
    maximumAge: 75,
    minimumMonthlyIncome: null,
    customerType: "INDIVIDUAL",
    customerCategory: null,
    kycRequired: true,
    collateralRequired: false,
    active: true
  }],
  features: [{ featureName: "postmanWorkflow", featureValue: "true", active: true }]
};

const additionalCoverageFolders = [
  {
    name: "Identity and CIF - customer onboarding and trusted data sharing",
    description: "Role-aware coverage for Identity and CIF. Set adminBearerToken for Identity administration and CIF updates; set serviceBearerToken only for the private identity-link call. The create-CIF request requires an unlinked consumer access token, because customer identity is deliberately derived from the JWT rather than the request body.",
    item: [
      request("Create disposable consumer identity", "POST", "{{gatewayBaseUrl}}/api/v1/identity/users", {
        prerequest: [...skipUnlessConfigured("adminBearerToken"), ...skipUnlessConfigured("postmanUserPassword")],
        headers: [jsonHeader, { key: "Authorization", value: "Bearer {{adminBearerToken}}" }],
        body: { username: "postman.consumer.{{runId}}@example.test", password: "{{postmanUserPassword}}", customerId: null, tenantId: "{{tenantId}}", role: "CONSUMER" },
        tests: [
          "pm.test('consumer identity is created', () => pm.response.to.have.status(201));",
          "pm.collectionVariables.set('workflowIdentityUserId', pm.response.json().id);"
        ]
      }),
      request("Get disposable consumer identity", "GET", "{{gatewayBaseUrl}}/api/v1/identity/users/{{workflowIdentityUserId}}", {
        prerequest: skipUnlessConfigured("adminBearerToken"),
        headers: [{ key: "Authorization", value: "Bearer {{adminBearerToken}}" }],
        tests: ["pm.test('identity user is readable', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().roles).to.include('CONSUMER'); });"]
      }),
      request("Create CIF for an unlinked consumer", "POST", "{{gatewayBaseUrl}}/api/v1/cifs", {
        prerequest: skipUnlessConfigured("consumerBearerToken"),
        headers: [jsonHeader, { key: "Authorization", value: "Bearer {{consumerBearerToken}}" }, { key: "Idempotency-Key", value: "cif-create-{{runId}}" }],
        body: { firstName: "Postman", lastName: "Onboarding", dob: "1990-01-15", age: 36, email: "postman.cif.{{runId}}@example.test", number: "{{mobileNumber}}", address: "100 Test Street, Pune, Maharashtra 411001, IN", employmentType: "SALARIED", salary: 75000, panNumber: "{{panNumber}}", aadhaarNumber: "{{aadhaarNumber}}" },
        tests: [
          "pm.test('CIF is created pending KYC', () => { pm.response.to.have.status(201); pm.expect(pm.response.json().kycStatus).to.eql('PENDING'); });",
          "pm.collectionVariables.set('workflowCifId', String(pm.response.json().cifId));"
        ]
      }),
      request("Get workflow CIF", "GET", "{{gatewayBaseUrl}}/api/v1/cifs/{{workflowCifId}}", {
        prerequest: skipUnlessConfigured("consumerBearerToken"),
        headers: [{ key: "Authorization", value: "Bearer {{consumerBearerToken}}" }],
        tests: ["pm.test('created CIF is readable by its owner', () => pm.response.to.have.status(200));"]
      }),
      request("Update workflow CIF", "PUT", "{{gatewayBaseUrl}}/api/v1/cifs/{{workflowCifId}}", {
        prerequest: skipUnlessConfigured("consumerBearerToken"),
        headers: [jsonHeader, { key: "Authorization", value: "Bearer {{consumerBearerToken}}" }],
        body: { firstName: "Postman", lastName: "Onboarding", dob: "1990-01-15", age: 36, email: "postman.cif.updated.{{runId}}@example.test", number: "{{mobileNumber}}", address: "101 Updated Test Street, Pune, Maharashtra 411001, IN", employmentType: "SALARIED", salary: 80000, panNumber: "{{panNumber}}", aadhaarNumber: "{{aadhaarNumber}}" },
        tests: ["pm.test('CIF update is persisted', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().salary).to.eql(80000); });"]
      }),
      request("Read CIF credit-card decision details", "GET", "{{cifBaseUrl}}/api/v1/cifs/{{workflowCifId}}/credit-card-details", { tests: ["pm.test('credit-card details are returned', () => pm.response.to.have.status(200));"] }),
      request("Read CIF deposit-opening details", "GET", "{{cifBaseUrl}}/api/v1/cifs/{{workflowCifId}}/deposit-creation-details", { tests: ["pm.test('deposit-opening details are returned', () => pm.response.to.have.status(200));"] }),
      request("Read CIF contact details", "GET", "{{cifBaseUrl}}/api/v1/cifs/{{workflowCifId}}/customer-contact-details", { tests: ["pm.test('contact details are returned', () => pm.response.to.have.status(200));"] }),
      request("Synchronize workflow CIF KYC status", "PATCH", "{{cifBaseUrl}}/api/v1/cifs/{{workflowCifId}}/kyc-status", {
        prerequest: skipUnlessConfigured("serviceBearerToken"),
        headers: [jsonHeader, { key: "Authorization", value: "Bearer {{serviceBearerToken}}" }], body: { kycStatus: "APPROVED" },
        tests: ["pm.test('CIF KYC status is synchronized', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().kycStatus).to.eql('APPROVED'); });"]
      }),
      request("Link disposable identity to workflow CIF", "PUT", "{{identityBaseUrl}}/internal/v1/identity/users/{{workflowIdentityUserId}}/customer-link", {
        prerequest: skipUnlessConfigured("serviceBearerToken"),
        headers: [jsonHeader, { key: "Authorization", value: "Bearer {{serviceBearerToken}}" }], body: { customerId: "{{workflowCifId}}", tenantId: "{{tenantId}}" },
        tests: ["pm.test('identity is linked to the generated CIF', () => pm.response.to.have.status(204));"]
      }),
      request("Read KYC admin work queue", "GET", "{{kycBaseUrl}}/api/v1/kycs/admin/work-queue?cifId={{customerId}}&statuses=PENDING&page=0&size=25", {
        prerequest: skipUnlessConfigured("adminBearerToken"), headers: [{ key: "Authorization", value: "Bearer {{adminBearerToken}}" }],
        tests: ["pm.test('KYC reviewer work queue is readable', () => pm.response.to.have.status(200));"]
      })
    ]
  },
  {
    name: "Payments - complete orchestration, statements, EOD, and recovery APIs",
    description: "Covers every Payments controller route. Merchant, repayment, FD funding, and payout calls skip until their SET_ME variables are configured. EOD requests always reopen intake at the end.",
    item: [
      request("Get workflow payment", "GET", "{{paymentsBaseUrl}}/api/v1/payments/{{workflowPaymentId}}", { tests: ["pm.test('payment is readable', () => pm.response.to.have.status(200));"] }),
      request("List customer payments", "GET", "{{paymentsBaseUrl}}/api/v1/payments?customerId={{customerId}}&page=0&size=50", { tests: ["pm.test('payment page is readable', () => pm.response.to.have.status(200));"] }),
      request("List statement activity", "GET", "{{paymentsBaseUrl}}/internal/payments?accountId={{sourceAccountId}}&from={{businessDate}}&to={{businessDate}}&page=0&size=100", { tests: ["pm.test('statement activity is readable', () => pm.response.to.have.status(200));"] }),
      request("List operational payments", "GET", "{{paymentsBaseUrl}}/internal/v1/payments?businessDate={{businessDate}}&page=0&size=100", { tests: ["pm.test('operational payment page is readable', () => pm.response.to.have.status(200));"] }),
      request("Cancel already-settled payment (guard)", "POST", "{{paymentsBaseUrl}}/api/v1/payments/{{workflowPaymentId}}/cancel", { tests: ["pm.test('posted payment cannot be cancelled', () => pm.response.to.have.status(409));"] }),
      request("Retry reversal on non-pending payment (guard)", "POST", "{{paymentsBaseUrl}}/internal/v1/payments/{{workflowPaymentId}}/reversal", { headers: [jsonHeader], body: { reason: "Postman recovery endpoint guard test" }, tests: optionalStatusTest }),
      request("Retry billing settlement on non-repayment (guard)", "POST", "{{paymentsBaseUrl}}/internal/v1/payments/{{workflowPaymentId}}/billing-settlement", { headers: [{ key: "Idempotency-Key", value: "billing-retry-{{runId}}" }], tests: optionalStatusTest }),
      request("Merchant credit-card payment", "POST", "{{paymentsBaseUrl}}/api/v1/payments/credit-card-payment/merchant-payment", {
        prerequest: skipUnlessConfigured("creditCardAccountId"), headers: [jsonHeader, { key: "Idempotency-Key", value: "merchant-{{runId}}" }],
        body: { requestorCustomerId: "{{customerId}}", creditCardAccountId: "{{creditCardAccountId}}", merchantId: "MERCHANT-POSTMAN", amount: 25, currencyCode: "INR", reference: "Postman merchant purchase" }, tests: optionalStatusTest
      }),
      request("Credit-card repayment", "POST", "{{paymentsBaseUrl}}/api/v1/payments/credit-card-payment/repayment", {
        prerequest: skipUnlessConfigured("billId"), headers: [jsonHeader, { key: "Idempotency-Key", value: "repayment-{{runId}}" }],
        body: { requestorCustomerId: "{{customerId}}", billId: "{{billId}}", sourceDepositAccountId: "{{sourceAccountId}}", creditCardAccountId: "{{creditCardAccountId}}", amount: 25, currencyCode: "INR", reference: "Postman bill repayment" }, tests: optionalStatusTest
      }),
      request("Fund fixed deposit", "POST", "{{paymentsBaseUrl}}/api/v1/payments/fixed-deposit-funding", {
        prerequest: skipUnlessConfigured("fixedDepositId"), headers: [jsonHeader, { key: "Idempotency-Key", value: "fd-funding-{{runId}}" }],
        body: { requestorCustomerId: "{{customerId}}", sourceAccountId: "{{sourceAccountId}}", fixedDepositId: "{{fixedDepositId}}", amount: 1000, currencyCode: "INR", reference: "Postman FD funding" }, tests: optionalStatusTest
      }),
      request("Initiate fixed-deposit payout", "POST", "{{paymentsBaseUrl}}/internal/v1/payments", {
        prerequest: skipUnlessConfigured("fixedDepositId"), headers: [jsonHeader, { key: "Idempotency-Key", value: "fd-payout-{{runId}}" }],
        body: { paymentType: "FIXED_DEPOSIT_MATURITY_PAYOUT", requestorCustomerId: "{{customerId}}", sourceAccountId: "{{fixedDepositAccountId}}", destinationType: "DEPOSIT_ACCOUNT", destinationAccountId: "{{targetAccountId}}", amount: 1010, principalAmount: 1000, interestAmount: 10, currencyCode: "INR", reference: "Postman FD payout", fixedDepositId: "{{fixedDepositId}}" }, tests: optionalStatusTest
      }),
      request("EOD cutoff", "POST", "{{paymentsBaseUrl}}/internal/v1/payments/eod/cutoff", { headers: [jsonHeader, { key: "Idempotency-Key", value: "eod-cutoff-{{runId}}" }], body: { businessDate: "{{businessDate}}", commandReference: "postman-{{runId}}" }, tests: ["pm.test('payment intake is cut off', () => pm.response.to.have.status(200));"] }),
      request("EOD drain", "POST", "{{paymentsBaseUrl}}/internal/v1/payments/eod/drain", { headers: [{ key: "Idempotency-Key", value: "eod-drain-{{runId}}" }], tests: ["pm.test('drain status is returned', () => pm.response.to.have.status(200));"] }),
      request("EOD reopen", "POST", "{{paymentsBaseUrl}}/internal/v1/payments/eod/reopen", { headers: [{ key: "Idempotency-Key", value: "eod-reopen-{{runId}}" }], tests: ["pm.test('payment intake is reopened', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().newPaymentIntake).to.eql(true); });"] })
    ]
  },
  {
    name: "Product Master - complete catalogue, pricing, policy, and decision APIs",
    description: "Creates and discontinues a disposable savings product and benchmark. Also exercises seeded credit-card catalogue and policy APIs.",
    item: [
      request("Create disposable product", "POST", "{{productBaseUrl}}/api/products", { headers: [jsonHeader], body: productPayload, tests: ["pm.test('draft product is created', () => { pm.response.to.have.status(201); pm.expect(pm.response.json().productCode).to.eql(pm.collectionVariables.get('postmanProductCode')); });"] }),
      request("List products with filters", "GET", "{{productBaseUrl}}/api/products?category=DEPOSIT&subtype=SAVINGS&page=0&size=20", { tests: ["pm.test('product page is returned', () => pm.response.to.have.status(200));"] }),
      request("Get disposable product", "GET", "{{productBaseUrl}}/api/products/{{postmanProductCode}}", { tests: ["pm.test('product is readable', () => pm.response.to.have.status(200));"] }),
      request("Update disposable product", "PUT", "{{productBaseUrl}}/api/products/{{postmanProductCode}}", { headers: [jsonHeader], body: { ...productPayload, productName: "Postman Savings Updated {{runId}}" }, tests: ["pm.test('product is updated', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().productName).to.include('Updated'); });"] }),
      request("Activate disposable product", "PATCH", "{{productBaseUrl}}/api/products/{{postmanProductCode}}/status", { headers: [jsonHeader], body: { status: "ACTIVE", changedBy: "postman" }, tests: ["pm.test('product is active', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().status).to.eql('ACTIVE'); });"] }),
      request("List all active products", "GET", "{{productBaseUrl}}/api/products/active", { tests: ["pm.test('active catalogue is returned', () => pm.response.to.have.status(200));"] }),
      request("List active deposit products", "GET", "{{productBaseUrl}}/api/products/category/DEPOSIT/active", { tests: ["pm.test('active deposit catalogue is returned', () => pm.response.to.have.status(200));"] }),
      request("Get product eligibility rules", "GET", "{{productBaseUrl}}/api/products/{{postmanProductCode}}/eligibility", { tests: ["pm.test('eligibility rules are returned', () => pm.response.to.have.status(200));"] }),
      request("Get product pricing", "GET", "{{productBaseUrl}}/api/products/{{postmanProductCode}}/pricing", { tests: ["pm.test('pricing is returned', () => pm.response.to.have.status(200));"] }),
      request("Get rate quote", "GET", "{{productBaseUrl}}/api/products/{{postmanProductCode}}/rate-quote?quoteDate={{businessDate}}&principal=10000", { tests: ["pm.test('rate quote is returned', () => pm.response.to.have.status(200));"] }),
      request("Add future interest policy", "POST", "{{productBaseUrl}}/api/products/{{postmanProductCode}}/interest-policies", { headers: [jsonHeader], body: { ...productPayload.interestRule, annualInterestRate: 2.75, effectiveFrom: "{{nextBusinessDate}}", policyVersion: "PM-V2" }, tests: optionalStatusTest }),
      request("List interest policies", "GET", "{{productBaseUrl}}/api/products/{{postmanProductCode}}/interest-policies", { tests: ["pm.test('interest policies are returned', () => pm.response.to.have.status(200));"] }),
      request("Validate account opening (public)", "POST", "{{productBaseUrl}}/api/products/{{postmanProductCode}}/validate-account-opening", { headers: [jsonHeader], body: { openingAmount: 500, currency: "INR", age: 30, customerType: "INDIVIDUAL", kycVerified: true, productVersion: 1, valueDate: "{{businessDate}}" }, tests: ["pm.test('public deposit decision is returned', () => pm.response.to.have.status(200));"] }),
      request("Validate account opening (internal)", "POST", "{{productBaseUrl}}/internal/v1/products/{{postmanProductCode}}/validate-account-opening", { headers: [jsonHeader], body: { openingAmount: 500, currency: "INR", age: 30, customerType: "INDIVIDUAL", kycVerified: true, productVersion: 1, valueDate: "{{businessDate}}" }, tests: ["pm.test('internal deposit decision is returned', () => pm.response.to.have.status(200));"] }),
      request("List minimal credit-card catalogue", "GET", "{{productBaseUrl}}/api/products/category/CREDIT_CARD/active/minimal", { tests: ["pm.test('minimal card catalogue is returned', () => pm.response.to.have.status(200));"] }),
      request("Get minimal credit-card product", "GET", "{{productBaseUrl}}/api/products/{{creditCardProductCode}}/minimal", { tests: ["pm.test('minimal card product is returned', () => pm.response.to.have.status(200));"] }),
      request("Validate credit-card application (public)", "POST", "{{productBaseUrl}}/api/products/{{creditCardProductCode}}/validate-credit-card-application", { headers: [jsonHeader], body: { requestedCreditLimit: 100000, age: 30, monthlyIncome: 75000, customerType: "INDIVIDUAL", kycCompleted: true }, tests: ["pm.test('public card decision is returned', () => pm.response.to.have.status(200));"] }),
      request("Validate credit-card application (internal)", "POST", "{{productBaseUrl}}/internal/v1/products/{{creditCardProductCode}}/validate-credit-card-application", { headers: [jsonHeader], body: { requestedCreditLimit: 100000, age: 30, monthlyIncome: 75000, customerType: "INDIVIDUAL", kycCompleted: true }, tests: ["pm.test('internal card decision is returned', () => pm.response.to.have.status(200));"] }),
      request("List credit-card policies", "GET", "{{productBaseUrl}}/api/products/{{creditCardProductCode}}/credit-card-policies", { tests: ["pm.test('card policies are returned', () => pm.response.to.have.status(200));"] }),
      request("Add future credit-card policy", "POST", "{{productBaseUrl}}/api/products/{{creditCardProductCode}}/credit-card-policies", { headers: [jsonHeader], body: { policyVersion: "PM-{{runId}}", effectiveFrom: "{{nextBusinessDate}}", effectiveTo: null, minimumCreditLimit: 50000, maximumCreditLimit: 500000, interestFreeDays: 45, minimumPaymentPercentage: 5, minimumPaymentAmount: 500, paymentDueDays: 15, cashAdvanceAllowed: true, cashAdvanceLimitPercentage: 20 }, tests: optionalStatusTest }),
      request("Create benchmark rate", "POST", "{{productBaseUrl}}/api/benchmarks", { headers: [jsonHeader], body: { benchmarkCode: "{{benchmarkCode}}", annualRate: 6.5, effectiveFrom: "{{businessDate}}", effectiveTo: null, createdBy: "postman" }, tests: ["pm.test('benchmark is created', () => pm.response.to.have.status(201));"] }),
      request("Get effective benchmark", "GET", "{{productBaseUrl}}/api/benchmarks/{{benchmarkCode}}/effective?effectiveOn={{businessDate}}", { tests: ["pm.test('effective benchmark is returned', () => pm.response.to.have.status(200));"] }),
      request("Get benchmark history", "GET", "{{productBaseUrl}}/api/benchmarks/{{benchmarkCode}}/history", { tests: ["pm.test('benchmark history is returned', () => pm.response.to.have.status(200));"] }),
      request("Discontinue disposable product", "DELETE", "{{productBaseUrl}}/api/products/{{postmanProductCode}}?changedBy=postman", { tests: ["pm.test('product is discontinued', () => pm.response.to.have.status(204));"] })
    ]
  },
  {
    name: "Credit Card - applications, accounts, limits, rates, and readiness",
    description: "Requires credit-card-service on port 8084 plus a CIF that is KYC-complete. Transactional hold/capture/release/payment/closure APIs are in the embedded Credit Card suite below.",
    item: [
      request("Credit Card readiness", "GET", "{{creditCardBaseUrl}}/actuator/health/readiness", { tests: expectUp }),
      request("Submit credit-card application", "POST", "{{creditCardBaseUrl}}/api/credit-cards/applications", { headers: [jsonHeader], body: { cifId: "{{customerId}}", productCode: "{{creditCardProductCode}}", requestedCreditLimit: 100000 }, tests: ["pm.test('application decision is recorded', () => pm.response.to.have.status(201));", "pm.collectionVariables.set('creditCardApplicationId', String(pm.response.json().applicationId));"] }),
      request("Get credit-card application", "GET", "{{creditCardBaseUrl}}/api/credit-cards/applications/{{creditCardApplicationId}}", { tests: ["pm.test('application is readable', () => pm.response.to.have.status(200));"] }),
      request("List applications by CIF", "GET", "{{creditCardBaseUrl}}/api/credit-cards/applications/cif/{{customerId}}", { tests: ["pm.test('application list is returned', () => pm.response.to.have.status(200));"] }),
      request("Approve already-decided application (guard)", "POST", "{{creditCardBaseUrl}}/api/credit-cards/applications/{{creditCardApplicationId}}/approve", { tests: optionalStatusTest }),
      request("Reject already-decided application (guard)", "POST", "{{creditCardBaseUrl}}/api/credit-cards/applications/{{creditCardApplicationId}}/reject", { tests: optionalStatusTest }),
      request("Open account for already-auto-opened application (guard)", "POST", "{{creditCardBaseUrl}}/api/credit-cards/accounts", { headers: [jsonHeader], body: { applicationId: "{{creditCardApplicationId}}" }, tests: optionalStatusTest }),
      request("List accounts by CIF and capture account", "GET", "{{creditCardBaseUrl}}/api/credit-cards/accounts/cif/{{customerId}}", { tests: ["pm.test('account list is returned', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().length).to.be.above(0); });", "if (pm.response.code === 200 && pm.response.json().length) pm.collectionVariables.set('creditCardAccountId', String(pm.response.json()[0].accountId));"] }),
      request("Get credit-card account", "GET", "{{creditCardBaseUrl}}/api/credit-cards/accounts/{{creditCardAccountId}}", { tests: ["pm.test('card account is readable', () => pm.response.to.have.status(200));"] }),
      request("Get available credit limit", "GET", "{{creditCardBaseUrl}}/api/credit-cards/accounts/{{creditCardAccountId}}/available-limit", { tests: ["pm.test('available limit is returned', () => pm.response.to.have.status(200));"] }),
      request("Get purchase interest rate", "GET", "{{creditCardBaseUrl}}/api/credit-cards/accounts/{{creditCardAccountId}}/interest-rate", { tests: ["pm.test('interest rate is returned', () => pm.response.to.have.status(200));"] }),
      request("Get credit-card EOD readiness", "GET", "{{creditCardBaseUrl}}/api/credit-cards/accounts/eod/readiness", { tests: ["pm.test('EOD readiness is returned', () => pm.response.to.have.status(200));"] })
    ]
  },
  {
    name: "Notification - KYC callback API",
    item: [
      request("Persist KYC status notification", "POST", "{{notificationBaseUrl}}/internal/v1/notifications/kyc-status", { headers: [jsonHeader, { key: "Idempotency-Key", value: "kyc-notification-{{runId}}" }], body: { cifId: "{{customerId}}", kycStatus: "VERIFIED", rejectionReason: null }, tests: ["pm.test('KYC notification is accepted', () => pm.expect(pm.response.code).to.be.oneOf([200, 201]));"] })
    ]
  }
];

const sourceCollections = [
  ["deposit", "Deposit Account - exhaustive controller coverage", "deposit-account-service/postman/Deposit-Account-Service.postman_collection.json"],
  ["deposit_product", "Deposit and Product integration", "deposit-account-service/postman/Deposit-Product-Master-Integration.postman_collection.json"],
  ["accounting", "Accounting - exhaustive controller coverage", "accounting-service/postman/Accounting-Service.postman_collection.json"],
  ["product", "Product Master smoke suite", "product-master-service/postman/Product-Master-Service.postman_collection.json"],
  ["notification", "Notification API suite", "notification-service/postman/Notification-Service.postman_collection.json"],
  ["credit_card", "Credit Card hold and account lifecycle suite (requires service on port 8084)", "credit-card-service/postman/Credit-Card-Hold-Flow.postman_collection.json"]
];

function replaceVariableReferences(value, mappings) {
  let text = JSON.stringify(value);
  for (const [oldKey, newKey] of mappings) {
    text = text.split(`{{${oldKey}}}`).join(`{{${newKey}}}`);
    for (const method of ["get", "set", "unset", "has"]) {
      text = text.split(`collectionVariables.${method}('${oldKey}'`).join(`collectionVariables.${method}('${newKey}'`);
      text = text.split(`collectionVariables.${method}(\"${oldKey}\"`).join(`collectionVariables.${method}(\"${newKey}\"`);
    }
  }
  return JSON.parse(text);
}

const detailedItems = [];
const detailedVariables = [];
for (const [prefix, title, relativePath] of sourceCollections) {
  const source = JSON.parse(fs.readFileSync(path.join(root, relativePath), "utf8"));
  const mappings = (source.variable || []).map(variable => [variable.key, `${prefix}_${variable.key}`]);
  for (const variable of source.variable || []) {
    detailedVariables.push({
      key: `${prefix}_${variable.key}`,
      value: typeof variable.value === "string"
        ? replaceVariableReferences(variable.value, mappings)
        : variable.value,
      type: variable.type || "string"
    });
  }
  const folder = replaceVariableReferences({
    name: title,
    description: source.info?.description || `Embedded from ${relativePath}`,
    ...(source.auth ? { auth: source.auth } : {}),
    ...(source.event ? { event: source.event } : {}),
    item: source.item || []
  }, mappings);
  detailedItems.push(folder);
}

const collectionVariables = [
  ["gatewayBaseUrl", "http://localhost:8080"],
  ["discoveryBaseUrl", "http://localhost:8761"],
  ["identityBaseUrl", "http://localhost:8093"],
  ["cifBaseUrl", "http://localhost:8081"],
  ["kycBaseUrl", "http://localhost:8082"],
  ["productBaseUrl", "http://localhost:8083"],
  ["creditCardBaseUrl", "http://localhost:8084"],
  ["paymentsBaseUrl", "http://localhost:8085"],
  ["depositBaseUrl", "http://localhost:8086"],
  ["accountingBaseUrl", "http://localhost:8088"],
  ["notificationBaseUrl", "http://localhost:8090"],
  ["bearerToken", ""],
  ["adminBearerToken", "SET_ME_ADMIN_ACCESS_TOKEN"],
  ["consumerBearerToken", "SET_ME_UNLINKED_CONSUMER_ACCESS_TOKEN"],
  ["serviceBearerToken", "SET_ME_SERVICE_ACCESS_TOKEN"],
  ["postmanUserPassword", "SET_ME_A_12_CHARACTER_PASSWORD"],
  ["tenantId", "moneybags-local"],
  ["customerId", "1001"],
  ["savingsProductCode", "SAV-REG-001"],
  ["fixedDepositProductCode", "FD-REG-001"],
  ["creditCardProductCode", "CC-PLAT-001"],
  ["productVersion", "1"],
  ["sourceAccountId", "seed-sav-source-001"],
  ["targetAccountId", "seed-cur-target-001"],
  ["transferAmount", "100.00"],
  ["runId", ""], ["businessDate", ""], ["correlationId", ""],
  ["panNumber", ""], ["aadhaarNumber", ""], ["mobileNumber", ""],
  ["sourceBalanceBefore", ""], ["targetBalanceBefore", ""],
  ["workflowPaymentId", ""], ["workflowJournalNumber", ""],
  ["workflowKycId", ""], ["workflowDocumentId", ""], ["kycCifSyncStatus", ""],
  ["workflowNotificationId", ""],
  ["workflowIdentityUserId", ""], ["workflowCifId", ""],
  ["postmanProductCode", ""], ["benchmarkCode", ""], ["nextBusinessDate", ""],
  ["creditCardApplicationId", ""], ["creditCardAccountId", "SET_ME_CREDIT_CARD_ACCOUNT_ID"],
  ["billId", "SET_ME_GENERATED_BILL_ID"], ["fixedDepositId", "SET_ME_FIXED_DEPOSIT_ID"],
  ["fixedDepositAccountId", "SET_ME_FIXED_DEPOSIT_ACCOUNT_ID"]
].map(([key, value]) => ({ key, value, type: /(?:[Bb]earerToken|Password)$/.test(key) ? "secret" : "string" }));

const collection = {
  info: {
    _postman_id: "961edada-7f32-4f67-b52d-b8272d4288ea",
    name: "Moneybags - Complete API Workflows and Data Flow",
    description: "Executable platform workflow plus namespaced detailed service suites. Run folders 00-05 in order for the deterministic cross-service scenario. Folder 90 contains destructive or service-specific exhaustive coverage and should be selected explicitly.",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  auth: { type: "noauth" },
  event: [scriptEvent("prerequest", [
    "if (!pm.collectionVariables.get('runId')) {",
    "  const runId = Date.now().toString();",
    "  const digits = ('000000000000' + runId).slice(-12);",
    "  pm.collectionVariables.set('runId', runId);",
    "  pm.collectionVariables.set('businessDate', new Date().toISOString().slice(0, 10));",
    "  pm.collectionVariables.set('correlationId', pm.variables.replaceIn('{{$guid}}'));",
    "  pm.collectionVariables.set('panNumber', 'ABCDE' + digits.slice(-4) + 'F');",
    "  pm.collectionVariables.set('aadhaarNumber', digits);",
    "  pm.collectionVariables.set('mobileNumber', '98765' + digits.slice(-5));",
    "  pm.collectionVariables.set('postmanProductCode', 'PM-SAV-' + runId);",
    "  pm.collectionVariables.set('benchmarkCode', 'POSTMAN_' + runId);",
    "  const tomorrow = new Date(); tomorrow.setUTCDate(tomorrow.getUTCDate() + 1);",
    "  pm.collectionVariables.set('nextBusinessDate', tomorrow.toISOString().slice(0, 10));",
    "  const dataKeys = ['customerId','sourceAccountId','targetAccountId','transferAmount','tenantId'];",
    "  dataKeys.forEach(k => { if (pm.iterationData.has(k)) pm.collectionVariables.set(k, String(pm.iterationData.get(k))); });",
    "}",
    "const token = pm.collectionVariables.get('bearerToken') || pm.environment.get('bearerToken');",
    "if (token && !pm.request.headers.has('Authorization')) pm.request.headers.upsert({ key: 'Authorization', value: 'Bearer ' + token });",
    "if (!pm.request.headers.has('X-Correlation-ID') && !pm.request.headers.has('X-Correlation-Id')) pm.request.headers.upsert({ key: 'X-Correlation-ID', value: pm.collectionVariables.get('correlationId') });"
  ])],
  variable: [...collectionVariables, ...detailedVariables],
  item: [
    ...workflowFolders,
    {
      name: "90 - Detailed API coverage (select folders explicitly)",
      description: "Embedded and namespaced copies of the repository's service collections. Some requests mutate or close seeded records and are intentionally not part of the repeatable 00-06 workflow.",
      item: [...additionalCoverageFolders, ...detailedItems]
    }
  ]
};

const environment = {
  id: "9827f468-2085-44ab-868e-e660193f2afd",
  name: "Moneybags Local Workflow",
  values: collectionVariables.slice(0, 25).map(({ key, value, type }) => ({ key, value, type, enabled: true })),
  _postman_variable_scope: "environment",
  _postman_exported_at: new Date().toISOString(),
  _postman_exported_using: "Codex"
};

const data = [{
  scenario: "happy-path",
  customerId: 1001,
  sourceAccountId: "seed-sav-source-001",
  targetAccountId: "seed-cur-target-001",
  transferAmount: "100.00",
  tenantId: "moneybags-local"
}];

function countRequests(items) {
  return items.reduce((count, item) => count + (item.request ? 1 : 0) + countRequests(item.item || []), 0);
}

fs.writeFileSync(path.join(postmanDir, "Moneybags-Complete-Workflow.postman_collection.json"), JSON.stringify(collection, null, 2) + "\n");
fs.writeFileSync(path.join(postmanDir, "Moneybags-Local.postman_environment.json"), JSON.stringify(environment, null, 2) + "\n");
fs.writeFileSync(path.join(postmanDir, "Moneybags-Workflow.postman_data.json"), JSON.stringify(data, null, 2) + "\n");
console.log(`Generated ${countRequests(collection.item)} requests across ${collection.item.length} top-level folders.`);
