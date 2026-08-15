# MoneyBags: Core Process Flows

This document shows the primary user and data flows across the MoneyBags microservices. Public requests enter through the API Gateway; service-to-service arrows represent trusted internal calls.

## 1. Customer Onboarding and KYC

```text
┌──────────┐   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
│ Customer │──▶│ API Gateway │──▶│ CIF Service │──▶│ KYC Service │
└──────────┘   └─────────────┘   │ Create CIF  │   │ Create case │
                                  └──────┬──────┘   └──────┬──────┘
                                         │                 │
                                         │                 ▼
                                         │         ┌─────────────┐
                                         │         │ KYC Review  │
                                         │         │ Approve /   │
                                         │         │ Reject      │
                                         │         └──────┬──────┘
                                         │                │
                                         ▼                ▼
                                  ┌─────────────┐  ┌──────────────────┐
                                  │ CIF Service │  │ Notification Svc │
                                  │ Update KYC  │  │ Send KYC outcome │
                                  │ status      │  └──────────────────┘
                                  └─────────────┘
```

## 2. Deposit Account Opening

```text
┌──────────┐   ┌─────────────┐   ┌─────────────────┐
│ Customer │──▶│ API Gateway │──▶│ Deposit Account │
└──────────┘   └─────────────┘   │ Service         │
                                  └───────┬────────┘
                         ┌────────────────┼────────────────┐
                         ▼                ▼                ▼
              ┌────────────────┐ ┌────────────────┐ ┌──────────────┐
              │ CIF Service    │ │ KYC Service    │ │ Product      │
              │ Customer valid?│ │ KYC approved?  │ │ Master       │
              └────────────────┘ └────────────────┘ │ Product active?│
                                                     │ Opening rules? │
                                                     │ Minimum balance│
                                                     └───────┬────────┘
                                                             │
                                                             ▼
                                               ┌────────────────────────┐
                                               │ Create Deposit Account │
                                               │ + holders + limits     │
                                               └───────────┬────────────┘
                                                           ▼
                                               ┌────────────────────────┐
                                               │ Return Account Number  │
                                               └────────────────────────┘
```

## 3. Credit Card Application and Validation

```text
┌──────────┐   ┌─────────────┐   ┌──────────────────┐
│ Customer │──▶│ API Gateway │──▶│ Credit Card Svc  │
└──────────┘   └─────────────┘   │ Application      │
                                  └───────┬─────────┘
                         ┌────────────────┼────────────────┐
                         ▼                ▼                ▼
              ┌────────────────┐ ┌────────────────┐ ┌─────────────────┐
              │ CIF Service    │ │ KYC Service    │ │ Product Master  │
              │ Customer status│ │ KYC status     │ │ Card eligibility│
              │ Income/profile │ │ Documents      │ │ Limit band      │
              └────────────────┘ └────────────────┘ │ Fees / terms     │
                                                     └────────┬────────┘
                                                              ▼
                                                ┌─────────────────────────┐
                                                │ Card Decision & Limit   │
                                                │ Approval                │
                                                └───────┬─────────┬───────┘
                                                        │         │
                                                        ▼         ▼
                                                 ┌──────────┐ ┌──────────┐
                                                 │ Create   │ │ Reject / │
                                                 │ Card A/c │ │ Review   │
                                                 └──────────┘ └──────────┘
```

## 4. Deposit-to-Deposit Book Transfer

```text
┌──────────┐   ┌─────────────┐   ┌─────────────────┐
│ Customer │──▶│ API Gateway │──▶│ Payments Service│
└──────────┘   └─────────────┘   └────────┬────────┘
                                           │
                                           ▼
                              ┌────────────────────────┐
                              │ Deposit Account Service│
                              │ Validate both accounts │
                              │ Reserve source funds   │
                              └───────────┬────────────┘
                                          ▼
                              ┌────────────────────────┐
                              │ Accounting Service     │
                              │ Post balanced journal  │
                              └───────────┬────────────┘
                                          ▼
                              ┌────────────────────────┐
                              │ Deposit Account Service│
                              │ Debit source           │
                              │ Credit destination     │
                              │ Capture reservation    │
                              └───────────┬────────────┘
                                          ▼
                              ┌────────────────────────┐
                              │ Notification Service   │
                              │ Transfer confirmation  │
                              └────────────────────────┘
```

## 5. Credit Card Bill Payment

```text
┌──────────┐   ┌─────────────┐   ┌─────────────────┐
│ Customer │──▶│ API Gateway │──▶│ Payments Service│
└──────────┘   └─────────────┘   └────────┬────────┘
                                           │
                      ┌────────────────────┼────────────────────┐
                      ▼                    ▼                    │
       ┌────────────────────────┐ ┌────────────────────────┐    │
       │ Deposit Account Service│ │ Credit Card Service    │    │
       │ Source account valid?  │ │ Card status / balance  │    │
       │ Reserve payment funds  │ │ Payment eligibility    │    │
       └───────────┬────────────┘ └────────────────────────┘    │
                   └────────────────────┬────────────────────────┘
                                        ▼
                           ┌────────────────────────┐
                           │ Accounting Service     │
                           │ Post payment journal   │
                           └───────────┬────────────┘
                                       ▼
        ┌──────────────────────────────┴──────────────────────────────┐
        ▼                                                             ▼
┌───────────────────────┐                                  ┌──────────────────┐
│ Deposit Account Svc   │                                  │ Credit Card Svc  │
│ Capture reserved debit│                                  │ Reduce outstanding│
└───────────────────────┘                                  │ Restore limit    │
                                                           └─────────┬────────┘
                                                                     ▼
                                                       ┌────────────────────┐
                                                       │ Payment = SETTLED  │
                                                       └────────────────────┘
```

## 6. Credit Card Bill Generation

```text
┌─────────────────────┐
│ EOD Scheduler / Run │
└──────────┬──────────┘
           ▼
┌─────────────────────────┐       ┌────────────────────────┐
│ Bill Generation Service │──────▶│ Product Master Service │
│ Start billing cycle     │       │ Billing cycle          │
└──────────┬──────────────┘       │ Interest / fee rules   │
           │                      │ Product version        │
           │                      └────────────────────────┘
           ▼
┌─────────────────────────┐       ┌────────────────────────┐
│ Credit Card Service     │       │ Accounting Service     │
│ Card snapshot           │       │ Posted financial       │
│ Outstanding / limit     │       │ activity / journals    │
└──────────┬──────────────┘       └──────────┬─────────────┘
           └─────────────────────┬───────────┘
                                 ▼
                    ┌─────────────────────────┐
                    │ Calculate Bill          │
                    │ • previous balance      │
                    │ • purchases / payments  │
                    │ • interest / fees       │
                    │ • minimum amount due    │
                    └──────────┬──────────────┘
                               ▼
                    ┌─────────────────────────┐
                    │ Immutable Bill Created  │
                    └─────────────────────────┘
```

## 7. Statement Generation and Delivery

```text
┌──────────┐   ┌─────────────┐   ┌───────────────────┐
│ Customer │──▶│ API Gateway │──▶│ Statement Service │
└──────────┘   └─────────────┘   └─────────┬─────────┘
                                            │
                         ┌──────────────────┼──────────────────┐
                         ▼                  ▼                  ▼
               ┌────────────────┐ ┌────────────────┐ ┌────────────────┐
               │ CIF Service    │ │ Deposit / Card │ │ Payments Svc   │
               │ Verify owner   │ │ Account data   │ │ Transaction    │
               │ Contact details│ │ Balance/status │ │ history        │
               └────────────────┘ └────────────────┘ └────────────────┘
                                             │
                                  (for card statements)
                                             ▼
                                   ┌────────────────┐
                                   │ Bill Generation│
                                   │ Bill summary   │
                                   └────────┬───────┘
                                            ▼
                           ┌───────────────────────────┐
                           │ Generate immutable PDF /  │
                           │ document reference        │
                           └────────────┬──────────────┘
                                        ▼
                           ┌───────────────────────────┐
                           │ Notification Service      │
                           │ Send statement available  │
                           └───────────────────────────┘
```

## 8. End-of-Day Closure and Reconciliation

```text
┌──────────────┐
│ EOD Operator │
└──────┬───────┘
       ▼
┌────────────────────────┐
│ EOD / Reconciliation   │
│ Orchestrator           │
│ Freeze business cutoff │
└──────────┬─────────────┘
           ▼
┌────────────────────────────────────────────────────────────┐
│ 1. Payments: drain / complete in-flight payment processing  │
└──────────────────────────┬─────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────┐
│ 2. Deposit + Credit Card: confirm readiness                 │
└──────────────────────────┬─────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────┐
│ 3. Bill Generation: complete daily billing classification   │
└──────────────────────────┬─────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────┐
│ 4. Accounting: trial balance + payment reconciliation       │
└──────────────────────────┬─────────────────────────────────┘
                           ▼
                 ┌─────────────────────┐
                 │ Differences found?  │
                 └───────┬───────┬─────┘
                         │ No    │ Yes
                         ▼       ▼
              ┌──────────────┐  ┌─────────────────────┐
              │ Statements + │  │ Block close / create │
              │ Notifications│  │ EOD exception        │
              └──────┬───────┘  └─────────────────────┘
                     ▼
          ┌─────────────────────────┐
          │ Close period; open next │
          │ business date           │
          └─────────────────────────┘
```

## Product Master Responsibilities

Product Master is the policy source for product definitions and versions, eligibility rules, pricing, interest policies, fee rules, card terms, benchmarks, and billing-cycle configuration. It supplies rules to account opening, credit-card validation, and bill generation; it does not create accounts, payments, bills, or accounting journals.
