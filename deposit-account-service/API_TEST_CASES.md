# Deposit Account Service API Test Cases

These cases cover the implemented public and internal HTTP APIs. Automated coverage is in
`DepositAccountControllerIntegrationTest`; the test profile uses H2 in Oracle mode and stubbed upstream clients.

## Automated scenarios

| ID | Scenario | Expected result |
|---|---|---|
| DA-01 | Check account-opening eligibility for a valid CIF and product | 200, eligible decision |
| DA-02 | Open an account with a new idempotency key | 201, pending account and ETag |
| DA-03 | Replay the same opening request and idempotency key | Same account response |
| DA-04 | Reuse an opening key with a different request | 409, `IDEMPOTENCY_KEY_REUSED` |
| DA-05 | Primary CIF is absent from `customerIds` | 400, `PRIMARY_HOLDER_MISSING` |
| DA-06 | Read an account, balance, history and paged search result | 200 with masked account data |
| DA-07 | Add and remove an eligible joint holder | 201 then 204 |
| DA-08 | Attempt to remove the primary holder | 409, `PRIMARY_HOLDER_REQUIRED` |
| DA-09 | Replace nominees whose allocation totals 100 percent | 200 |
| DA-10 | Submit nominee allocations that do not total 100 percent | 400, `INVALID_NOMINEE_ALLOCATION` |
| DA-11 | Upsert a limit using matching path/body types | 200 |
| DA-12 | Submit mismatched path/body limit types | 400, `LIMIT_TYPE_MISMATCH` |
| DA-13 | Add and revoke an account mandate | 201 then 204 |
| DA-14 | Activate a pending account | 200, status `ACTIVE` |
| DA-15 | Submit an unsupported lifecycle command | 400, `UNKNOWN_COMMAND` |
| DA-16 | Submit a stale `If-Match` value | 412, `STALE_ACCOUNT_VERSION` |
| DA-17 | Activate account and query internal eligibility | 200; debit and credit allowed |
| DA-18 | Reserve and settle a book transfer | Source available balance decreases on reserve; settlement debits source and credits target atomically |
| DA-19 | Replay a payment request using the same idempotency key and body | Same reservation/operation response |
| DA-20 | Release an active card-repayment reservation | Blocked balance returns to available balance |
| DA-21 | Reuse a payment idempotency key with a different body | 409, `IDEMPOTENCY_KEY_REUSED` |

## Manual smoke request

With the service running on port 8086:

```http
POST /api/v1/deposit-accounts
Content-Type: application/json
Idempotency-Key: manual-open-001
X-Correlation-Id: manual-correlation-001
```

```json
{
  "customerIds": ["CIF-MANUAL-001"],
  "primaryCustomerId": "CIF-MANUAL-001",
  "productId": "SAV-001",
  "productVersion": 1,
  "currency": "INR",
  "openingAmount": 0,
  "servicingBranchId": "BR-001",
  "operatingInstruction": "SINGLE",
  "nominees": [],
  "channel": "BRANCH",
  "externalReference": "MANUAL-001"
}
```

Expected status: `201 Created`. The response contains a masked account number and an `ETag` header.
