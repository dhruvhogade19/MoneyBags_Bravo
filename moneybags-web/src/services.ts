import { api, query } from "./api";
import type { Account, AccountBalance, AccountDetail, AccountStatusHistory, Bill, CardAccount, CardApplication, Cif, ClosureQuote, ClosureRequest, DepositReadiness, EodResult, EligibilityResult, FixedDeposit, FixedDepositAccrual, FixedDepositQuote, FixedDepositSchedule, Journal, Kyc, KycDocument, Notification, Page, Payment, PaymentEodControl, PaymentStatusHistory, PrematureClosureQuote, Product } from "./contracts";

export function items<T>(value: T[] | Page<T> | { content?: T[] } | undefined): T[] {
  if (!value) return [];
  if (Array.isArray(value)) return value;
  return value.content ?? [];
}

export const services = {
  cif: {
    get: (cifId: string | number, signal?: AbortSignal) => api.get<Cif>(`/api/v1/cifs/${cifId}`, signal),
    me: (signal?: AbortSignal) => api.get<Cif>("/api/v1/cifs/me", signal),
    repairIdentityLink: () => api.post<Cif>("/api/v1/cifs/me/identity-link", {}),
    create: (body: unknown, idempotencyKey?: string) => api.post<Cif>("/api/v1/cifs", body, idempotencyKey),
    update: (cifId: string | number, body: unknown) => api.put<Cif>(`/api/v1/cifs/${cifId}`, body)
  },
  kyc: {
    byCif: (cifId: string | number, signal?: AbortSignal) => api.get<Kyc[]>(query("/api/v1/kycs", { cifId }), signal),
    create: (body: unknown, idempotencyKey?: string) => api.post<Kyc>("/api/v1/kycs", body, idempotencyKey),
    documents: (kycId: number, signal?: AbortSignal) => api.get<KycDocument[]>(`/api/v1/kycs/${kycId}/documents`, signal),
    upload: (kycId: number, documentType: string, file: File, idempotencyKey?: string) => {
      const form = new FormData();
      form.append("documentTypes", documentType);
      form.append("files", file);
      return api.request<KycDocument[]>(`/api/v1/kycs/${kycId}/documents`, { method: "POST", body: form, idempotent: true, idempotencyKey });
    },
    uploadBatch: (kycId: number, documents: Array<{ type: string; file: File }>, idempotencyKey?: string) => {
      const form = new FormData();
      documents.forEach(document => { form.append("documentTypes", document.type); form.append("files", document.file); });
      return api.request<KycDocument[]>(`/api/v1/kycs/${kycId}/documents`, { method: "POST", body: form, idempotent: true, idempotencyKey });
    },
    verifyDocument: (kycId: number, documentId: number, status: "VERIFIED" | "MISMATCH", remarks?: string, idempotencyKey?: string) => api.patch<KycDocument>(`/api/v1/kycs/${kycId}/documents/${documentId}/verification`, { status, remarks }, idempotencyKey),
    queue: (signal?: AbortSignal) => api.get<Page<Kyc>>(query("/api/v1/kycs/admin/work-queue", { page: 0, size: 50 }), signal),
    decide: (kycId: number, decision: string, rejectionReason?: string, idempotencyKey?: string) => api.patch<Kyc>(`/api/v1/kycs/${kycId}/decision`, { decision, rejectionReason }, idempotencyKey)
  },
  products: {
    active: (signal?: AbortSignal) => api.get<Page<Product> | Product[]>(query("/api/products/active", { page: 0, size: 100 }), signal),
    all: (signal?: AbortSignal) => api.get<Page<Product> | Product[]>(query("/api/products", { page: 0, size: 100 }), signal),
    one: (code: string, signal?: AbortSignal) => api.get<Product>(`/api/products/${encodeURIComponent(code)}`, signal)
  },
  accounts: {
    list: (customerId: string | number, signal?: AbortSignal) => api.get<Page<Account>>(query("/api/deposit-accounts", { customerId, page: 0, size: 100 }), signal),
    all: (signal?: AbortSignal) => api.get<Page<Account>>(query("/api/deposit-accounts", { page: 0, size: 100 }), signal),
    search: (customerId?: string, status?: string, signal?: AbortSignal) => api.get<Page<Account>>(query("/api/deposit-accounts", { customerId, status, page: 0, size: 100 }), signal),
    one: (accountId: string, signal?: AbortSignal) => api.get<AccountDetail>(`/api/deposit-accounts/${encodeURIComponent(accountId)}`, signal),
    balance: (accountId: string, signal?: AbortSignal) => api.get<AccountBalance>(`/api/deposit-accounts/${encodeURIComponent(accountId)}/balance`, signal),
    history: (accountId: string, signal?: AbortSignal) => api.get<AccountStatusHistory[]>(`/api/deposit-accounts/${encodeURIComponent(accountId)}/status-history`, signal),
    open: (body: unknown, idempotencyKey?: string) => api.post<AccountDetail>("/api/deposit-accounts", body, idempotencyKey),
    eligibility: (body: unknown) => api.post<EligibilityResult>("/api/deposit-accounts/eligibility-check", body),
    addHolder: (accountId: string, body: unknown, idempotencyKey?: string) => api.post<AccountDetail>(`/api/deposit-accounts/${encodeURIComponent(accountId)}/holders`, body, idempotencyKey),
    removeHolder: (accountId: string, customerId: string, idempotencyKey?: string) => api.delete<void>(`/api/deposit-accounts/${encodeURIComponent(accountId)}/holders/${encodeURIComponent(customerId)}`, idempotencyKey),
    replaceNominees: (accountId: string, body: unknown, idempotencyKey?: string) => api.put<unknown[]>(`/api/deposit-accounts/${encodeURIComponent(accountId)}/nominees`, body, idempotencyKey),
    addMandate: (accountId: string, body: unknown, idempotencyKey?: string) => api.post<unknown>(`/api/deposit-accounts/${encodeURIComponent(accountId)}/mandates`, body, idempotencyKey),
    revokeMandate: (accountId: string, mandateId: string, idempotencyKey?: string) => api.delete<void>(`/api/deposit-accounts/${encodeURIComponent(accountId)}/mandates/${encodeURIComponent(mandateId)}`, idempotencyKey),
    setLimit: (accountId: string, limitType: string, body: unknown, idempotencyKey?: string) => api.put<unknown>(`/api/deposit-accounts/${encodeURIComponent(accountId)}/limits/${encodeURIComponent(limitType)}`, body, idempotencyKey),
    command: (accountId: string, command: string, version: number, body: unknown, idempotencyKey?: string) => api.request<AccountDetail>(`/api/deposit-accounts/${encodeURIComponent(accountId)}/commands/${encodeURIComponent(command)}`, { method: "POST", body, idempotent: true, idempotencyKey, headers: { "If-Match": `\"${version}\"` } }),
    closureQuote: (accountId: string, body: unknown) => api.post<ClosureQuote>(`/api/deposit-accounts/${encodeURIComponent(accountId)}/closure-quotes`, body),
    close: (accountId: string, body: unknown, idempotencyKey?: string) => api.post<ClosureRequest>(`/api/deposit-accounts/${encodeURIComponent(accountId)}/closure-requests`, body, idempotencyKey),
    closureRequests: (accountId: string, signal?: AbortSignal) => api.get<ClosureRequest[]>(`/api/deposit-accounts/${encodeURIComponent(accountId)}/closure-requests`, signal),
    cancelClosure: (accountId: string, requestId: string, body: unknown, idempotencyKey?: string) => api.post<ClosureRequest>(`/api/deposit-accounts/${encodeURIComponent(accountId)}/closure-requests/${encodeURIComponent(requestId)}/cancel`, body, idempotencyKey)
  },
  fixedDeposits: {
    list: (customerId: string | number, signal?: AbortSignal) => api.get<Page<FixedDeposit> | FixedDeposit[]>(query("/api/deposit-accounts/fixed-deposits", { customerId, page: 0, size: 100 }), signal),
    all: (signal?: AbortSignal) => api.get<Page<FixedDeposit> | FixedDeposit[]>(query("/api/deposit-accounts/fixed-deposits", { page: 0, size: 100 }), signal),
    search: (customerId?: string, status?: string, maturingBefore?: string, signal?: AbortSignal) => api.get<Page<FixedDeposit> | FixedDeposit[]>(query("/api/deposit-accounts/fixed-deposits", { customerId, status, maturingBefore, page: 0, size: 100 }), signal),
    one: (fdId: string, signal?: AbortSignal) => api.get<FixedDeposit>(`/api/deposit-accounts/fixed-deposits/${encodeURIComponent(fdId)}`, signal),
    accruals: (fdId: string, signal?: AbortSignal) => api.get<FixedDepositAccrual[]>(`/api/deposit-accounts/fixed-deposits/${encodeURIComponent(fdId)}/interest-accruals`, signal),
    schedule: (fdId: string, signal?: AbortSignal) => api.get<FixedDepositSchedule>(`/api/deposit-accounts/fixed-deposits/${encodeURIComponent(fdId)}/projected-schedule`, signal),
    quote: (body: unknown) => api.post<FixedDepositQuote>("/api/deposit-accounts/fixed-deposits/quotes", body),
    book: (body: unknown, idempotencyKey?: string) => api.post<FixedDeposit>("/api/deposit-accounts/fixed-deposits", body, idempotencyKey),
    prematureQuote: (fdId: string, body: unknown) => api.post<PrematureClosureQuote>(`/api/deposit-accounts/fixed-deposits/${encodeURIComponent(fdId)}/premature-closure-quotes`, body),
    prematureClose: (fdId: string, body: unknown, idempotencyKey?: string) => api.post<ClosureRequest>(`/api/deposit-accounts/fixed-deposits/${encodeURIComponent(fdId)}/premature-closure-requests`, body, idempotencyKey)
  },
  depositOperations: {
    readiness: (signal?: AbortSignal) => api.get<DepositReadiness>("/api/deposit-accounts/operations/eod/readiness", signal),
    accountAccruals: (body: unknown, idempotencyKey?: string) => api.post<EodResult>("/api/deposit-accounts/operations/eod/account-accruals", body, idempotencyKey),
    fixedDepositAccruals: (body: unknown, idempotencyKey?: string) => api.post<EodResult>("/api/deposit-accounts/operations/eod/fixed-deposit-accruals", body, idempotencyKey),
    fixedDepositMaturities: (body: unknown, idempotencyKey?: string) => api.post<EodResult>("/api/deposit-accounts/operations/eod/fixed-deposit-maturities", body, idempotencyKey)
  },
  cards: {
    applications: (cifId: string | number, signal?: AbortSignal) => api.get<CardApplication[]>(`/api/credit-cards/applications/cif/${cifId}`, signal),
    applicationsForReview: (status?: string, signal?: AbortSignal) => api.get<CardApplication[]>(query("/api/credit-cards/applications", { status }), signal),
    accounts: (cifId: string | number, signal?: AbortSignal) => api.get<CardAccount[]>(`/api/credit-cards/accounts/cif/${cifId}`, signal),
    apply: (body: unknown, idempotencyKey?: string) => api.post<CardApplication>("/api/credit-cards/applications", body, idempotencyKey),
    approve: (applicationId: number, idempotencyKey?: string) => api.post<CardAccount>(`/api/credit-cards/applications/${applicationId}/approve`, {}, idempotencyKey),
    reject: (applicationId: number, idempotencyKey?: string) => api.post<CardApplication>(`/api/credit-cards/applications/${applicationId}/reject`, {}, idempotencyKey)
  },
  payments: {
    list: (customerId: string | number, signal?: AbortSignal, page = 0, size = 100) => api.get<Page<Payment>>(query("/api/v1/payments", { customerId, page, size }), signal),
    one: (paymentId: string, signal?: AbortSignal) => api.get<Payment>(`/api/v1/payments/${encodeURIComponent(paymentId)}`, signal),
    history: (paymentId: string, signal?: AbortSignal) => api.get<PaymentStatusHistory[]>(`/api/v1/payments/${encodeURIComponent(paymentId)}/history`, signal),
    transfer: (body: unknown, idempotencyKey?: string) => api.post<Payment>("/api/v1/payments/book-transfers", body, idempotencyKey),
    merchant: (body: unknown, idempotencyKey?: string) => api.post<Payment>("/api/v1/payments/credit-card-payment/merchant-payment", body, idempotencyKey),
    repay: (body: unknown, idempotencyKey?: string) => api.post<Payment>("/api/v1/payments/credit-card-payment/repayment", body, idempotencyKey),
    fundFixedDeposit: (body: unknown, idempotencyKey?: string) => api.post<Payment>("/api/v1/payments/fixed-deposit-funding", body, idempotencyKey),
    cancel: (paymentId: string, idempotencyKey?: string) => api.post<Payment>(`/api/v1/payments/${encodeURIComponent(paymentId)}/cancel`, {}, idempotencyKey)
  },
  paymentOperations: {
    list: (status?: string, businessDate?: string, signal?: AbortSignal) => api.get<Page<Payment>>(query("/api/v1/payments/operations", { status, businessDate, page: 0, size: 100 }), signal),
    cutoff: (businessDate: string, idempotencyKey?: string) => api.post<PaymentEodControl>("/api/v1/payments/operations/eod/cutoff", { businessDate, commandReference: `PAYMENT-CUTOFF-${businessDate}` }, idempotencyKey),
    drain: (idempotencyKey?: string) => api.post<PaymentEodControl>("/api/v1/payments/operations/eod/drain", {}, idempotencyKey),
    reopen: (idempotencyKey?: string) => api.post<PaymentEodControl>("/api/v1/payments/operations/eod/reopen", {}, idempotencyKey),
    reverse: (paymentId: string, reason: string, idempotencyKey?: string) => api.post<Payment>(`/api/v1/payments/operations/${encodeURIComponent(paymentId)}/reversal`, { reason }, idempotencyKey),
    retryBilling: (paymentId: string, idempotencyKey?: string) => api.post<Payment>(`/api/v1/payments/operations/${encodeURIComponent(paymentId)}/billing-settlement`, {}, idempotencyKey)
  },
  bills: {
    list: (accountId?: string, signal?: AbortSignal) => api.get<Page<Bill>>(query("/api/v1/bills", { accountId, page: 0, size: 100 }), signal),
    one: (billId: string, signal?: AbortSignal) => api.get<Bill>(`/api/v1/bills/${encodeURIComponent(billId)}`, signal)
  },
  notifications: {
    list: (cifId: string | number, signal?: AbortSignal) => api.get<Page<Notification>>(query("/api/notifications", { cifId, page: 0, size: 100 }), signal)
  },
  accounting: {
    journals: (signal?: AbortSignal) => api.get<Page<Journal>>(query("/api/v1/journals", { page: 0, size: 100 }), signal),
    glAccounts: (signal?: AbortSignal) => api.get<Page<Record<string, unknown>>>(query("/api/v1/gl-accounts", { page: 0, size: 100 }), signal),
    rules: (signal?: AbortSignal) => api.get<Page<Record<string, unknown>>>(query("/api/v1/accounting-rules", { page: 0, size: 100 }), signal),
    mappings: (signal?: AbortSignal) => api.get<Page<Record<string, unknown>>>(query("/api/v1/subledger-mappings", { page: 0, size: 100 }), signal)
  }
};
