export function finiteNumber(value: unknown): number | undefined {
  if (value === null || value === undefined || value === "") return undefined;
  const number = typeof value === "number" ? value : Number(value);
  return Number.isFinite(number) ? number : undefined;
}

export function formatDate(value?: string): string {
  if (!value) return "—";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "—";
  return new Intl.DateTimeFormat("en-IN", { dateStyle: "medium" }).format(parsed);
}

const PAYABLE_BILL_STATUSES = new Set(["GENERATED", "PARTIALLY_PAID", "OVERDUE"]);

export function isBillPayable(status: string | undefined, outstandingAmount: unknown): boolean {
  const amount = finiteNumber(outstandingAmount);
  return PAYABLE_BILL_STATUSES.has((status ?? "").toUpperCase()) && amount !== undefined && amount > 0;
}

export function canonicalCreditCardAccountReference(value: string | number): string {
  const text = String(value).trim().toUpperCase();
  const numeric = text.startsWith("CC-") ? text.slice(3) : text;
  if (!/^\d+$/.test(numeric)) throw new Error("The bill does not contain a valid credit-card account reference.");
  return `CC-${numeric}`;
}

export type PaymentOutcome = "success" | "pending" | "error";

export function paymentOutcome(status: string | undefined): PaymentOutcome {
  const normalized = (status ?? "").toUpperCase();
  if (normalized === "SETTLED") return "success";
  if (["FAILED", "REVERSED", "REVERSAL_PENDING", "CANCELLED"].includes(normalized)) return "error";
  return "pending";
}

export class IdempotencyKeyStore {
  private fingerprint?: string;
  private key?: string;

  constructor(private readonly createKey: () => string = () => crypto.randomUUID()) {}

  keyFor(command: unknown): string {
    const fingerprint = JSON.stringify(command);
    if (this.fingerprint !== fingerprint || !this.key) {
      this.fingerprint = fingerprint;
      this.key = this.createKey();
    }
    return this.key;
  }

  reset(): void {
    this.fingerprint = undefined;
    this.key = undefined;
  }
}

export function buildLogoutUrl(issuer: string, redirectUri: string, idToken: string): string {
  const query = new URLSearchParams({ id_token_hint: idToken, post_logout_redirect_uri: redirectUri });
  return `${issuer.replace(/\/$/, "")}/connect/logout?${query.toString()}`;
}

export function isNavigationPathActive(itemPath: string, currentPath: string, homePath: string): boolean {
  if (itemPath === currentPath) return true;
  if (itemPath === "/ops/accounting/rules" && currentPath === "/ops/accounting/mappings") return true;
  return itemPath !== homePath && currentPath.startsWith(`${itemPath}/`);
}
