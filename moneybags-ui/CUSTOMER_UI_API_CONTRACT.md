# Public and customer UI adapter contract

The public and customer Oracle JET feature modules call only the same-origin BFF adapter exposed as the AMD module `services/api/gatewayApi`. Every method returns a `Promise` and rejects with an `Error` whose `message` is safe to show to the current user.

The adapter may translate the UI-friendly names below into existing gateway paths, construct query strings, add idempotency headers, and create multipart payloads. Feature pages must not know internal service URLs.

## Authentication session

The modules call these synchronous/asynchronous methods on `services/auth/session`:

```js
session.getUser()
// -> null or { userId, username, roles: string[], customerId, cifId, firstName?, name? }

session.register({ email, password })
// -> Promise<{ userId, username, onboardingStatus: 'PENDING_PROFILE' }>

session.signIn(returnPath)
// redirects through the customer authorization flow

session.signOut()
// -> Promise<void>; clears the BFF session and redirects home
```

Registration is deliberately two-phase. Anonymous registration creates only the Consumer login. The authenticated Profile page then calls `createCustomerProfile`; CIF creation starts KYC. The customer must sign out and sign in once after CIF-to-identity linkage so a new token contains `customer_id`.

## Product catalogue

```js
gatewayApi.listPublicProducts({
  category?, subtype?, status?, productName?, activeOn?, page?, size?
})
// -> Promise<ProductResponse[] | PageResponse<ProductResponse>>

gatewayApi.getPublicProduct(productCode)
// -> Promise<ProductResponse>

gatewayApi.listCreditCardProducts()
// -> Promise<MinimalCreditCardProductResponse[]>
```

## CIF profile and KYC

```js
gatewayApi.createCustomerProfile(CreateCifRequest)
// -> Promise<CifResponse>; authenticated Consumer without customer_id only

gatewayApi.getCustomerProfile(cifId)
// -> Promise<CifResponse>

gatewayApi.updateCustomerProfile(cifId, UpdateCifRequest)
// -> Promise<CifResponse>

gatewayApi.listKycCases(cifId)
// -> Promise<KycResponse[]>

gatewayApi.listKycDocuments(kycId)
// -> Promise<KycDocumentResponse[]>

gatewayApi.uploadKycDocuments(kycId, [{ documentType, file }])
// -> Promise<KycDocumentResponse[]>; adapter builds multipart documentTypes/files
```

## Deposit accounts and fixed deposits

```js
gatewayApi.listDepositAccounts({ customerId, status?, page?, size? })
// -> Promise<AccountSummaryView[] | SpringPage<AccountSummaryView>>

gatewayApi.getDepositAccount(accountId)
// -> Promise<AccountDetailView>

gatewayApi.checkDepositEligibility(EligibilityCheckRequest)
// -> Promise<EligibilityResult>

gatewayApi.openDepositAccount(OpenDepositAccountRequest)
// -> Promise<AccountDetailView>

gatewayApi.listFixedDeposits({ customerId, status?, maturingBefore?, page?, size? })
// -> Promise<FixedDepositView[] | SpringPage<FixedDepositView>>

gatewayApi.getFixedDeposit(fixedDepositId)
// -> Promise<FixedDepositView>

gatewayApi.quoteFixedDeposit(QuoteRequest)
// -> Promise<QuoteResponse>

gatewayApi.bookFixedDeposit(BookingRequest)
// -> Promise<FixedDepositView>
```

## Credit cards

```js
gatewayApi.listCreditCardApplications(cifId)
// -> Promise<ApplicationResponse[]>

gatewayApi.submitCreditCardApplication(ApplicationRequest)
// -> Promise<ApplicationResponse>

gatewayApi.listCreditCardAccounts(cifId)
// -> Promise<AccountResponse[]>

gatewayApi.getCreditCardAccount(accountId)
// -> Promise<AccountResponse>
```

## Payments, bills, and notifications

```js
gatewayApi.listPayments({ customerId, page?, size? })
// -> Promise<PageResponse<PaymentResponse>>

gatewayApi.getPayment(paymentId)
// -> Promise<PaymentResponse>

gatewayApi.createBookTransfer(BookTransferRequest)
// -> Promise<PaymentResponse>

gatewayApi.createCardRepayment(CardRepaymentRequest)
// -> Promise<PaymentResponse>

gatewayApi.listBills({ cifId, page?, size? })
// -> Promise<BillPage>; BFF/customer-safe endpoint must enforce bill ownership

gatewayApi.getBill(billId)
// -> Promise<BillResponse>; BFF/customer-safe endpoint must enforce bill ownership

gatewayApi.listNotifications({ cifId, page?, size? })
// -> Promise<SpringPage<NotificationResponse>>

gatewayApi.getNotification(notificationId)
// -> Promise<NotificationResponse>
```

`listBills` is intentionally CIF-scoped in the browser contract. The adapter must never call or expose `/internal/v1/bills` directly to the browser.
