import { auth } from "./auth";
import { config } from "./config";

export type ApiProblem = Readonly<{
  status: number;
  code?: string;
  title: string;
  detail: string;
  correlationId?: string;
  fieldErrors?: Record<string, string>;
}>;

export class ApiError extends Error {
  constructor(public readonly problem: ApiProblem) {
    super(problem.detail);
    this.name = "ApiError";
  }
}

type RequestOptions = Readonly<{
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  headers?: Record<string, string>;
  idempotent?: boolean;
  idempotencyKey?: string;
  signal?: AbortSignal;
}>;

function correlationId(): string { return crypto.randomUUID(); }

async function parseProblem(response: Response, requestCorrelationId: string): Promise<ApiProblem> {
  let body: Record<string, unknown> = {};
  try { body = await response.json() as Record<string, unknown>; } catch { /* non-JSON upstream error */ }
  const fieldErrors = typeof body.fieldErrors === "object" && body.fieldErrors !== null
    ? body.fieldErrors as Record<string, string>
    : typeof body.validationErrors === "object" && body.validationErrors !== null
      ? body.validationErrors as Record<string, string>
      : undefined;
  return {
    status: response.status,
    code: typeof body.code === "string" ? body.code : undefined,
    title: typeof body.title === "string" ? body.title : `Request failed (${response.status})`,
    detail: typeof body.detail === "string" ? body.detail : typeof body.message === "string" ? body.message : "MoneyBags could not complete the request.",
    correlationId: response.headers.get("X-Correlation-ID") ?? (typeof body.correlationId === "string" ? body.correlationId : requestCorrelationId),
    fieldErrors
  };
}

export class ApiClient {
  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const token = await auth.accessToken();
    const tenantId = auth.session?.claims.tenant_id;
    if (!tenantId) throw new Error("The signed session does not contain a tenant identifier");
    const requestCorrelationId = correlationId();
    const headers: Record<string, string> = {
      Authorization: `Bearer ${token}`,
      "X-Tenant-ID": tenantId,
      "X-Correlation-ID": requestCorrelationId,
      Accept: "application/json",
      ...options.headers
    };
    if (options.body !== undefined && !(options.body instanceof FormData)) headers["Content-Type"] = "application/json";
    if (options.idempotent) headers["Idempotency-Key"] = options.idempotencyKey ?? crypto.randomUUID();
    const response = await fetch(`${config.apiBaseUrl}${path}`, {
      method: options.method ?? "GET",
      headers,
      body: options.body === undefined ? undefined : options.body instanceof FormData ? options.body : JSON.stringify(options.body),
      signal: options.signal
    });
    if (!response.ok) throw new ApiError(await parseProblem(response, requestCorrelationId));
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
  }

  get<T>(path: string, signal?: AbortSignal): Promise<T> { return this.request<T>(path, { signal }); }
  post<T>(path: string, body: unknown, idempotencyKey?: string): Promise<T> { return this.request<T>(path, { method: "POST", body, idempotent: true, idempotencyKey }); }
  put<T>(path: string, body: unknown, idempotencyKey?: string): Promise<T> { return this.request<T>(path, { method: "PUT", body, idempotent: true, idempotencyKey }); }
  patch<T>(path: string, body: unknown, idempotencyKey?: string): Promise<T> { return this.request<T>(path, { method: "PATCH", body, idempotent: true, idempotencyKey }); }
  delete<T>(path: string, idempotencyKey?: string): Promise<T> { return this.request<T>(path, { method: "DELETE", idempotent: true, idempotencyKey }); }
}

export const api = new ApiClient();

export function query(path: string, params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => { if (value !== undefined && value !== "") search.set(key, String(value)); });
  const encoded = search.toString();
  return encoded ? `${path}?${encoded}` : path;
}
