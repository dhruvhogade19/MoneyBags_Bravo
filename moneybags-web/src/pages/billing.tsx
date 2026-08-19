import { h } from "preact";
import { useEffect, useMemo, useState } from "preact/hooks";
import { auth } from "../auth";
import type { Bill, BillLine, CardAccount, StatementPreview } from "../contracts";
import { EmptyState, ErrorState, Loading, Money, PageHeader, Panel, Status, useIdempotencyKeyStore, useRemote } from "../components/common";
import { navigate } from "../router";
import { items, services } from "../services";
import { formatDate, isBillPayable } from "../utils";

const DRAFT_KEY = "moneybags.billing.statement.draft";
type PeriodChoice = "current" | "previous" | "older" | "custom";
type Draft = { accountId: string; periodChoice: PeriodChoice; startDate: string; endDate: string; saveToHistory: boolean };

function customerId(): string | undefined {
  const value = auth.session?.claims.customer_id;
  return value == null ? undefined : String(value);
}

function localIso(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const day = String(value.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function periodRange(choice: PeriodChoice): [string, string] {
  const today = new Date();
  if (choice === "current" || choice === "custom") return [localIso(new Date(today.getFullYear(), today.getMonth(), 1)), localIso(today)];
  const offset = choice === "previous" ? 1 : 2;
  return [localIso(new Date(today.getFullYear(), today.getMonth() - offset, 1)), localIso(new Date(today.getFullYear(), today.getMonth() - offset + 1, 0))];
}

function initialDraft(): Draft {
  const [startDate, endDate] = periodRange("current");
  try {
    const saved = JSON.parse(sessionStorage.getItem(DRAFT_KEY) ?? "null") as Partial<Draft> | null;
    if (saved?.startDate && saved?.endDate) return { accountId: saved.accountId ?? "", periodChoice: saved.periodChoice ?? "custom", startDate: saved.startDate, endDate: saved.endDate, saveToHistory: saved.saveToHistory ?? true };
  } catch { /* use a clean draft */ }
  return { accountId: "", periodChoice: "current", startDate, endDate, saveToHistory: true };
}

function mask(value: string | number | undefined): string {
  const text = String(value ?? "");
  return text ? `•••• •••• •••• ${text.slice(-4)}` : "—";
}

async function statementPdf(bill: Bill, disposition: "inline" | "attachment"): Promise<void> {
  const blob = await services.bills.pdf(bill.billId, disposition);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.target = disposition === "inline" ? "_blank" : "_self";
  if (disposition === "attachment") link.download = `MoneyBags-${bill.billingPeriod}-${bill.billId}.pdf`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 30000);
}

function accountLabel(card: CardAccount): string {
  return `${card.productCode} · ${mask(card.cardNumber || card.accountId)}`;
}

export function BillingDashboardPage() {
  const id = customerId();
  const remote = useRemote(async signal => {
    const [billPage, cards] = await Promise.all([
      services.bills.list(undefined, signal),
      id ? services.cards.accounts(id, signal) : Promise.resolve([] as CardAccount[])
    ]);
    return { bills: items(billPage).filter(bill => bill.savedToHistory !== false), cards };
  }, [id]);
  const [actionError, setActionError] = useState<unknown>();
  const [pdfBusy, setPdfBusy] = useState("");

  if (remote.loading) return <Loading label="Loading your billing dashboard" />;
  if (remote.error) return <ErrorState error={remote.error} retry={remote.retry} />;
  const { bills, cards } = remote.data!;
  const outstanding = bills.reduce((total, bill) => total + Number(bill.outstandingAmount ?? 0), 0);
  const minimum = bills.filter(bill => Number(bill.outstandingAmount) > 0).reduce((total, bill) => total + Number(bill.minimumAmountDue ?? 0), 0);
  const openBills = bills.filter(bill => Number(bill.outstandingAmount) > 0).sort((a, b) => a.paymentDueDate.localeCompare(b.paymentDueDate));
  const next = openBills[0];
  const latest = bills.slice().sort((a, b) => String(b.generatedAt ?? "").localeCompare(String(a.generatedAt ?? "")))[0];
  const latestCard = cards.find(card => String(card.accountId) === latest?.accountId);

  const openPdf = async (bill: Bill, disposition: "inline" | "attachment") => {
    setPdfBusy(`${bill.billId}-${disposition}`); setActionError(undefined);
    try { await statementPdf(bill, disposition); } catch (reason) { setActionError(reason); } finally { setPdfBusy(""); }
  };

  return <>
    <PageHeader title="Billing dashboard" description="View and pay your MoneyBags credit-card statements." />
    {actionError && <ErrorState error={actionError} />}
    <section class="mb-billing-hero" aria-label="Billing summary">
      <article class="mb-billing-balance">
        <span>Total outstanding</span><strong><Money value={outstanding} /></strong>
        <div><small>Minimum due <b><Money value={minimum} /></b></small><small>Due date <b>{next ? formatDate(next.paymentDueDate) : "No payment due"}</b></small></div>
        <p>{latestCard ? accountLabel(latestCard) : latest ? mask(latest.accountId) : "No generated statement"}</p>
      </article>
      <article class="mb-billing-insight"><span class="mb-billing-insight-icon">✦</span><h2>Payment insight</h2><p>{outstanding > 0 ? `Pay at least ₹${minimum.toLocaleString("en-IN")} by ${formatDate(next?.paymentDueDate)} to keep your account current.` : "You have no outstanding statement balance. Your next generated bill will appear here."}</p></article>
    </section>
    <Panel title="Billing history">
      {bills.length ? <div class="mb-table-wrap" id="billing-history"><table class="mb-table"><thead><tr><th>Billing period</th><th>Account</th><th>Amount due</th><th>Generated</th><th>Payment status</th><th>Reference</th><th>Actions</th></tr></thead><tbody>
        {bills.map(bill => <tr key={bill.billId}><td>{formatDate(bill.periodStart)} – {formatDate(bill.periodEnd)}</td><td>{mask(bill.accountId)}</td><td><Money value={bill.totalAmountDue} currency={bill.currency} /></td><td>{formatDate(bill.generatedAt)}</td><td><Status value={bill.status} /></td><td>{bill.billId}</td><td><div class="mb-row-actions"><button disabled={pdfBusy === `${bill.billId}-inline`} onClick={() => openPdf(bill, "inline")}>View</button><button disabled={pdfBusy === `${bill.billId}-attachment`} onClick={() => openPdf(bill, "attachment")}>Download</button>{isBillPayable(bill.status,bill.outstandingAmount) && <button onClick={() => navigate(`/app/bills/${bill.billId}/pay`)}>Pay</button>}</div></td></tr>)}
      </tbody></table></div> : <EmptyState title="No billing statements yet" message="Your statement will appear here after the billing cycle is processed. You will be notified when it is ready." />}
    </Panel>
  </>;
}

function Progress({ step }: { step: number }) {
  return <ol class="mb-billing-progress" aria-label="Statement generation progress">
    {["Account", "Billing period", "Review", "Generate"].map((label, index) => <li class={index < step ? "is-complete" : index === step ? "is-active" : ""} aria-current={index === step ? "step" : undefined}><span>{index < step ? "✓" : index + 1}</span><b>{label}</b></li>)}
  </ol>;
}

function Summary({ preview }: { preview: StatementPreview }) {
  const entries: [string, number, string?][] = [
    ["Opening balance", preview.openingBalance], ["Payments received", preview.paymentsReceived, "success"],
    ["New purchases", preview.newPurchases], ["Fees", preview.fees], ["Taxes", preview.taxes],
    ["Finance charges", preview.financeCharges], ["Minimum amount due", preview.minimumAmountDue], ["Total amount due", preview.totalAmountDue]
  ];
  return <section class="mb-statement-summary"><h2>Statement summary</h2><div>{entries.map(([label, value, tone]) => <article class={tone === "success" ? "is-success" : ""}><span>{label}</span><strong><Money value={value} currency={preview.currency} /></strong></article>)}</div><footer><span>Payment due date</span><strong>{formatDate(preview.paymentDueDate)}</strong></footer></section>;
}

function TransactionRows({ lines, currency }: { lines: BillLine[]; currency: string }) {
  const visible = lines.filter(line => line.lineType !== "PREVIOUS_BALANCE");
  if (!visible.length) return <EmptyState title="No eligible transactions" message="There is no posted activity for this account and billing period. You can choose another period without losing your account selection." />;
  return <div class="mb-statement-transactions">{visible.map((line, index) => {
    const incoming = line.lineType === "PAYMENT" || Number(line.amount) < 0;
    return <details key={`${line.occurredAt}-${index}`}><summary><span class="mb-transaction-icon">{incoming ? "↓" : "↑"}</span><span><b>{line.description || line.lineType.replaceAll("_", " ")}</b><small>{formatDate(line.occurredAt)} · {line.lineType.replaceAll("_", " ")}</small></span><strong class={incoming ? "is-success" : ""}>{incoming ? "− " : ""}<Money value={Math.abs(Number(line.amount))} currency={currency} /></strong></summary><dl><dt>Posting date</dt><dd>{formatDate(line.occurredAt)}</dd><dt>Category</dt><dd>{line.lineType.replaceAll("_", " ")}</dd><dt>Reference</dt><dd>{line.sourceReference || "Not supplied"}</dd></dl></details>;
  })}</div>;
}

export function BillingStatementWizard() {
  const id = customerId();
  const accountsRemote = useRemote(signal => id ? services.cards.accounts(id, signal) : Promise.resolve([] as CardAccount[]), [id]);
  const [draft, setDraft] = useState<Draft>(initialDraft);
  const [step, setStep] = useState(0);
  const [preview, setPreview] = useState<StatementPreview>();
  const [result, setResult] = useState<Bill>();
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const [processing, setProcessing] = useState(false);
  const commands = useIdempotencyKeyStore();
  const today = localIso(new Date());
  const activeCards = (accountsRemote.data ?? []).filter(card => card.status === "ACTIVE");
  const selected = activeCards.find(card => String(card.accountId) === draft.accountId);
  const invalidPeriod = !draft.startDate || !draft.endDate || draft.startDate > draft.endDate || draft.endDate > today;

  useEffect(() => {
    if (!draft.accountId && activeCards.length) setDraft(current => ({ ...current, accountId: String(activeCards[0].accountId) }));
  }, [accountsRemote.data]);

  const updatePeriod = (choice: PeriodChoice) => {
    const [startDate, endDate] = periodRange(choice);
    setDraft(current => ({ ...current, periodChoice: choice, startDate, endDate })); setPreview(undefined); setError(undefined);
  };
  const request = () => ({ accountId: draft.accountId, startDate: draft.startDate, endDate: draft.endDate, saveToHistory: draft.saveToHistory });
  const loadPreview = async () => {
    if (invalidPeriod) return;
    setBusy(true); setError(undefined);
    try { setPreview(await services.bills.preview(request(), commands.keyFor({ ...request(), operation: "preview" }))); commands.reset(); setStep(2); } catch (reason) { setError(reason); } finally { setBusy(false); }
  };
  const generate = async () => {
    if (busy || processing || preview?.duplicate) return;
    setBusy(true); setProcessing(true); setError(undefined);
    try { const bill = await services.bills.generate(request(), commands.keyFor({ ...request(), operation: "generate" })); commands.reset(); sessionStorage.removeItem(DRAFT_KEY); setResult(bill); } catch (reason) { setError(reason); setProcessing(false); } finally { setBusy(false); }
  };
  const saveDraft = () => { sessionStorage.setItem(DRAFT_KEY, JSON.stringify(draft)); navigate("/app/bills"); };
  const pdf = async (bill: Bill, disposition: "inline" | "attachment") => { setError(undefined); try { await statementPdf(bill, disposition); } catch (reason) { setError(reason); } };

  if (accountsRemote.loading) return <Loading label="Loading eligible billing accounts" />;
  if (accountsRemote.error) return <ErrorState error={accountsRemote.error} retry={accountsRemote.retry} />;
  if (processing && !result) return <div class="mb-billing-processing"><span class="mb-spinner" aria-hidden="true" /><h1>We're preparing your billing statement.</h1><p>This usually takes only a moment. Please keep this page open.</p></div>;
  if (result) return <div class="mb-billing-success"><span>✓</span><h1>Your billing statement has been generated successfully.</h1><dl><dt>Statement reference</dt><dd>{result.billId}</dd><dt>Account</dt><dd>{mask(result.accountId)}</dd><dt>Billing period</dt><dd>{formatDate(result.periodStart)} – {formatDate(result.periodEnd)}</dd><dt>Generated date</dt><dd>{formatDate(result.generatedAt)}</dd><dt>Amount due</dt><dd><Money value={result.totalAmountDue} currency={result.currency} /></dd><dt>PDF password</dt><dd>{result.pdfPassword}</dd></dl>{error && <ErrorState error={error} />}<div class="mb-row-actions"><button onClick={() => pdf(result, "inline")}>View statement</button><button onClick={() => pdf(result, "attachment")}>Download PDF</button><button onClick={() => navigate("/app/bills#billing-history")}>Go to Billing History</button>{isBillPayable(result.status,result.outstandingAmount)&&<button onClick={() => navigate(`/app/bills/${result.billId}/pay`)}>Pay now</button>}<button class="mb-button mb-button-primary" onClick={() => { setResult(undefined); setPreview(undefined); setProcessing(false); setStep(0); }}>Generate another bill</button></div></div>;

  return <>
    <PageHeader title="Generate billing statement" description="Review posted credit-card activity and create a protected PDF statement." />
    <Progress step={step} />
    {error && <ErrorState error={error} retry={step === 2 ? loadPreview : undefined} />}
    {step === 0 && <Panel title="Select account">
      <p class="mb-page-note">Billing statements are currently available for active MoneyBags credit cards. Deposit-account statements remain a separate account-statement experience.</p>
      {activeCards.length ? <div class="mb-billing-account-list">{activeCards.map(card => <button class={draft.accountId === String(card.accountId) ? "is-selected" : ""} onClick={() => setDraft(current => ({ ...current, accountId: String(card.accountId) }))}><span><b>{card.productCode}</b><small>{mask(card.cardNumber || card.accountId)}</small></span><span><b><Money value={card.outstandingAmount} /></b><Status value={card.status} /></span></button>)}</div> : <EmptyState title="No eligible accounts" message="An active credit card is required before a billing statement can be generated." />}
      <div class="mb-wizard-actions"><button onClick={() => navigate("/app/bills")}>Back</button><button class="mb-button mb-button-primary" disabled={!selected} onClick={() => setStep(1)}>Continue to billing period</button></div>
    </Panel>}
    {step === 1 && <Panel title="Select billing period">
      <div class="mb-period-options">{(["current", "previous", "older", "custom"] as PeriodChoice[]).map(choice => <button class={draft.periodChoice === choice ? "is-selected" : ""} onClick={() => updatePeriod(choice)}><b>{choice === "current" ? "Current billing cycle" : choice === "previous" ? "Previous cycle" : choice === "older" ? "Two cycles ago" : "Custom date range"}</b><small>{choice === "custom" ? "Choose your own dates" : periodRange(choice).map(formatDate).join(" – ")}</small></button>)}</div>
      <div class="mb-form-grid"><label class="mb-field"><span>Start date</span><input type="date" value={draft.startDate} max={today} disabled={draft.periodChoice !== "custom"} onInput={event => setDraft(current => ({ ...current, startDate: (event.currentTarget as HTMLInputElement).value }))} /></label><label class="mb-field"><span>End date</span><input type="date" value={draft.endDate} min={draft.startDate} max={today} disabled={draft.periodChoice !== "custom"} onInput={event => setDraft(current => ({ ...current, endDate: (event.currentTarget as HTMLInputElement).value }))} /></label></div>
      {invalidPeriod && <p class="mb-field-error">Choose a valid date range ending today or earlier.</p>}
      <div class="mb-wizard-actions"><button onClick={() => setStep(0)}>Back</button><button class="mb-button mb-button-primary" disabled={invalidPeriod || busy} onClick={loadPreview}>{busy ? "Preparing review…" : "Review charges"}</button></div>
    </Panel>}
    {step === 2 && preview && <>
      {preview.duplicate && <div class="mb-warning-banner"><b>A statement already exists for this account and period.</b><span> Open the existing statement instead of generating a duplicate.</span><button onClick={() => navigate("/app/bills")}>View Billing History</button></div>}
      <div class="mb-review-heading"><div><span>Selected account</span><b>{selected ? accountLabel(selected) : mask(preview.accountId)}</b></div><div><span>Billing period</span><b>{formatDate(preview.periodStart)} – {formatDate(preview.periodEnd)}</b></div></div>
      <div class="mb-billing-review-grid"><Panel title="Review charges"><TransactionRows lines={preview.lines} currency={preview.currency} /></Panel><Summary preview={preview} /></div>
      <div class="mb-wizard-actions"><button onClick={() => setStep(1)}>Back</button><button class="mb-button mb-button-primary" disabled={preview.duplicate} onClick={() => setStep(3)}>Continue to generate</button></div>
    </>}
    {step === 3 && preview && <Panel title="Confirm generation">
      <div class="mb-confirm-note"><span>🔒</span><div><h2>Your PDF will be password-protected.</h2><p>The password is shown only after generation and follows the MoneyBags account-and-period convention.</p></div></div>
      <label class="mb-check-row"><input type="checkbox" checked={draft.saveToHistory} onChange={event => setDraft(current => ({ ...current, saveToHistory: (event.currentTarget as HTMLInputElement).checked }))} /><span><b>Save this statement to Billing History</b><small>You can view or download it again later.</small></span></label>
      <Summary preview={preview} />
      <div class="mb-wizard-actions"><button onClick={() => setStep(2)}>Back</button><button onClick={saveDraft}>Save Draft</button><button class="mb-button mb-button-primary" disabled={busy || preview.duplicate} onClick={generate}>{busy ? "Generating…" : "Generate Bill"}</button></div>
    </Panel>}
  </>;
}
