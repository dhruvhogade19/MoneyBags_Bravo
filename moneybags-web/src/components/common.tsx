import { h, ComponentChildren } from "preact";
import { useEffect, useRef, useState } from "preact/hooks";
import { ApiError } from "../api";
import { finiteNumber, IdempotencyKeyStore } from "../utils";

export function Icon({ name }: { name: string }) {
  const glyphs: Record<string, string> = {
    overview: "⌂", accounts: "▣", fd: "◫", cards: "▱", payments: "⇄", bills: "▤",
    products: "◇", notifications: "◌", profile: "♙", operations: "◎", accounting: "≡",
    kyc: "✓", eod: "↻", search: "⌕", arrow: "↗", add: "+", menu: "☰", close: "×",
    home: "⌂", account_balance: "▣", savings: "◫", credit_card: "▱", swap_horiz: "⇄",
    inventory_2: "◇", description: "▤", person: "♙", dashboard: "◎", verified_user: "✓",
    receipt_long: "▤", menu_book: "≡", schema: "⌘", fact_check: "↻",
    admin_panel_settings: "♙"
  };
  return <span class="mb-icon" aria-hidden="true">{glyphs[name] ?? "•"}</span>;
}

export function Status({ value }: { value?: string }) {
  const normalized = (value ?? "UNKNOWN").toUpperCase();
  const success = ["ACTIVE", "APPROVED", "VERIFIED", "SETTLED", "POSTED", "PAID", "SENT", "ELIGIBLE"].includes(normalized);
  const warning = normalized.startsWith("PENDING_") || normalized === "REVERSAL_PENDING" || ["PENDING", "SUBMITTED", "PROCESSING", "UNDER_REVIEW", "PARTIALLY_PAID", "GENERATED", "BLOCKED"].includes(normalized);
  const danger = ["FAILED", "REJECTED", "OVERDUE", "MISMATCH", "CLOSED"].includes(normalized);
  return <span class={`mb-status ${success ? "mb-status-success" : warning ? "mb-status-warning" : danger ? "mb-status-danger" : ""}`}>{normalized.replaceAll("_", " ")}</span>;
}

export function Money({ value, currency = "INR" }: { value?: number | string; currency?: string }) {
  const amount = finiteNumber(value);
  if (amount === undefined) return <span class="mb-money">—</span>;
  try {
    return <span class="mb-money">{new Intl.NumberFormat("en-IN", { style: "currency", currency, maximumFractionDigits: 2 }).format(amount)}</span>;
  } catch {
    return <span class="mb-money">—</span>;
  }
}

export function PageHeader({ title, description, action }: { title: string; description: string; action?: ComponentChildren }) {
  return <header class="mb-page-header"><div><h1>{title}</h1><p>{description}</p></div>{action && <div class="mb-page-action">{action}</div>}</header>;
}

export function Panel({ title, action, children, className = "" }: { title?: string; action?: ComponentChildren; children: ComponentChildren; className?: string }) {
  return <section class={`mb-panel ${className}`}>{title && <header class="mb-panel-header"><h2>{title}</h2>{action}</header>}<div class="mb-panel-body">{children}</div></section>;
}

export function EmptyState({ title, message }: { title: string; message: string }) {
  return <div class="mb-empty"><span class="mb-empty-mark">◇</span><h3>{title}</h3><p>{message}</p></div>;
}

export function ErrorState({ error, retry }: { error: unknown; retry?: () => void }) {
  const problem = error instanceof ApiError ? error.problem : undefined;
  const fieldErrors = Object.entries(problem?.fieldErrors ?? {});
  return <div class="mb-error" role="alert"><strong>{problem?.title ?? "Something went wrong"}</strong><p>{problem?.detail ?? (error instanceof Error ? error.message : "The request could not be completed.")}</p>{fieldErrors.length > 0 && <ul class="mb-field-errors">{fieldErrors.map(([field, message]) => <li key={field}><b>{field.replace(/([A-Z])/g, " $1")}:</b> {message}</li>)}</ul>}{problem?.correlationId && <small>Support reference: {problem.correlationId}</small>}{retry && <button class="mb-button mb-button-secondary" type="button" onClick={retry}>Try again</button>}</div>;
}

export function Loading({ label = "Loading" }: { label?: string }) {
  return <div class="mb-loading" role="status"><span></span><span></span><span></span><em>{label}</em></div>;
}

export function useRemote<T>(load: (signal: AbortSignal) => Promise<T>, dependencies: unknown[]) {
  const [state, setState] = useState<{ loading: boolean; data?: T; error?: unknown; revision: number }>({ loading: true, revision: 0 });
  useEffect(() => {
    const controller = new AbortController();
    let live = true;
    setState((previous) => ({ loading: true, revision: previous.revision }));
    load(controller.signal).then((data) => { if (live) setState((previous) => ({ loading: false, data, revision: previous.revision })); })
      .catch((error) => { if (live && (error as Error).name !== "AbortError") setState((previous) => ({ loading: false, error, revision: previous.revision })); });
    return () => { live = false; controller.abort(); };
  }, [...dependencies, state.revision]);
  return { ...state, retry: () => setState((previous) => ({ ...previous, revision: previous.revision + 1 })) };
}

export function useIdempotencyKeyStore(): IdempotencyKeyStore {
  const store = useRef<IdempotencyKeyStore>();
  if (!store.current) store.current = new IdempotencyKeyStore();
  return store.current;
}

export function Field({ label, name, type = "text", value, required = false, min, max, step, pattern, inputMode, maxLength, placeholder, title, hint, onInput }: { label: string; name: string; type?: string; value?: string | number; required?: boolean; min?: string; max?: string; step?: string; pattern?: string; inputMode?: "none" | "text" | "decimal" | "numeric" | "tel" | "search" | "email" | "url"; maxLength?: number; placeholder?: string; title?: string; hint?: string; onInput?: (value: string) => void }) {
  return <label class="mb-field"><span>{label}{required && <b aria-hidden="true"> *</b>}</span><input name={name} type={type} value={value} required={required} min={min} max={max} step={step} pattern={pattern} inputMode={inputMode} maxLength={maxLength} placeholder={placeholder} title={title} onInput={(event) => onInput?.((event.currentTarget as HTMLInputElement).value)} />{hint && <small class="mb-field-hint">{hint}</small>}</label>;
}

export function SelectField({ label, value, required = false, onChange, children }: { label: string; value: string; required?: boolean; onChange: (value: string) => void; children: ComponentChildren }) {
  return <label class="mb-field"><span>{label}{required && <b aria-hidden="true"> *</b>}</span><select value={value} required={required} onChange={(event) => onChange((event.currentTarget as HTMLSelectElement).value)}>{children}</select></label>;
}

export function Receipt({ title, reference, children, done }: { title: string; reference?: string; children?: ComponentChildren; done: () => void }) {
  return <Panel className="mb-receipt"><div class="mb-receipt-mark">✓</div><h2>{title}</h2>{children}{reference && <p class="mb-reference">Reference: {reference}</p>}<button class="mb-button mb-button-primary" type="button" onClick={done}>Done</button></Panel>;
}
