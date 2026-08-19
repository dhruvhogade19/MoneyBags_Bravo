import { useEffect, useMemo, useState } from "preact/hooks";
import { auth } from "../auth";
import type { EodBusinessDate, EodException, EodRun, EodStep } from "../contracts";
import { services } from "../services";
import { EmptyState, ErrorState, Loading, PageHeader, Panel, Status } from "../components/common";

const stepLabels: Record<string, string> = {
  ACCOUNTING_PERIOD_OPEN_CURRENT: "Open current accounting period",
  PAYMENTS_CUTOFF: "Close new payment intake",
  PAYMENTS_DRAIN: "Drain in-flight payments",
  CREDIT_CARD_READINESS: "Check credit-card readiness",
  DEPOSIT_READINESS: "Check deposit readiness",
  DEPOSIT_ACCRUALS: "Post CASA interest accruals",
  FIXED_DEPOSIT_ACCRUALS: "Post fixed-deposit accruals",
  FIXED_DEPOSIT_MATURITIES: "Process fixed-deposit maturities",
  BILLS_CLOSE: "Close credit-card billing",
  TRIAL_BALANCE: "Generate trial balance",
  PAYMENTS_RECONCILIATION: "Reconcile payment journals",
  FIXED_DEPOSIT_RECONCILIATION: "Reconcile fixed-deposit journals",
  ACCOUNTING_PERIOD_CLOSE: "Close current accounting period",
  ACCOUNTING_PERIOD_OPEN_NEXT: "Open next accounting period",
  PAYMENTS_REOPEN: "Reopen payment intake"
};

function operator(): string {
  return auth.session?.claims.name || auth.session?.claims.sub || auth.session?.claims.user_id || "bank-admin";
}

function when(value?: string): string {
  return value ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "medium" }).format(new Date(value)) : "—";
}

function duration(start?: string, end?: string): string {
  if (!start) return "—";
  const milliseconds = Math.max(0, new Date(end ?? Date.now()).getTime() - new Date(start).getTime());
  if (milliseconds < 1000) return `${milliseconds} ms`;
  const seconds = Math.floor(milliseconds / 1000);
  return seconds < 60 ? `${seconds}s` : `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
}

function outputSummary(step: EodStep): string {
  if (step.message) return step.message;
  if (step.status === "PENDING") return "Waiting for the preceding control to complete.";
  if (step.status === "RUNNING") return `Calling ${step.providerService}.`;
  const output = step.output ?? {};
  const preferred = ["status", "ready", "readyForEod", "processed", "processedCount", "failedCount", "pendingPayments", "balanced", "totalAmount"];
  const values = preferred.filter(key => output[key] !== undefined).map(key => `${key.replace(/([A-Z])/g, " $1").toLowerCase()}: ${String(output[key])}`);
  return values.length ? values.join(" · ") : step.status === "COMPLETED" ? "Control completed successfully." : "No message returned.";
}

function detailJson(value: unknown): string {
  return JSON.stringify(value ?? {}, null, 2);
}

function StepTable({ run, busy, onRetry }: { run: EodRun; busy: string; onRetry: (step: EodStep) => void }) {
  const [expanded, setExpanded] = useState<string>();
  return <div class="mb-table-wrap mb-eod-table-wrap"><table class="mb-table mb-eod-table">
    <thead><tr><th>#</th><th>Step</th><th>Service</th><th>Status</th><th>Attempts</th><th>Started</th><th>Duration</th><th>Message / notes</th><th>Actions</th></tr></thead>
    <tbody>{run.steps.map(step => <>
      <tr key={step.stepCode} class={step.status === "FAILED" ? "mb-eod-failed-row" : ""}>
        <td><span class={`mb-step-index is-${step.status.toLowerCase()}`}>{step.status === "COMPLETED" ? "✓" : step.sequence}</span></td>
        <td><strong>{stepLabels[step.stepCode] ?? step.stepCode.replaceAll("_", " ")}</strong><small>{step.method} {step.path}</small></td>
        <td>{step.providerService.replace("-service", "")}</td>
        <td><Status value={step.status}/></td>
        <td>{step.attemptCount}</td><td>{when(step.startedAt)}</td><td>{duration(step.startedAt, step.completedAt)}</td>
        <td class="mb-eod-note">{outputSummary(step)}{step.errorCode && <small>{step.errorCode}</small>}</td>
        <td><div class="mb-row-actions"><button onClick={() => setExpanded(expanded === step.stepCode ? undefined : step.stepCode)}>{expanded === step.stepCode ? "Hide" : "Details"}</button>{step.status === "FAILED" && <button class="is-danger" disabled={Boolean(busy)} onClick={() => onRetry(step)}>{busy === step.stepCode ? "Retrying…" : "Retry"}</button>}</div></td>
      </tr>
      {expanded === step.stepCode && <tr class="mb-eod-detail-row"><td colSpan={9}><div><section><span>Command reference</span><code>{step.commandReference}</code></section><section><span>Completed</span><strong>{when(step.completedAt)}</strong></section></div><pre>{detailJson(step.output)}</pre></td></tr>}
    </>)}</tbody>
  </table></div>;
}

function ExceptionList({ values, busy, onResolve }: { values: EodException[]; busy: string; onResolve: (value: EodException, waived: boolean) => void }) {
  if (!values.length) return null;
  return <Panel title={`Exceptions (${values.length})`} className="mb-eod-exceptions"><div class="mb-eod-exception-list">{values.map(value => <article key={value.exceptionId}>
    <header><div><Status value={value.status}/><strong>{value.stepCode.replaceAll("_", " ")}</strong></div><span>{value.severity}</span></header>
    <p><b>{value.errorCode}</b> · {String(value.details?.message ?? "The control requires operator attention.")}</p>
    {value.resolution && <small>Resolution: {value.resolution} · {value.resolvedBy}</small>}
    {value.status === "OPEN" && <div class="mb-row-actions"><button disabled={Boolean(busy)} onClick={() => onResolve(value, false)}>Resolve with note</button><button class="is-danger" disabled={Boolean(busy)} onClick={() => onResolve(value, true)}>Waive with note</button></div>}
  </article>)}</div></Panel>;
}

export function EodOperationsPage() {
  const [businessDate, setBusinessDate] = useState<EodBusinessDate>();
  const [selectedDate, setSelectedDate] = useState("");
  const [runs, setRuns] = useState<EodRun[]>([]);
  const [run, setRun] = useState<EodRun>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState("");

  const loadDate = async (date: string, signal?: AbortSignal) => {
    const values = await services.eod.runs(date || undefined, signal);
    setRuns(values);
    setRun(current => values.find(value => value.runId === current?.runId) ?? values[0]);
  };

  const load = async (signal?: AbortSignal) => {
    setError(undefined);
    const current = await services.eod.businessDate(signal);
    setBusinessDate(current);
    const date = selectedDate || current.businessDate;
    if (!selectedDate) setSelectedDate(date);
    await loadDate(date, signal);
  };

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    load(controller.signal).catch(reason => { if (!controller.signal.aborted) setError(reason); }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!selectedDate || loading) return;
    const controller = new AbortController();
    loadDate(selectedDate, controller.signal).catch(reason => { if (!controller.signal.aborted) setError(reason); });
    return () => controller.abort();
  }, [selectedDate]);

  const polling = run && ["PENDING", "RUNNING"].includes(run.status);
  useEffect(() => {
    if (!polling || !run) return;
    const timer = window.setInterval(async () => {
      try {
        const updated = await services.eod.run(run.runId);
        setRun(updated);
        setRuns(current => current.map(value => value.runId === updated.runId ? updated : value));
        if (!["PENDING", "RUNNING"].includes(updated.status)) setBusinessDate(await services.eod.businessDate());
      } catch (reason) { setError(reason); }
    }, 1500);
    return () => window.clearInterval(timer);
  }, [polling, run?.runId]);

  const completed = run?.steps.filter(step => step.status === "COMPLETED").length ?? 0;
  const total = run?.steps.length ?? 0;
  const progress = total ? Math.round(completed * 100 / total) : 0;
  const canStart = businessDate?.status === "OPEN" && selectedDate === businessDate.businessDate && !polling;
  const openExceptions = run?.exceptions.filter(value => value.status === "OPEN").length ?? 0;

  const start = async () => {
    if (!businessDate || !window.confirm(`Start the full bank EOD close for ${selectedDate}? New payment intake will be stopped while controls run.`)) return;
    setBusy("start"); setError(undefined);
    try {
      const value = await services.eod.start(selectedDate, operator(), `EOD:${selectedDate}`);
      setRun(value); setRuns(current => [value, ...current.filter(item => item.runId !== value.runId)]);
      setBusinessDate({ ...businessDate, status: "EOD_IN_PROGRESS" });
    } catch (reason) { setError(reason); } finally { setBusy(""); }
  };

  const resume = async () => {
    if (!run) return;
    const reason = window.prompt("Reason for resuming this run", "Continue after reviewing the failed control");
    if (!reason?.trim()) return;
    setBusy("resume"); setError(undefined);
    try { setRun(await services.eod.resume(run.runId, operator(), reason.trim(), crypto.randomUUID())); }
    catch (reason) { setError(reason); } finally { setBusy(""); }
  };

  const retry = async (step: EodStep) => {
    if (!run) return;
    const reason = window.prompt(`Reason for retrying ${stepLabels[step.stepCode] ?? step.stepCode}`, "Reviewed and ready to retry");
    if (!reason?.trim()) return;
    setBusy(step.stepCode); setError(undefined);
    try { setRun(await services.eod.retry(run.runId, step.stepCode, operator(), reason.trim(), crypto.randomUUID())); }
    catch (reason) { setError(reason); } finally { setBusy(""); }
  };

  const resolve = async (exception: EodException, waived: boolean) => {
    const note = window.prompt(`${waived ? "Waiver" : "Resolution"} note for ${exception.stepCode}`, "");
    if (!note?.trim()) return;
    setBusy(exception.exceptionId); setError(undefined);
    try { setRun(await services.eod.resolve(exception.exceptionId, note.trim(), operator(), waived, crypto.randomUUID())); }
    catch (reason) { setError(reason); } finally { setBusy(""); }
  };

  const latestLabel = useMemo(() => run ? `${run.businessDate} · ${run.status.replaceAll("_", " ")}` : "No run selected", [run]);
  if (loading) return <Loading label="Loading end-of-day controls"/>;
  return <>
    <PageHeader title="End-of-day control" description="Run the bank close, follow every service checkpoint, and resolve operational exceptions." action={<button class="mb-button mb-button-primary" disabled={!canStart || Boolean(busy)} onClick={start}>{busy === "start" ? "Starting…" : "Start EOD"}</button>}/>
    {error && <ErrorState error={error} retry={() => load().catch(setError)}/>} 
    <section class="mb-eod-summary">
      <article><span>Current business date</span><strong>{businessDate?.businessDate ?? "—"}</strong><Status value={businessDate?.status ?? "UNKNOWN"}/></article>
      <article><span>Selected run</span><strong>{latestLabel}</strong><small>{run ? `Started by ${run.startedBy}` : "Choose a date with EOD history"}</small></article>
      <article><span>Progress</span><strong>{completed} / {total || "—"} steps</strong><div class="mb-eod-progress"><i style={{ width: `${progress}%` }}/></div><small>{progress}% complete</small></article>
      <article><span>Exceptions</span><strong>{openExceptions} open</strong><small>{run?.exceptions.length ?? 0} recorded for this run</small></article>
    </section>
    <Panel title="Business date and run controls" className="mb-eod-controls"><div class="mb-filter-row">
      <label class="mb-field"><span>Business date</span><input type="date" value={selectedDate} onInput={event => setSelectedDate((event.currentTarget as HTMLInputElement).value)}/></label>
      <label class="mb-field"><span>Run</span><select value={run?.runId ?? ""} onChange={event => setRun(runs.find(value => value.runId === (event.currentTarget as HTMLSelectElement).value))}><option value="">No run selected</option>{runs.map(value => <option value={value.runId}>{when(value.startedAt)} · {value.status}</option>)}</select></label>
      <button class="mb-button mb-button-secondary" disabled={Boolean(busy)} onClick={() => load().catch(setError)}>Refresh</button>
      {run?.status === "FAILED" && <button class="mb-button mb-button-primary" disabled={Boolean(busy)} onClick={resume}>{busy === "resume" ? "Resuming…" : "Resume run"}</button>}
    </div>{selectedDate !== businessDate?.businessDate && <div class="mb-info-banner">Historical mode: select the current open business date to start a new run.</div>}</Panel>
    {run ? <>
      <Panel title={`Step progress · ${run.status.replaceAll("_", " ")}`} className="mb-eod-steps"><div class="mb-eod-run-meta"><span>Run ID <code>{run.runId}</code></span><span>Started {when(run.startedAt)}</span><span>Elapsed {duration(run.startedAt, run.completedAt)}</span>{polling && <span class="mb-live-indicator"><i/> Live</span>}</div><StepTable run={run} busy={busy} onRetry={retry}/></Panel>
      <ExceptionList values={run.exceptions} busy={busy} onResolve={resolve}/>
    </> : <Panel><EmptyState title="No EOD run for this date" message={canStart ? "Start EOD when operations are ready to close the business date." : "Choose another business date to view its EOD history."}/></Panel>}
  </>;
}
