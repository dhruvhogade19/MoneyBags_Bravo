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
