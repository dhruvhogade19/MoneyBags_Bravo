import { h } from "preact";
import { useState } from "preact/hooks";
import type { Bill, BillLine, CardAccount, StatementPreview } from "../contracts";
import { EmptyState, ErrorState, Field, Loading, Money, PageHeader, Panel, Status, useIdempotencyKeyStore } from "../components/common";
import { navigate } from "../router";
import { services } from "../services";
import { formatDate } from "../utils";

type PeriodChoice = "current" | "previous" | "older" | "custom";

function iso(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function range(choice: PeriodChoice): [string, string] {
  const today = new Date();
  if (choice === "current" || choice === "custom") return [iso(new Date(today.getFullYear(), today.getMonth(), 1)), iso(today)];
  const offset = choice === "previous" ? 1 : 2;
  return [iso(new Date(today.getFullYear(), today.getMonth() - offset, 1)), iso(new Date(today.getFullYear(), today.getMonth() - offset + 1, 0))];
}

function mask(value: string | number): string {
  const text = String(value);
  return `•••• •••• •••• ${text.slice(-4)}`;
}

function Progress({ step }: { step: number }) {
  return <ol class="mb-billing-progress mb-billing-progress-five" aria-label="Admin statement generation progress">
    {["Customer", "Account", "Billing period", "Review", "Generate"].map((label, index) => <li class={index < step ? "is-complete" : index === step ? "is-active" : ""} aria-current={index === step ? "step" : undefined}><span>{index < step ? "✓" : index + 1}</span><b>{label}</b></li>)}
  </ol>;
}

function Summary({ preview }: { preview: StatementPreview }) {
  const entries: [string, number, boolean?][] = [
    ["Opening balance", preview.openingBalance], ["Payments received", preview.paymentsReceived, true],
    ["New purchases", preview.newPurchases], ["Fees", preview.fees], ["Taxes", preview.taxes],
    ["Finance charges", preview.financeCharges], ["Minimum amount due", preview.minimumAmountDue], ["Total amount due", preview.totalAmountDue]
  ];
  return <section class="mb-statement-summary"><h2>Statement summary</h2><div>{entries.map(([label, value, incoming]) => <article class={incoming ? "is-success" : ""}><span>{label}</span><strong><Money value={value} currency={preview.currency}/></strong></article>)}</div><footer><span>Payment due date</span><strong>{formatDate(preview.paymentDueDate)}</strong></footer></section>;
}

function Transactions({ lines, currency }: { lines: BillLine[]; currency: string }) {
  const visible = lines.filter(line => line.lineType !== "PREVIOUS_BALANCE");
  if (!visible.length) return <EmptyState title="No eligible transactions" message="No posted card activity exists for this customer, card and billing period."/>;
  return <div class="mb-statement-transactions">{visible.map((line, index) => <details key={`${line.occurredAt}-${index}`}><summary><span class="mb-transaction-icon">{Number(line.amount) < 0 ? "↓" : "↑"}</span><span><b>{line.description}</b><small>{formatDate(line.occurredAt)} · {line.lineType.replaceAll("_", " ")}</small></span><strong class={Number(line.amount) < 0 ? "is-success" : ""}><Money value={Math.abs(Number(line.amount))} currency={currency}/></strong></summary><dl><dt>Reference</dt><dd>{line.sourceReference || "Not supplied"}</dd><dt>Posting date</dt><dd>{formatDate(line.occurredAt)}</dd></dl></details>)}</div>;
}

async function openPdf(bill: Bill, disposition: "inline" | "attachment") {
  const blob = await services.bills.pdf(bill.billId, disposition);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.target = disposition === "inline" ? "_blank" : "_self";
  if (disposition === "attachment") link.download = `MoneyBags-${bill.billingPeriod}-${bill.billId}.pdf`;
  document.body.appendChild(link); link.click(); link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 30000);
}

export function AdminBillingStatementWizard() {
  const [step, setStep] = useState(0);
  const [cif, setCif] = useState("");
  const [cards, setCards] = useState<CardAccount[]>([]);
  const [accountId, setAccountId] = useState("");
  const [period, setPeriod] = useState<PeriodChoice>("current");
  const initial = range("current");
  const [startDate, setStartDate] = useState(initial[0]);
  const [endDate, setEndDate] = useState(initial[1]);
  const [preview, setPreview] = useState<StatementPreview>();
  const [result, setResult] = useState<Bill>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const commands = useIdempotencyKeyStore();
  const today = iso(new Date());
  const invalidPeriod = !startDate || !endDate || startDate > endDate || endDate > today;
  const selected = cards.find(card => String(card.accountId) === accountId);

  const selectPeriod = (choice: PeriodChoice) => { const next = range(choice); setPeriod(choice); setStartDate(next[0]); setEndDate(next[1]); setPreview(undefined); setError(undefined); };
  const findCustomerCards = async () => {
    if (!/^\d+$/.test(cif) || Number(cif) <= 0) return;
    setBusy(true); setError(undefined);
    try {
      const active = (await services.cards.accounts(cif)).filter(card => card.status === "ACTIVE");
      setCards(active); setAccountId(active.length ? String(active[0].accountId) : ""); setStep(1);
    } catch (reason) { setError(reason); } finally { setBusy(false); }
  };
  const body = () => ({ cifId: Number(cif), accountId, startDate, endDate });
  const review = async () => {
    if (invalidPeriod || !selected) return;
    setBusy(true); setError(undefined);
    try { setPreview(await services.bills.adminPreview(body(), commands.keyFor({ ...body(), operation: "admin-preview" }))); commands.reset(); setStep(3); }
    catch (reason) { setError(reason); } finally { setBusy(false); }
  };
  const generate = async () => {
    if (!preview || preview.duplicate || busy) return;
    setBusy(true); setError(undefined);
    try { setResult(await services.bills.adminGenerate(body(), commands.keyFor({ ...body(), operation: "admin-generate" }))); commands.reset(); }
    catch (reason) { setError(reason); } finally { setBusy(false); }
  };

  if (busy && step === 4 && !result) return <Loading label="We're preparing the customer's billing statement"/>;
  if (result) return <div class="mb-billing-success"><span>✓</span><h1>The billing statement has been generated successfully.</h1><p>This statement is retained in Billing History for audit.</p><dl><dt>Statement reference</dt><dd>{result.billId}</dd><dt>Customer CIF</dt><dd>{cif}</dd><dt>Account</dt><dd>{mask(result.accountId)}</dd><dt>Billing period</dt><dd>{formatDate(result.periodStart)} – {formatDate(result.periodEnd)}</dd><dt>Generated</dt><dd>{formatDate(result.generatedAt)}</dd><dt>Amount due</dt><dd><Money value={result.totalAmountDue} currency={result.currency}/></dd><dt>PDF password</dt><dd>{result.pdfPassword}</dd></dl>{error && <ErrorState error={error}/>}<div class="mb-row-actions"><button onClick={() => openPdf(result, "inline").catch(setError)}>View statement</button><button onClick={() => openPdf(result, "attachment").catch(setError)}>Download PDF</button><button onClick={() => navigate("/ops/cards")}>Cards & billing</button><button class="mb-button mb-button-primary" onClick={() => { setResult(undefined); setPreview(undefined); setStep(0); }}>Generate another</button></div></div>;

  return <><PageHeader title="Generate customer bill" description="Create an auditable statement for an active card owned by the selected customer." action={<button class="mb-button mb-button-secondary" onClick={() => navigate("/ops/cards")}>Back to Cards & billing</button>}/><Progress step={step}/>{error && <ErrorState error={error} retry={step === 3 ? review : undefined}/>}
    {step === 0 && <Panel title="Find customer"><div class="mb-form-grid"><Field label="Customer CIF" name="cif" type="number" min="1" value={cif} required onInput={setCif}/><div class="mb-field"><span>&nbsp;</span><button class="mb-button mb-button-primary" disabled={busy || !/^\d+$/.test(cif)} onClick={findCustomerCards}>{busy ? "Searching…" : "Find active cards"}</button></div></div></Panel>}
    {step === 1 && <Panel title={`Select active card · CIF ${cif}`}>{cards.length ? <div class="mb-billing-account-list">{cards.map(card => <button class={accountId === String(card.accountId) ? "is-selected" : ""} onClick={() => setAccountId(String(card.accountId))}><span><b>{card.productCode}</b><small>{mask(card.cardNumber || card.accountId)}</small></span><span><b><Money value={card.outstandingAmount}/></b><Status value={card.status}/></span></button>)}</div> : <EmptyState title="No active cards" message="This customer has no active credit card eligible for billing."/>}<div class="mb-wizard-actions"><button onClick={() => setStep(0)}>Back</button><button class="mb-button mb-button-primary" disabled={!selected} onClick={() => setStep(2)}>Continue</button></div></Panel>}
    {step === 2 && <Panel title="Select billing period"><div class="mb-period-options">{(["current", "previous", "older", "custom"] as PeriodChoice[]).map(choice => <button class={period === choice ? "is-selected" : ""} onClick={() => selectPeriod(choice)}><b>{choice === "current" ? "Current billing cycle" : choice === "previous" ? "Previous cycle" : choice === "older" ? "Two cycles ago" : "Custom date range"}</b><small>{choice === "custom" ? "Choose your own dates" : range(choice).map(formatDate).join(" – ")}</small></button>)}</div><div class="mb-form-grid"><label class="mb-field"><span>Start date</span><input type="date" value={startDate} max={today} disabled={period !== "custom"} onInput={event => setStartDate((event.currentTarget as HTMLInputElement).value)}/></label><label class="mb-field"><span>End date</span><input type="date" value={endDate} min={startDate} max={today} disabled={period !== "custom"} onInput={event => setEndDate((event.currentTarget as HTMLInputElement).value)}/></label></div>{invalidPeriod && <p class="mb-field-error">Choose a valid period ending today or earlier.</p>}<div class="mb-wizard-actions"><button onClick={() => setStep(1)}>Back</button><button class="mb-button mb-button-primary" disabled={invalidPeriod || busy} onClick={review}>{busy ? "Preparing…" : "Preview charges"}</button></div></Panel>}
    {step === 3 && preview && <>{preview.duplicate && <div class="mb-warning-banner"><b>A retained statement already exists for this card and period.</b><span> Duplicate generation is blocked by the billing service.</span></div>}<div class="mb-review-heading"><div><span>Customer</span><b>CIF {cif}</b></div><div><span>Selected card</span><b>{selected ? `${selected.productCode} · ${mask(selected.cardNumber || selected.accountId)}` : mask(preview.accountId)}</b></div><div><span>Billing period</span><b>{formatDate(preview.periodStart)} – {formatDate(preview.periodEnd)}</b></div></div><div class="mb-billing-review-grid"><Panel title="Posted charges"><Transactions lines={preview.lines} currency={preview.currency}/></Panel><Summary preview={preview}/></div><div class="mb-wizard-actions"><button onClick={() => setStep(2)}>Back</button><button class="mb-button mb-button-primary" disabled={preview.duplicate} onClick={() => setStep(4)}>Continue</button></div></>}
    {step === 4 && preview && <Panel title="Confirm generation"><div class="mb-confirm-note"><span>🔒</span><div><h2>The PDF will be password-protected.</h2><p>Admin-generated statements are always retained for audit. Bank operations cannot initiate a customer repayment.</p></div></div><Summary preview={preview}/><div class="mb-wizard-actions"><button onClick={() => setStep(3)}>Back</button><button class="mb-button mb-button-primary" disabled={busy || preview.duplicate} onClick={generate}>{busy ? "Generating…" : "Generate and retain bill"}</button></div></Panel>}
  </>;
}
