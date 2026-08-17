# Banker UI integration contract

All banker view models are AMD modules. They depend on `services/api/gatewayApi`,
`services/auth/session`, and `viewModels/banker/support`. Browser modules never
reference an `/internal/**` URL.

## Session contract

```javascript
session.hasRole('BANK_ADMIN') // boolean, reactive state may be used internally
session.currentUser()         // preferred: { id/sub, username, roles }
// session.user() or an observable `session.user` is also accepted by support.js.
```

## Gateway API contract

Every method returns a `Promise`. The adapter owns credentials, CSRF handling,
correlation IDs, idempotency keys, ETags, `X-Actor-Id`, and normalised errors.

```javascript
// CIF and KYC
getCif(cifId)
getKycQueue({ cifId?, statuses?, page, size })
getKyc(kycId)
getKycDocuments(kycId)
verifyKycDocument(kycId, documentId, { status, remarks })
decideKyc(kycId, { decision, rejectionReason })

// Deposit accounts
searchDepositAccounts({ customerId?, status?, page, size })
getDepositAccount(accountId)
openDepositAccount(openDepositAccountRequest)
commandDepositAccount(accountId, command, statusCommand, version)

// Credit cards
listCardApplicationsByCif(cifId)
getCardApplication(applicationId)
approveCardApplication(applicationId)
rejectCardApplication(applicationId)
listCardAccountsByCif(cifId)
getCardAccount(accountId)
closeCardAccount(accountId)
getCreditCardEodReadiness()

// Product Master
listProducts({ category?, subtype?, status?, productName?, activeOn?, page, size })
getProduct(productCode)
createProduct(productRequest)
updateProduct(productCode, productRequest)
changeProductStatus(productCode, { status, changedBy })
getRateQuote(productCode, { quoteDate, principal?, tenureMonths? })
listInterestPolicies(productCode)
addInterestPolicy(productCode, interestRule)
getBenchmarkHistory(benchmarkCode)
getEffectiveBenchmark(benchmarkCode, effectiveOn)
createBenchmark(benchmarkRateRequest)

// Payments and billing
listPayments(customerId, page, size)
getPayment(paymentId)
cancelPayment(paymentId)
getBill(billId)

// Accounting
listJournals({ businessDate?, sourceService?, eventType?, externalReference?, page, size })
listGlAccounts(page, size)
createGlAccount(glAccountRequest)
listAccountingRules(page, size)
createAccountingRule(accountingRuleRequest)
listSubledgerMappings(page, size)
createSubledgerMapping(subledgerMappingRequest)
getAccountingPeriod(businessDate)
getTrialBalance(runId)
getReconciliation(runId)

// IAM
getIdentityUser(userId)
createIdentityUser({ username, password, customerId?, tenantId, role })
```

Spring `Page` and custom page responses are consumed through their `content`
array and `totalElements` property. Plain arrays are also accepted.

## Route/module mapping

| Route | Module |
|---|---|
| `banker-dashboard` | `banker/dashboard` |
| `banker-customers` | `banker/customers` |
| `banker-kyc` | `banker/kyc` |
| `banker-accounts` | `banker/accounts` |
| `banker-cards` | `banker/cards` |
| `banker-catalogue` | `banker/catalogue` |
| `banker-catalogue-editor` | `banker/productEditor` |
| `banker-catalogue-pricing` | `banker/productPricing` |
| `banker-catalogue-benchmarks` | `banker/productBenchmarks` |
| `banker-payments` | `banker/payments` |
| `banker-billing` | `banker/billing` |
| `banker-accounting` | `banker/accounting` |
| `banker-eod` | `banker/eod` |
| `banker-iam` | `banker/iam` |

## EOD boundary

The EOD cockpit calls only gateway-routed read operations:

- credit-card readiness;
- accounting-period lookup;
- existing trial-balance lookup;
- existing reconciliation lookup.

Payments cutoff, deposit accrual/readiness, fixed-deposit processing, bill close,
trial-balance generation, reconciliation generation, and accounting-period
open/close are shown as pending orchestrator steps. Statement Service is shown as
pending. None of those internal operations are invoked from the browser.
