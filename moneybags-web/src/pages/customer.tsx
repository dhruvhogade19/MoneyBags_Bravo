import { h } from "preact";
import { useEffect, useMemo, useState } from "preact/hooks";
import { auth } from "../auth";
import { ApiError } from "../api";
import type { Account, Bill, CardAccount, CardApplication, Cif, EligibilityResult, FixedDeposit, KycDocument, Notification, Payment, Product } from "../contracts";
import { EmptyState, ErrorState, Field, Icon, Loading, Money, PageHeader, Panel, Receipt, SelectField, Status, useIdempotencyKeyStore, useRemote } from "../components/common";
import { navigate } from "../router";
import { items, services } from "../services";
import { formatDate } from "../utils";

function customerId(): string | undefined {
  const value = auth.session?.claims.customer_id;
  return value === undefined || value === null ? undefined : String(value);
}

function date(value?: string): string { return formatDate(value); }

const REQUIRED_KYC_DOCUMENTS = [
  { type: "PAN", label: "PAN card" },
  { type: "AADHAAR", label: "Aadhaar card" },
  { type: "ADDRESS_PROOF", label: "Address proof" },
  { type: "SALARY_PROOF", label: "Salary proof" }
] as const;

async function activateCustomerProfile(): Promise<Cif> {
  const cif = await services.cif.repairIdentityLink();
  const refreshed = await auth.refreshSession();
  if (refreshed.claims.customer_id == null) {
    throw new Error("Your profile was created, but the secure customer session was not updated. Please sign out and sign in again.");
  }
  return cif;
}

export function CustomerDashboard() {
  const id = customerId();
  const remote = useRemote(async (signal) => {
    if (!id) return { cif: undefined, accounts: [], fds: [], cards: [], payments: [], notifications: [] };
    const results = await Promise.allSettled([
      services.cif.get(id, signal), services.accounts.list(id, signal), services.fixedDeposits.list(id, signal), services.cards.accounts(id, signal), services.payments.list(id, signal), services.notifications.list(id, signal)
    ]);
    const value = <T,>(index: number): T | undefined => results[index].status === "fulfilled" ? (results[index] as PromiseFulfilledResult<T>).value : undefined;
    return {
      cif: value<Cif>(0), accounts: items<Account>(value<unknown>(1) as any), fds: items<FixedDeposit>(value<unknown>(2) as any), cards: value<CardAccount[]>(3) ?? [],
      payments: items<Payment>(value<unknown>(4) as any), notifications: items<Notification>(value<unknown>(5) as any), unavailable: results.filter((result) => result.status === "rejected").length
    };
  }, [id]);
  if (remote.loading) return <Loading label="Preparing your overview" />;
  if (remote.error) return <ErrorState error={remote.error} retry={remote.retry} />;
  const data = remote.data!;
  if (!id) return <OnboardingRequired />;
  const casaAccounts = (data.accounts as Account[]).filter(account => account.productSubtype !== "FIXED_DEPOSIT");
  const available = casaAccounts.reduce((sum, account) => sum + Number(account.availableBalance ?? 0), 0);
  const fdPrincipal = (data.fds as FixedDeposit[]).reduce((sum, fd) => sum + Number(fd.principal ?? 0), 0);
  const outstanding = (data.cards as CardAccount[]).reduce((sum, card) => sum + Number(card.outstandingAmount ?? 0), 0);
  const hasBankingData = data.accounts.length > 0 || data.fds.length > 0 || data.cards.length > 0 || data.payments.length > 0;
  return <>
    <PageHeader title={`Good morning${data.cif?.firstName ? `, ${data.cif.firstName}` : ""}`} description="Your money is clearly organised today." action={<button class="mb-button mb-button-primary" onClick={() => navigate("/app/payments/transfer")}><Icon name="arrow" /> Transfer money</button>} />
    {Boolean(data.unavailable) && <div class="mb-warning-banner">Some services are temporarily unavailable. Available information is still shown.</div>}
    <div class="mb-dashboard-grid">
      <section class="mb-balance-card"><div class="mb-balance-content"><span>Available across your accounts</span><strong><Money value={available} /></strong><small>{casaAccounts.length} Savings/Current account{casaAccounts.length === 1 ? "" : "s"}</small><div><button onClick={() => navigate("/app/payments/transfer")}>Send money ↗</button><button onClick={() => navigate("/app/accounts/open")}>Open account +</button></div></div></section>
      <section class="mb-editorial-card"><span class="mb-editorial-mark">✦</span><h2>Banking,<br/>made clearer.</h2><p>{data.cif?.kycStatus === "APPROVED" ? "Your KYC is verified and all eligible products are available." : "Complete your profile and KYC to unlock every product."}</p><button onClick={() => navigate(data.cif?.kycStatus === "APPROVED" ? "/app/products" : "/app/kyc")}>{data.cif?.kycStatus === "APPROVED" ? "Explore products" : "Complete KYC"} →</button></section>
    </div>
    <div class="mb-summary-row"><article><span>Fixed deposits</span><strong><Money value={fdPrincipal} /></strong><small>{data.fds.length} deposit{data.fds.length === 1 ? "" : "s"}</small></article><article><span>Card outstanding</span><strong><Money value={outstanding} /></strong><small>{data.cards.length} active card{data.cards.length === 1 ? "" : "s"}</small></article><article><span>Profile status</span><strong class="mb-summary-status">{data.cif?.kycStatus ?? "Not started"}</strong><small>CIF {id}</small></article></div>
    {!hasBankingData && <Panel><EmptyState title="Nothing to show yet" message="Open an account or apply for a product to start seeing your banking activity."/></Panel>}
    <Panel title="Recent activity" action={<button class="mb-text-button" onClick={() => navigate("/app/payments")}>See all</button>}>
      <PaymentRows payments={(data.payments as Payment[]).slice(0, 5)} />
    </Panel>
  </>;
}

function OnboardingRequired() {
  return <><PageHeader title="Welcome to MoneyBags" description="Create your customer profile to begin."/><Panel><EmptyState title="Your customer profile is not linked yet" message="Complete onboarding to access accounts, cards and payments."/><button class="mb-button mb-button-primary mb-center-action" onClick={() => navigate("/app/profile")}>Start onboarding</button></Panel></>;
}

export function ProfilePage() {
  const id = customerId();
  const [created, setCreated] = useState<Cif>();
  const [editing, setEditing] = useState(false);
  const remote = useRemote(async (signal) => id ? services.cif.get(id, signal) : undefined, [id, created?.cifId]);
  if (remote.loading) return <Loading label="Loading profile" />;
  if (remote.error && id) return <ErrorState error={remote.error} retry={remote.retry} />;
  const cif = created ?? remote.data;
  if (!cif) return <CifForm onCreated={setCreated} />;
  if (editing) return <CifForm existing={cif} onCreated={(updated) => { setCreated(updated); setEditing(false); }} onCancel={() => setEditing(false)} />;
  const age = ageFromDob(cif.dob);
  return <><PageHeader title="Profile & KYC" description="Your personal and verification details." action={<Status value={cif.kycStatus} />}/><div class="mb-detail-grid"><Panel title="Personal details"><dl class="mb-detail-list"><dt>Name</dt><dd>{cif.firstName} {cif.lastName}</dd><dt>CIF</dt><dd>{cif.cifId}</dd><dt>Date of birth</dt><dd>{date(cif.dob)} ({age} years)</dd><dt>Email</dt><dd>{cif.email}</dd><dt>Mobile</dt><dd>{cif.number}</dd><dt>Employment</dt><dd>{cif.employmentType}</dd><dt>Monthly income</dt><dd>{cif.salary == null ? "—" : `₹${Number(cif.salary).toLocaleString("en-IN")}`}</dd></dl><button class="mb-button mb-button-secondary" onClick={() => setEditing(true)}>Correct profile details</button></Panel><Panel title="Protected information"><dl class="mb-detail-list"><dt>PAN</dt><dd>••••••{cif.panNumber?.slice(-4)}</dd><dt>Aadhaar</dt><dd>•••• •••• {cif.aadhaarNumber?.slice(-4)}</dd><dt>Address</dt><dd>{cif.address}</dd></dl><button class="mb-button mb-button-secondary" onClick={() => navigate("/app/kyc")}>View KYC</button></Panel></div></>;
}

function ageFromDob(value: string): number {
  const dob = new Date(`${value}T00:00:00`); const today = new Date();
  return Math.max(0, today.getFullYear() - dob.getFullYear() - (today < new Date(today.getFullYear(), dob.getMonth(), dob.getDate()) ? 1 : 0));
}

function latestAdultDob(): string {
  const value = new Date(); value.setFullYear(value.getFullYear() - 18); return value.toISOString().slice(0, 10);
}

function CifForm({ onCreated, existing, onCancel }: { onCreated: (value: Cif) => void; existing?: Cif; onCancel?: () => void }) {
  const [values, setValues] = useState<Record<string, string>>(existing ? {
    firstName: existing.firstName, lastName: existing.lastName, dob: existing.dob, email: existing.email,
    number: existing.number, salary: existing.salary == null ? "" : String(existing.salary),
    panNumber: existing.panNumber, aadhaarNumber: existing.aadhaarNumber, address: existing.address,
    employmentType: existing.employmentType
  } : { employmentType: "SALARIED" });
  const [error, setError] = useState<unknown>(); const [busy, setBusy] = useState(false);
  const commands = useIdempotencyKeyStore();
  const set = (key: string) => (value: string) => setValues((old) => ({ ...old, [key]: value }));
  const employmentType = values.employmentType || "SALARIED";
  const changeEmployment = (value: string) => setValues((old) => ({ ...old, employmentType: value, ...(value === "STUDENT" ? { salary: "" } : {}) }));
  const submit = async (event: Event) => {
    event.preventDefault();
    setBusy(true);
    setError(undefined);
    try {
      const age = ageFromDob(values.dob);
      const body = {
        ...values,
        age,
        employmentType,
        salary: employmentType === "STUDENT" ? null : Number(values.salary),
        panNumber: values.panNumber?.trim().toUpperCase(),
        aadhaarNumber: values.aadhaarNumber?.trim(),
        number: values.number?.trim()
      };
      if (age < 18) throw new Error("Deposit and card products require the customer to be at least 18 years old.");
      const created = existing
        ? await services.cif.update(existing.cifId, body)
        : await services.cif.create(body, commands.keyFor(body));
      commands.reset();
      if (!existing) await activateCustomerProfile();
      onCreated(created);
      if (!existing) navigate("/app/overview", true);
    } catch (reason) {
      if (!existing && reason instanceof ApiError && reason.problem.status === 409) {
        try {
          const existing = await services.cif.me();
          await activateCustomerProfile();
          commands.reset();
          onCreated(existing);
          navigate("/app/overview", true);
          return;
        } catch (recoveryError) {
          setError(recoveryError);
        }
      } else {
        setError(reason);
      }
    } finally {
      setBusy(false);
    }
  };
  return <>
    <PageHeader title={existing ? "Correct profile details" : "Create your profile"} description={existing ? "Changes to identity or eligibility data require a fresh demo KYC approval." : "Tell us about yourself. Required fields are marked with an asterisk."}/>
    <Panel>
      <form class="mb-form" onSubmit={submit}>
        {error && <ErrorState error={error}/>} 
        <div class="mb-form-grid">
          <Field label="First name" name="firstName" value={values.firstName} required onInput={set("firstName")}/>
          <Field label="Last name" name="lastName" value={values.lastName} required onInput={set("lastName")}/>
          <Field label="Date of birth" name="dob" type="date" value={values.dob} max={latestAdultDob()} required hint="You must be at least 18 to use the available deposit and card products." onInput={set("dob")}/>
          <Field label="Email" name="email" type="email" value={values.email} required onInput={set("email")}/>
          <Field label="Mobile number" name="number" value={values.number} required inputMode="numeric" pattern="[0-9]{10,15}" maxLength={15} hint="Enter 10 to 15 digits." onInput={set("number")}/>
          {employmentType !== "STUDENT" && <Field label="Monthly salary" name="salary" type="number" value={values.salary} min="0.01" step="0.01" required hint="A positive salary is required for salaried and business customers." onInput={set("salary")}/>} 
          <Field label="PAN number" name="panNumber" value={values.panNumber} required pattern="[A-Za-z]{5}[0-9]{4}[A-Za-z]" maxLength={10} placeholder="ABCDE1234F" title="PAN must contain five letters, four digits, and one final letter" hint="Format: five letters, four digits, one letter." onInput={set("panNumber")}/>
          <Field label="Aadhaar number" name="aadhaarNumber" value={values.aadhaarNumber} required inputMode="numeric" pattern="[0-9]{12}" maxLength={12} placeholder="12 digits" hint="Enter exactly 12 digits." onInput={set("aadhaarNumber")}/>
        </div>
        <label class="mb-field"><span>Employment type *</span><select required value={employmentType} onChange={(e) => changeEmployment((e.currentTarget as HTMLSelectElement).value)}><option value="SALARIED">Salaried</option><option value="BUSINESS">Business</option><option value="STUDENT">Student</option></select></label>
        <label class="mb-field"><span>Address *</span><textarea required value={values.address} onInput={(e) => set("address")((e.currentTarget as HTMLTextAreaElement).value)}></textarea></label>
        <div class="mb-row-actions"><button class="mb-button mb-button-primary" disabled={busy}>{busy ? "Saving profile…" : existing ? "Save and resubmit KYC" : "Create profile"}</button>{onCancel&&<button type="button" class="mb-button mb-button-secondary" onClick={onCancel}>Cancel</button>}</div>
      </form>
    </Panel>
  </>;
}

export function KycPage() {
  const id = customerId();
  const [revision, setRevision] = useState(0);
  const [files, setFiles] = useState<Record<string, File | undefined>>({});
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<unknown>();
  const commands = useIdempotencyKeyStore();
  const remote = useRemote(async (signal) => {
    if (!id) return undefined;
    const [cif, records] = await Promise.all([services.cif.get(id, signal), services.kyc.byCif(id, signal)]);
    const latest = records[0];
    return { cif, latest, documents: latest ? await services.kyc.documents(latest.kycId, signal) : [] };
  }, [id, revision]);
  if (!id) return <OnboardingRequired/>;
  if (remote.loading) return <Loading label="Loading KYC"/>;
  if (remote.error || !remote.data) return <ErrorState error={remote.error} retry={remote.retry}/>;
  const { cif, latest, documents } = remote.data;
  if (!latest) return <><PageHeader title="KYC is being initiated" description="Your CIF profile was created successfully."/><Panel><div class="mb-warning-banner">KYC initiation is still being processed for CIF {cif.cifId}. Refresh in a moment.</div><button class="mb-button mb-button-secondary" onClick={() => setRevision(n => n + 1)}>Refresh KYC status</button></Panel></>;
  const documentByType = new Map(documents.map(document => [document.documentType, document]));
  const missing = REQUIRED_KYC_DOCUMENTS.filter(document => !documentByType.has(document.type));
  const finalised = ["APPROVED", "REJECTED"].includes(latest.kycStatus);
  const statusMessage = latest.kycStatus === "APPROVED" ? "Your KYC has been approved." : latest.kycStatus === "REJECTED" ? "Your KYC has been rejected." : latest.kycStatus === "FLAGGED" ? "A document needs additional review by the bank." : missing.length ? "Upload the required documents to submit your KYC for review." : "Your documents are with a bank administrator for review.";
  const upload = async (event: Event) => {
    event.preventDefault();
    const selected = missing.map(document => ({ type: document.type, file: files[document.type] })).filter((document): document is { type: typeof REQUIRED_KYC_DOCUMENTS[number]["type"]; file: File } => document.file instanceof File);
    if (selected.length !== missing.length) { setUploadError(new Error("Select a file for every required document before submitting.")); return; }
    setUploading(true); setUploadError(undefined);
    try {
      await services.kyc.uploadBatch(latest.kycId, selected, commands.keyFor({ kycId: latest.kycId, documents: selected.map(document => document.type) }));
      commands.reset(); setFiles({}); setRevision(value => value + 1);
    } catch (error) { setUploadError(error); } finally { setUploading(false); }
  };
  return <><PageHeader title="Know Your Customer" description={statusMessage} action={<Status value={latest.kycStatus}/>}/><Panel title="Verification status"><div class="mb-timeline"><div class="is-complete"><b>1</b><span>Profile created<small>CIF {cif.cifId}</small></span></div><div class={documents.length === REQUIRED_KYC_DOCUMENTS.length ? "is-complete" : "is-current"}><b>2</b><span>Documents submitted<small>{documents.length} of {REQUIRED_KYC_DOCUMENTS.length} required documents</small></span></div><div class={latest.decision ? "is-complete" : documents.length === REQUIRED_KYC_DOCUMENTS.length ? "is-current" : ""}><b>3</b><span>Bank-admin review<small>{latest.decision ?? "Waiting for a final decision"}</small></span></div></div>{latest.rejectionReason && <div class="mb-error"><strong>KYC rejected</strong><p>{latest.rejectionReason}</p></div>}{latest.mismatchReason && <div class="mb-warning-banner">Review note: {latest.mismatchReason}</div>}<button class="mb-button mb-button-secondary" onClick={() => setRevision(n => n + 1)}>Refresh status</button></Panel><Panel title="Required documents" action={<span class="mb-document-count">{documents.length}/{REQUIRED_KYC_DOCUMENTS.length} complete</span>}>{uploadError && <ErrorState error={uploadError}/>}<div class="mb-document-list">{REQUIRED_KYC_DOCUMENTS.map(requirement => <CustomerDocumentRow key={requirement.type} requirement={requirement} document={documentByType.get(requirement.type)} file={files[requirement.type]} disabled={finalised || Boolean(documentByType.get(requirement.type))} onFile={(file) => setFiles(current => ({ ...current, [requirement.type]: file }))}/>)}</div>{!finalised && missing.length > 0 && <form class="mb-upload-actions" onSubmit={upload}><p>Upload all remaining documents together. PDF, PNG, or JPEG files up to 10 MB are accepted.</p><button class="mb-button mb-button-primary" disabled={uploading}>{uploading ? "Submitting documents…" : "Submit documents for review"}</button></form>}</Panel></>;
}

function CustomerDocumentRow({ requirement, document, file, disabled, onFile }: { requirement: typeof REQUIRED_KYC_DOCUMENTS[number]; document?: KycDocument; file?: File; disabled: boolean; onFile: (file?: File) => void }) {
  return <article class="mb-document-row"><div><strong>{requirement.label}</strong><small>{document ? document.originalFileName : file ? file.name : "Not uploaded"}</small></div>{document ? <Status value={document.verificationStatus}/> : <label class="mb-file-input"><span>{file ? "Change file" : "Choose file"}</span><input type="file" accept="application/pdf,image/png,image/jpeg" disabled={disabled} onChange={(event) => onFile((event.currentTarget as HTMLInputElement).files?.[0])}/></label>}</article>;
}

export function ProductsPage() {
  const remote = useRemote((signal) => services.products.active(signal), []);
  if (remote.loading) return <Loading label="Loading products"/>; if (remote.error) return <ErrorState error={remote.error} retry={remote.retry}/>;
  const products = items(remote.data);
  return <><PageHeader title="Explore products" description="Choose a product designed around your goals."/><div class="mb-product-grid">{products.map((product) => <article class="mb-product-card" key={product.productCode}><span class="mb-product-type">{product.subtype?.replaceAll("_", " ")}</span><h2>{product.productName}</h2><p>{product.description ?? "A MoneyBags product with clear terms and transparent pricing."}</p><div class="mb-product-fact"><span>Interest rate</span><strong>{product.interestRule?.annualInterestRate ?? "—"}%</strong></div><button class="mb-button mb-button-primary" onClick={() => navigate(product.category === "CREDIT_CARD" ? `/app/cards/apply?product=${product.productCode}` : product.subtype === "FIXED_DEPOSIT" ? `/app/fixed-deposits/open?product=${product.productCode}` : `/app/accounts/open?product=${product.productCode}`)}>Choose product</button></article>)}</div>{products.length === 0 && <EmptyState title="No active products" message="Product Master has not published an active product yet."/>}</>;
}

export function AccountsPage() {
  const id = customerId(); const remote = useRemote((signal) => id ? services.accounts.list(id, signal) : Promise.resolve({ content: [], page: 0, size: 0, totalElements: 0 }), [id]);
  if (remote.loading) return <Loading label="Loading accounts"/>; if (remote.error) return <ErrorState error={remote.error} retry={remote.retry}/>;
  const accounts = items(remote.data);
  return <><PageHeader title="Your accounts" description="Balances and controls across your deposit accounts." action={<button class="mb-button mb-button-primary" onClick={() => navigate("/app/accounts/open")}><Icon name="add"/> Open account</button>}/><div class="mb-account-grid">{accounts.map((account) => <article class="mb-account-card" key={account.accountId}><div><span>{account.productName}</span><Status value={account.status}/></div><strong><Money value={account.availableBalance} currency={account.currency}/></strong><small>{account.maskedAccountNumber}</small><button class="mb-text-button" onClick={() => navigate(`/app/accounts/${account.accountId}`)}>View account →</button></article>)}</div>{accounts.length === 0 && <EmptyState title="No deposit accounts" message="Open an account from the MoneyBags product catalogue."/>}</>;
}

export function AccountOpenPage() {
  const id=customerId();
  const requestedProduct=new URLSearchParams(location.search).get("product")??"";
  const productsRemote=useRemote((signal)=>services.products.active(signal),[]);
  const[values,setValues]=useState<Record<string,string>>({productId:requestedProduct,currency:"INR",openingAmount:"0",servicingBranchId:"MUM-001",operatingInstruction:"SINGLE"});
  const[eligibility,setEligibility]=useState<EligibilityResult>();
  const[result,setResult]=useState<Record<string,unknown>>();
  const[error,setError]=useState<unknown>();
  const[busy,setBusy]=useState<"check"|"submit"|"">("");
  const commands = useIdempotencyKeyStore();
  const products=items<Product>(productsRemote.data).filter(product=>["SAVINGS","CURRENT"].includes(product.subtype));
  const selected=products.find(product=>product.productCode===values.productId);
  const set=(key:string)=>(value:string)=>{setValues(old=>({...old,[key]:value}));setEligibility(undefined);setError(undefined);};
  const chooseProduct=(code:string)=>{const product=products.find(value=>value.productCode===code);setValues(old=>({...old,productId:code,currency:product?.currencyCode??"INR",openingAmount:String(product?.amountRule?.minimumOpeningBalance??0)}));setEligibility(undefined);setError(undefined);};

  useEffect(()=>{
    if(!products.length)return;
    const initial=products.find(product=>product.productCode===requestedProduct)
      ??products.find(product=>product.subtype==="SAVINGS")??products[0];
    chooseProduct(initial.productCode);
  },[productsRemote.data]);

  const eligibilityBody=()=>({customerId:id,productId:values.productId,productVersion:selected?.version,currency:values.currency,openingAmount:Number(values.openingAmount)});
  const check=async(event:Event)=>{event.preventDefault();if(!id||!selected)return;setBusy("check");setError(undefined);setEligibility(undefined);try{setEligibility(await services.accounts.eligibility(eligibilityBody()));}catch(reason){setError(reason);}finally{setBusy("");}};
  const submit=async()=>{if(!id||!selected||!eligibility?.eligible)return;setBusy("submit");setError(undefined);try{const command={customerIds:[id],primaryCustomerId:id,productId:selected.productCode,productVersion:selected.version,currency:values.currency,openingAmount:Number(values.openingAmount),servicingBranchId:values.servicingBranchId,operatingInstruction:values.operatingInstruction,nominees:[],channel:"WEB"};const key=commands.keyFor(command);setResult(await services.accounts.open({...command,externalReference:key},key) as Record<string,unknown>);commands.reset();}catch(reason){setError(reason);}finally{setBusy("");}};
  if(productsRemote.loading)return <Loading label="Loading deposit products"/>;
  if(productsRemote.error)return <ErrorState error={productsRemote.error} retry={productsRemote.retry}/>;
  if(!products.length)return <><PageHeader title="Open deposit account" description="Choose a Savings or Current account."/><Panel><EmptyState title="No deposit products available" message="Product Master has not published an active Savings or Current product."/></Panel></>;
  if(result)return <Receipt title="Account request submitted" reference={String(result.accountId??"Created")} done={()=>navigate("/app/accounts")}><Status value={String(result.status??"PENDING_ACTIVATION")}/><p>A bank administrator must approve this request before the account becomes active.</p></Receipt>;
  return <><PageHeader title="Open deposit account" description="Choose Savings or Current, check eligibility, then submit the request for bank approval."/><div class="mb-step-grid"><Panel title="1. Product and account setup"><form class="mb-form" onSubmit={check}>{error&&<ErrorState error={error}/>}<label class="mb-field"><span>Deposit product *</span><select required value={values.productId} onChange={event=>chooseProduct((event.currentTarget as HTMLSelectElement).value)}>{products.map(product=><option key={product.productCode} value={product.productCode}>{product.productName} ({product.subtype.replaceAll("_"," ")})</option>)}</select></label><div class="mb-form-grid"><Field label="Opening amount" name="openingAmount" type="number" value={values.openingAmount} min={String(selected?.amountRule?.minimumOpeningBalance??0)} step="0.01" required hint={`Minimum opening amount: ₹${Number(selected?.amountRule?.minimumOpeningBalance??0).toLocaleString("en-IN")}`} onInput={set("openingAmount")}/><Field label="Servicing branch" name="servicingBranchId" value={values.servicingBranchId} required onInput={set("servicingBranchId")}/></div><label class="mb-field"><span>Operating instruction *</span><select required value={values.operatingInstruction} onChange={event=>set("operatingInstruction")((event.currentTarget as HTMLSelectElement).value)}><option value="SINGLE">Single</option><option value="JOINTLY">Jointly</option><option value="EITHER_OR_SURVIVOR">Either or survivor</option></select></label><button class="mb-button mb-button-secondary" disabled={Boolean(busy)}>{busy==="check"?"Checking…":"Check eligibility"}</button></form></Panel><Panel title="2. Eligibility and request">{!eligibility?<EmptyState title="Ready for eligibility check" message="Complete the account setup and check eligibility."/>:eligibility.eligible?<><div class="mb-quote"><div><span>Decision</span><strong>Eligible</strong></div><div><span>Product</span><strong>{eligibility.productName}</strong></div><div><span>Status after submission</span><strong>Pending approval</strong></div></div><button class="mb-button mb-button-primary" disabled={Boolean(busy)} onClick={submit}>{busy==="submit"?"Submitting…":"Submit account request"}</button></>:<div class="mb-error"><strong>Not eligible for this product</strong><p>{eligibility.productName} · {eligibility.decisionCode.replaceAll("_"," ")}</p>{Boolean(eligibility.messages?.length)&&<ul class="mb-field-errors">{eligibility.messages!.map(message=><li key={message}>{message}</li>)}</ul>}<p>Choose another deposit product or update the customer profile information.</p></div>}</Panel></div></>;
}

export function FixedDepositsPage() {
  const id=customerId(); const remote=useRemote((signal)=>id?services.fixedDeposits.list(id,signal):Promise.resolve([]),[id]); if(remote.loading)return <Loading label="Loading fixed deposits"/>;if(remote.error)return <ErrorState error={remote.error} retry={remote.retry}/>;const fds=items(remote.data);
  return <><PageHeader title="Fixed deposits" description="Track principal, accrued interest and maturity." action={<button class="mb-button mb-button-primary" onClick={()=>navigate("/app/fixed-deposits/open")}>Book fixed deposit</button>}/><div class="mb-account-grid">{fds.map(fd=><article class="mb-account-card" key={fd.fixedDepositId}><div><span>{fd.productCode}</span><Status value={fd.status}/></div><strong><Money value={fd.principal} currency={fd.currency}/></strong><small>{fd.annualInterestRate}% · matures {date(fd.maturityDate)}</small><p>Expected maturity <Money value={fd.expectedMaturityAmount} currency={fd.currency}/></p></article>)}</div>{fds.length===0&&<EmptyState title="No fixed deposits" message="Get a live quote and book your first fixed deposit."/>}</>;
}

export function FixedDepositOpenPage() {
  const id=customerId();const [values,setValues]=useState<Record<string,string>>({productCode:new URLSearchParams(location.search).get("product")??"",principal:"100000",currency:"INR",tenureValue:"12",tenureUnit:"MONTH",interestPayoutFrequency:"AT_MATURITY",valueDate:new Date().toISOString().slice(0,10),fundingAccountId:"",payoutAccountId:"",servicingBranchId:"MUM-001"});const [quote,setQuote]=useState<Record<string,unknown>>();const [quoteFor,setQuoteFor]=useState<string>();const [receipt,setReceipt]=useState<Record<string,unknown>>();const [error,setError]=useState<unknown>();const [busy,setBusy]=useState(false);const commands=useIdempotencyKeyStore();
  const quoteInputs=(current:Record<string,string>)=>({productCode:current.productCode,principal:Number(current.principal),currency:current.currency,tenureValue:Number(current.tenureValue),tenureUnit:current.tenureUnit,interestPayoutFrequency:current.interestPayoutFrequency,valueDate:current.valueDate});
  const set=(k:string)=>(v:string)=>{setValues(o=>({...o,[k]:v}));setQuote(undefined);setQuoteFor(undefined);setError(undefined);};
  const quoteNow=async(e:Event)=>{e.preventDefault();if(!id)return;setBusy(true);setError(undefined);try{const inputs=quoteInputs(values);const p=await services.products.one(values.productCode);const result=await services.fixedDeposits.quote({customerId:id,...inputs,productVersion:p.version}) as Record<string,unknown>;setQuote(result);setQuoteFor(JSON.stringify(inputs));}catch(reason){setError(reason);setQuote(undefined);setQuoteFor(undefined);}finally{setBusy(false);}};
  const book=async()=>{const inputs=quoteInputs(values);if(!id||!quote||quoteFor!==JSON.stringify(inputs)){setQuote(undefined);setQuoteFor(undefined);setError(new Error("The deposit details changed. Get a fresh quote before booking."));return;}setBusy(true);setError(undefined);try{const p=await services.products.one(values.productCode);const command={customerIds:[id],primaryCustomerId:id,productCode:values.productCode,productVersion:p.version,principal:Number(values.principal),currency:values.currency,tenureValue:Number(values.tenureValue),tenureUnit:values.tenureUnit,interestPayoutFrequency:values.interestPayoutFrequency,fundingAccountId:values.fundingAccountId,payoutAccountId:values.payoutAccountId,servicingBranchId:values.servicingBranchId,nominees:[],channel:"WEB"};const key=commands.keyFor(command);setReceipt(await services.fixedDeposits.book({...command,externalReference:key},key) as Record<string,unknown>);commands.reset();}catch(reason){setError(reason);}finally{setBusy(false);}};
  if(receipt)return <Receipt title="Fixed deposit booked" reference={String(receipt.fixedDepositId??"Created")} done={()=>navigate("/app/fixed-deposits")}/>;
  return <><PageHeader title="Book a fixed deposit" description="Get an authoritative quote before confirming."/><Panel><form class="mb-form" onSubmit={quoteNow}>{error&&<ErrorState error={error}/>}<div class="mb-form-grid"><Field label="Product code" name="productCode" value={values.productCode} required onInput={set("productCode")}/><Field label="Principal" name="principal" type="number" value={values.principal} min="0.01" step="0.01" required onInput={set("principal")}/><Field label="Tenure" name="tenureValue" type="number" value={values.tenureValue} min="1" required onInput={set("tenureValue")}/><Field label="Value date" name="valueDate" type="date" value={values.valueDate} required onInput={set("valueDate")}/><Field label="Funding account ID" name="fundingAccountId" value={values.fundingAccountId} required onInput={set("fundingAccountId")}/><Field label="Payout account ID" name="payoutAccountId" value={values.payoutAccountId} required onInput={set("payoutAccountId")}/></div><button class="mb-button mb-button-secondary" disabled={busy}>{busy?"Working…":"Get quote"}</button>{quote&&<div class="mb-quote"><div><span>Annual rate</span><strong>{String(quote.annualInterestRate??"—")}%</strong></div><div><span>Expected interest</span><strong><Money value={quote.expectedInterest as number}/></strong></div><div><span>Maturity amount</span><strong><Money value={quote.expectedMaturityAmount as number}/></strong></div><button type="button" class="mb-button mb-button-primary" disabled={busy} onClick={book}>{busy?"Booking…":"Confirm and book"}</button></div>}</form></Panel></>;
}

export function CardsPage() {
  const id=customerId();const remote=useRemote(async(signal)=>id?Promise.all([services.cards.accounts(id,signal),services.cards.applications(id,signal)]):[[],[]] as [CardAccount[],CardApplication[]],[id]);if(remote.loading)return <Loading label="Loading cards"/>;if(remote.error)return <ErrorState error={remote.error} retry={remote.retry}/>;const [cards,apps]=remote.data!;
  return <><PageHeader title="Cards & bills" description="Credit, applications and outstanding amounts." action={<div class="mb-row-actions"><button class="mb-button mb-button-secondary" onClick={()=>navigate("/app/bills")}>View bills</button><button class="mb-button mb-button-primary" onClick={()=>navigate("/app/cards/apply")}>Apply for a card</button></div>}/>{cards.length>0?<div class="mb-account-grid">{cards.map(card=><article class="mb-credit-card" key={card.accountId}><div><span>MoneyBags</span><Status value={card.status}/></div><strong>{card.cardNumber?.replace(/.(?=.{4})/g,"•")}</strong><small>{card.productCode}</small><footer><span>Available <Money value={card.availableLimit}/></span><span>Outstanding <Money value={card.outstandingAmount}/></span></footer></article>)}</div>:<Panel><EmptyState title="No credit cards" message="Your active MoneyBags credit cards will appear here."/></Panel>}<Panel title="Applications">{apps.length>0?<div class="mb-table-wrap"><table class="mb-table"><thead><tr><th>Application</th><th>Product</th><th>Requested limit</th><th>Status</th><th>Submitted</th></tr></thead><tbody>{apps.map(app=><tr key={app.applicationId}><td>{app.applicationId}</td><td>{app.productCode}</td><td><Money value={app.requestedCreditLimit}/></td><td><Status value={app.applicationStatus}/></td><td>{date(app.submittedAt)}</td></tr>)}</tbody></table></div>:<EmptyState title="No card applications" message="Explore available credit-card products to apply."/>}</Panel></>;
}

export function CardApplyPage(){
  const id=customerId();
  const requestedProduct=new URLSearchParams(location.search).get("product")??"";
  const productsRemote=useRemote((signal)=>services.products.active(signal),[]);
  const [productCode,setProductCode]=useState(requestedProduct);
  const [limit,setLimit]=useState("");
  const [receipt,setReceipt]=useState<CardApplication>();
  const [error,setError]=useState<unknown>();
  const [busy,setBusy]=useState(false);
  const commands=useIdempotencyKeyStore();
  const products=items<Product>(productsRemote.data).filter(product=>product.category==="CREDIT_CARD");
  const selected=products.find(product=>product.productCode===productCode);

  useEffect(()=>{
    if(!products.length)return;
    const initial=products.find(product=>product.productCode===requestedProduct)??products[0];
    setProductCode(initial.productCode);
    setLimit(String(initial.creditCardRule?.minimumCreditLimit??50000));
  },[productsRemote.data]);

  const chooseProduct=(code:string)=>{
    setProductCode(code);
    const product=products.find(value=>value.productCode===code);
    setLimit(String(product?.creditCardRule?.minimumCreditLimit??50000));
  };
  const submit=async(e:Event)=>{e.preventDefault();if(!id||!productCode)return;setBusy(true);setError(undefined);try{const body={cifId:Number(id),productCode,requestedCreditLimit:Number(limit)};setReceipt(await services.cards.apply(body,commands.keyFor(body)));commands.reset();}catch(reason){setError(reason);}finally{setBusy(false);}};
  if(productsRemote.loading)return <Loading label="Loading credit-card products"/>;
  if(productsRemote.error)return <ErrorState error={productsRemote.error} retry={productsRemote.retry}/>;
  if(!products.length)return <><PageHeader title="Apply for a credit card" description="Choose an active credit-card product."/><Panel><EmptyState title="No credit-card products available" message="Product Master has not published an active credit-card product yet."/></Panel></>;
  if(receipt)return <Receipt title="Card application submitted for review" reference={String(receipt.applicationId)} done={()=>navigate("/app/cards")}><Status value={receipt.applicationStatus}/></Receipt>;
  return <><PageHeader title="Apply for a credit card" description="Choose an active product and request a limit within its allowed range."/><Panel><form class="mb-form" onSubmit={submit}>{error&&<ErrorState error={error}/>}<label class="mb-field"><span>Credit-card product *</span><select required value={productCode} onChange={event=>chooseProduct((event.currentTarget as HTMLSelectElement).value)}>{products.map(product=><option value={product.productCode}>{product.productName} · {product.productCode}</option>)}</select></label><Field label="Requested credit limit" name="limit" type="number" value={limit} min={String(selected?.creditCardRule?.minimumCreditLimit??0.01)} max={selected?.creditCardRule?.maximumCreditLimit==null?undefined:String(selected.creditCardRule.maximumCreditLimit)} step="0.01" required hint={selected?.creditCardRule?`Allowed range: ₹${selected.creditCardRule.minimumCreditLimit?.toLocaleString("en-IN")} – ₹${selected.creditCardRule.maximumCreditLimit?.toLocaleString("en-IN")}`:undefined} onInput={setLimit}/><button class="mb-button mb-button-primary" disabled={busy||!productCode}>{busy?"Submitting…":"Submit for review"}</button></form></Panel></>;
}

export function PaymentsPage(){
  const id=customerId();
  const remote=useRemote(signal=>id?services.payments.list(id,signal):Promise.resolve({content:[],page:0,size:0,totalElements:0}),[id]);
  if(remote.loading)return <Loading label="Loading payments"/>;
  if(remote.error)return <ErrorState error={remote.error} retry={remote.retry}/>;
  return <><PageHeader title="Payments" description="Track transfers, card purchases, bill repayments and deposit funding." action={<div class="mb-row-actions"><button onClick={()=>navigate("/app/payments/merchant")}>Card purchase</button><button class="mb-button mb-button-primary" onClick={()=>navigate("/app/payments/transfer")}>Transfer money</button></div>}/><Panel><PaymentRows payments={items(remote.data)}/></Panel></>;
}

function PaymentRows({payments}:{payments:Payment[]}){
  if(!payments.length)return <EmptyState title="No payments yet" message="Your completed and processing payments will appear here."/>;
  return <div class="mb-table-wrap"><table class="mb-table"><thead><tr><th>Payment</th><th>Type</th><th>Status</th><th>Date</th><th>Amount</th><th>Action</th></tr></thead><tbody>{payments.map(payment=><tr key={payment.paymentId}><td><strong>{payment.reference??payment.paymentId}</strong><small>{payment.paymentId}</small></td><td>{payment.paymentType?.replaceAll("_"," ")}</td><td><Status value={payment.status}/></td><td>{date(payment.createdAt)}</td><td><Money value={payment.amount} currency={payment.currencyCode}/></td><td><button class="mb-text-button" onClick={()=>navigate(`/app/payments/${payment.paymentId}`)}>View details</button></td></tr>)}</tbody></table></div>;
}

export function PaymentDetailPage({paymentId}:{paymentId:string}){
  const[revision,setRevision]=useState(0);const[error,setError]=useState<unknown>();const[busy,setBusy]=useState(false);const commands=useIdempotencyKeyStore();
  const remote=useRemote(signal=>Promise.all([services.payments.one(paymentId,signal),services.payments.history(paymentId,signal)]),[paymentId,revision]);
  if(remote.loading)return <Loading label="Loading payment details"/>;
  if(remote.error)return <ErrorState error={remote.error} retry={remote.retry}/>;
  const[payment,history]=remote.data!;
  const cancellable=!payment.accountingJournalNumber&&!['SETTLED','FAILED','CANCELLED','REVERSED'].includes(payment.status);
  const cancel=async()=>{if(!window.confirm(`Cancel payment ${payment.paymentId}? Any active reservation will be released.`))return;setBusy(true);setError(undefined);try{await services.payments.cancel(payment.paymentId,commands.keyFor({paymentId:payment.paymentId,action:'cancel'}));commands.reset();setRevision(value=>value+1);}catch(reason){setError(reason);}finally{setBusy(false);}};
  return <><PageHeader title="Payment details" description={payment.paymentId} action={cancellable?<button class="mb-button mb-button-secondary" disabled={busy} onClick={cancel}>{busy?"Cancelling…":"Cancel payment"}</button>:undefined}/>{error&&<ErrorState error={error}/>}<div class="mb-summary-row"><article><span>Amount</span><strong><Money value={payment.amount} currency={payment.currencyCode}/></strong><small>{payment.paymentType.replaceAll('_',' ')}</small></article><article><span>Status</span><strong><Status value={payment.status}/></strong><small>Business date {date(payment.businessDate)}</small></article><article><span>Reference</span><strong>{payment.reference??'—'}</strong><small>{payment.accountingJournalNumber??'Not posted to Accounting yet'}</small></article></div><div class="mb-detail-grid"><Panel title="Payment route"><dl class="mb-detail-list"><dt>Source</dt><dd>{payment.sourceAccountId??'—'}</dd><dt>Destination</dt><dd>{payment.destinationAccountId??payment.merchantId??'—'}</dd><dt>Bill</dt><dd>{payment.billId??'—'}</dd><dt>Fixed deposit</dt><dd>{payment.fixedDepositId??'—'}</dd></dl></Panel><Panel title="Settlement references"><dl class="mb-detail-list"><dt>Deposit reservation</dt><dd>{payment.depositReservationId??'—'}</dd><dt>Card hold</dt><dd>{payment.cardHoldId??'—'}</dd><dt>Accounting journal</dt><dd>{payment.accountingJournalNumber??'—'}</dd><dt>Correlation ID</dt><dd>{payment.correlationId??'—'}</dd></dl></Panel></div><Panel title="Status timeline">{history.length?<div class="mb-table-wrap"><table class="mb-table"><thead><tr><th>Status</th><th>Reason</th><th>Time</th><th>Correlation</th></tr></thead><tbody>{history.map((entry,index)=><tr key={`${entry.changedAt}-${index}`}><td><Status value={entry.toStatus}/></td><td>{entry.reasonMessage??entry.reasonCode??'—'}</td><td>{date(entry.changedAt)}</td><td>{entry.correlationId}</td></tr>)}</tbody></table></div>:<EmptyState title="No status history" message="No lifecycle entries are available for this payment."/>}</Panel></>;
}

export function TransferPage(){
  const id=customerId();const accounts=useRemote(signal=>id?services.accounts.list(id,signal):Promise.resolve({content:[],page:0,size:0,totalElements:0}),[id]);
  const[values,setValues]=useState<Record<string,string>>({sourceAccountId:"",targetAccountId:"",amount:"",currencyCode:"INR",reference:""});const[reviewing,setReviewing]=useState(false);const[receipt,setReceipt]=useState<Payment>();const[error,setError]=useState<unknown>();const[busy,setBusy]=useState(false);const commands=useIdempotencyKeyStore();const set=(key:string)=>(value:string)=>{setValues(current=>({...current,[key]:value}));setReviewing(false);};
  const submit=async(event:Event)=>{event.preventDefault();if(!id)return;if(!reviewing){setReviewing(true);return;}setBusy(true);setError(undefined);try{const body={requestorCustomerId:Number(id),...values,amount:Number(values.amount)};setReceipt(await services.payments.transfer(body,commands.keyFor(body)));commands.reset();}catch(reason){setError(reason);}finally{setBusy(false);}};
  if(accounts.loading)return <Loading label="Loading your accounts"/>;if(accounts.error)return <ErrorState error={accounts.error} retry={accounts.retry}/>;
  if(receipt)return <Receipt title={receipt.status==="SETTLED"?"Transfer complete":"Transfer submitted"} reference={receipt.paymentId} done={()=>navigate(`/app/payments/${receipt.paymentId}`)}><Money value={receipt.amount} currency={receipt.currencyCode}/><Status value={receipt.status}/></Receipt>;
  const active=items<Account>(accounts.data).filter(account=>account.status==='ACTIVE'&&account.productSubtype!=='FIXED_DEPOSIT');
  if(active.length<2)return <><PageHeader title="Transfer money" description="Move money securely between deposit accounts."/><Panel><EmptyState title="Two active accounts are required" message="Open or activate another Savings/Current account before making a book transfer."/></Panel></>;
  return <><PageHeader title="Transfer money" description="Choose the source and destination, then review before submitting."/><Panel><form class="mb-form" onSubmit={submit}>{error&&<ErrorState error={error}/>}<div class="mb-form-grid"><SelectField label="From account" value={values.sourceAccountId} onChange={set('sourceAccountId')}><option value="">Choose source account</option>{active.map(account=><option value={account.accountId}>{account.maskedAccountNumber} · {account.productName} · ₹{Number(account.availableBalance).toLocaleString('en-IN')}</option>)}</SelectField><SelectField label="To account" value={values.targetAccountId} onChange={set('targetAccountId')}><option value="">Choose destination account</option>{active.filter(account=>account.accountId!==values.sourceAccountId).map(account=><option value={account.accountId}>{account.maskedAccountNumber} · {account.productName}</option>)}</SelectField><Field label="Amount" name="amount" type="number" value={values.amount} min="0.01" step="0.01" required onInput={set('amount')}/><Field label="Currency" name="currency" value="INR" required onInput={set('currencyCode')}/></div><Field label="Reference" name="reference" value={values.reference} onInput={set('reference')}/>{reviewing&&<div class="mb-quote"><div><span>From</span><strong>{active.find(account=>account.accountId===values.sourceAccountId)?.maskedAccountNumber}</strong></div><div><span>To</span><strong>{active.find(account=>account.accountId===values.targetAccountId)?.maskedAccountNumber}</strong></div><div><span>Amount</span><strong><Money value={Number(values.amount)} currency={values.currencyCode}/></strong></div></div>}<button class="mb-button mb-button-primary" disabled={busy||!values.sourceAccountId||!values.targetAccountId||values.sourceAccountId===values.targetAccountId}>{busy?"Submitting securely…":reviewing?"Confirm transfer":"Review transfer"}</button></form></Panel></>;
}

export function MerchantPaymentPage(){
  const id=customerId();const cards=useRemote(signal=>id?services.cards.accounts(id,signal):Promise.resolve([]),[id]);const[values,setValues]=useState({cardId:"",merchantId:"",amount:"",reference:""});const[reviewing,setReviewing]=useState(false);const[receipt,setReceipt]=useState<Payment>();const[error,setError]=useState<unknown>();const[busy,setBusy]=useState(false);const commands=useIdempotencyKeyStore();const set=(key:keyof typeof values)=>(value:string)=>{setValues(current=>({...current,[key]:value}));setReviewing(false);};
  const submit=async(event:Event)=>{event.preventDefault();if(!id)return;if(!reviewing){setReviewing(true);return;}setBusy(true);setError(undefined);try{const body={requestorCustomerId:Number(id),creditCardAccountId:`CC-${values.cardId}`,merchantId:values.merchantId.trim(),amount:Number(values.amount),currencyCode:'INR',reference:values.reference||`Purchase at ${values.merchantId}`};setReceipt(await services.payments.merchant(body,commands.keyFor(body)));commands.reset();}catch(reason){setError(reason);}finally{setBusy(false);}};
  if(cards.loading)return <Loading label="Loading your cards"/>;if(cards.error)return <ErrorState error={cards.error} retry={cards.retry}/>;if(receipt)return <Receipt title="Card payment submitted" reference={receipt.paymentId} done={()=>navigate(`/app/payments/${receipt.paymentId}`)}><Money value={receipt.amount}/><Status value={receipt.status}/></Receipt>;
  const active=(cards.data??[]).filter(card=>card.status==='ACTIVE');if(!active.length)return <><PageHeader title="Pay a merchant" description="Use an active MoneyBags credit card."/><Panel><EmptyState title="No active card available" message="An approved, active credit-card account is required for a merchant payment."/></Panel></>;
  return <><PageHeader title="Pay a merchant" description="Reserve the card limit, post the merchant payable and capture the purchase."/><Panel><form class="mb-form" onSubmit={submit}>{error&&<ErrorState error={error}/>}<SelectField label="Credit card" value={values.cardId} onChange={set('cardId')}><option value="">Choose a card</option>{active.map(card=><option value={String(card.accountId)}>•••• {card.cardNumber.slice(-4)} · available ₹{Number(card.availableLimit).toLocaleString('en-IN')}</option>)}</SelectField><div class="mb-form-grid"><Field label="Merchant ID" name="merchant" value={values.merchantId} required onInput={set('merchantId')}/><Field label="Amount" name="amount" type="number" value={values.amount} min="0.01" step="0.01" required onInput={set('amount')}/></div><Field label="Reference" name="reference" value={values.reference} onInput={set('reference')}/>{reviewing&&<div class="mb-quote"><div><span>Card</span><strong>CC-{values.cardId}</strong></div><div><span>Merchant</span><strong>{values.merchantId}</strong></div><div><span>Amount</span><strong><Money value={Number(values.amount)}/></strong></div></div>}<button class="mb-button mb-button-primary" disabled={busy||!values.cardId||!values.merchantId||!values.amount}>{busy?"Submitting securely…":reviewing?"Confirm card payment":"Review payment"}</button></form></Panel></>;
}

export function BillsPage(){const remote=useRemote((signal)=>services.bills.list(undefined,signal),[]);if(remote.loading)return <Loading label="Loading bills"/>;if(remote.error)return <ErrorState error={remote.error} retry={remote.retry}/>;const bills=items(remote.data);return <><PageHeader title="Bills" description="Credit-card statements and amounts due."/><div class="mb-account-grid">{bills.map(bill=><article class="mb-bill-card" key={bill.billId}><div><span>{bill.billingPeriod}</span><Status value={bill.status}/></div><strong><Money value={bill.outstandingAmount} currency={bill.currency}/></strong><small>Due {date(bill.paymentDueDate)} · minimum <Money value={bill.minimumAmountDue} currency={bill.currency}/></small><button class="mb-button mb-button-primary" disabled={Number(bill.outstandingAmount)<=0} onClick={()=>navigate(`/app/bills/${bill.billId}/pay`)}>Pay bill</button></article>)}</div>{!bills.length&&<EmptyState title="No bills available" message="Generated card bills will appear here."/>}</>}

export function BillPayPage({billId}:{billId:string}){
  const id=customerId();const remote=useRemote(async signal=>{const[bill,accounts]=await Promise.all([services.bills.one(billId,signal),id?services.accounts.list(id,signal):Promise.resolve({content:[],page:0,size:0,totalElements:0})]);return{bill,accounts};},[billId,id]);const[source,setSource]=useState('');const[amount,setAmount]=useState('');const[reviewing,setReviewing]=useState(false);const[receipt,setReceipt]=useState<Payment>();const[error,setError]=useState<unknown>();const[busy,setBusy]=useState(false);const commands=useIdempotencyKeyStore();
  if(remote.loading)return <Loading label="Loading bill"/>;if(remote.error)return <ErrorState error={remote.error} retry={remote.retry}/>;const{bill,accounts}=remote.data!;const funding=items<Account>(accounts).filter(account=>account.status==='ACTIVE'&&account.productSubtype!=='FIXED_DEPOSIT');const selectedAmount=Number(amount||bill.outstandingAmount);
  const pay=async(event:Event)=>{event.preventDefault();if(!id)return;if(!reviewing){setAmount(String(selectedAmount));setReviewing(true);return;}setBusy(true);setError(undefined);try{const body={requestorCustomerId:Number(id),billId:bill.billId,sourceDepositAccountId:source,creditCardAccountId:bill.accountId.startsWith('CC-')?bill.accountId:`CC-${bill.accountId}`,amount:selectedAmount,currencyCode:bill.currency,reference:`Bill ${bill.billingPeriod} repayment`};setReceipt(await services.payments.repay(body,commands.keyFor(body)));commands.reset();}catch(reason){setError(reason);}finally{setBusy(false);}};
  if(receipt)return <Receipt title="Bill payment submitted" reference={receipt.paymentId} done={()=>navigate(`/app/payments/${receipt.paymentId}`)}><Money value={receipt.amount} currency={receipt.currencyCode}/><Status value={receipt.status}/></Receipt>;
  return <><PageHeader title="Pay card bill" description={`${bill.billingPeriod} · due ${date(bill.paymentDueDate)}`}/><Panel>{error&&<ErrorState error={error}/>}<div class="mb-payment-summary"><span>Outstanding</span><strong><Money value={bill.outstandingAmount} currency={bill.currency}/></strong><small>Minimum <Money value={bill.minimumAmountDue} currency={bill.currency}/></small></div><form class="mb-form" onSubmit={pay}><SelectField label="Funding deposit account" value={source} onChange={value=>{setSource(value);setReviewing(false);}}><option value="">Choose an active account</option>{funding.map(account=><option value={account.accountId}>{account.maskedAccountNumber} · available ₹{Number(account.availableBalance).toLocaleString('en-IN')}</option>)}</SelectField><div class="mb-row-actions"><button type="button" onClick={()=>{setAmount(String(bill.minimumAmountDue));setReviewing(false);}}>Minimum due</button><button type="button" onClick={()=>{setAmount(String(bill.outstandingAmount));setReviewing(false);}}>Full outstanding</button></div><Field label="Payment amount" name="amount" type="number" value={amount||String(bill.outstandingAmount)} min="0.01" max={String(bill.outstandingAmount)} step="0.01" required onInput={value=>{setAmount(value);setReviewing(false);}}/>{reviewing&&<div class="mb-quote"><div><span>Bill</span><strong>{bill.billId}</strong></div><div><span>Funding account</span><strong>{funding.find(account=>account.accountId===source)?.maskedAccountNumber}</strong></div><div><span>Amount</span><strong><Money value={selectedAmount} currency={bill.currency}/></strong></div></div>}<button class="mb-button mb-button-primary" disabled={busy||!source||selectedAmount<=0||selectedAmount>bill.outstandingAmount}>{busy?"Submitting…":reviewing?"Confirm repayment":"Review repayment"}</button></form></Panel></>;
}

export function NotificationsPage(){const id=customerId();const remote=useRemote((signal)=>id?services.notifications.list(id,signal):Promise.resolve({content:[],page:0,size:0,totalElements:0}),[id]);if(remote.loading)return <Loading label="Loading updates"/>;if(remote.error)return <ErrorState error={remote.error} retry={remote.retry}/>;const notices=items(remote.data);return <><PageHeader title="Updates" description="Important activity from across MoneyBags."/><Panel>{notices.map((notice:Notification)=><article class="mb-notification" key={notice.notificationId}><span class="mb-notification-mark"><Icon name="notifications"/></span><div><div><strong>{notice.emailSubject}</strong><Status value={notice.status}/></div><p>{notice.emailBody}</p><small>{date(notice.createdAt)} · {notice.notificationType.replaceAll("_"," ")}</small></div></article>)}{!notices.length&&<EmptyState title="No updates" message="Account and payment notifications will appear here."/>}</Panel></>}

export function StatementsPage(){return <><PageHeader title="Statements" description="Download account and card activity by period."/><Panel><div class="mb-warning-banner">Statement Service is not present in this repository yet. This page is ready for its public API and remains unavailable until that service is implemented.</div><EmptyState title="Statements are not available yet" message="Authoritative statements cannot be assembled safely in the browser from Payments data alone."/></Panel></>}

export function AccountDetailPage({accountId}:{accountId:string}){const remote=useRemote((signal)=>services.accounts.one(accountId,signal),[accountId]);if(remote.loading)return <Loading label="Loading account"/>;if(remote.error)return <ErrorState error={remote.error} retry={remote.retry}/>;return <><PageHeader title="Account details" description={accountId}/><Panel><JsonDetails value={remote.data}/></Panel></>}

export function JsonDetails({value}:{value:unknown}){const entries=useMemo(()=>Object.entries((value??{}) as Record<string,unknown>).filter(([,v])=>v===null||["string","number","boolean"].includes(typeof v)),[value]);if(!entries.length)return <EmptyState title="Nothing to show" message="No account details are currently available."/>;return <dl class="mb-detail-list">{entries.flatMap(([key,v])=>[<dt key={`${key}-label`}>{key.replace(/([A-Z])/g," $1")}</dt>,<dd key={`${key}-value`}>{String(v??"—")}</dd>])}</dl>}
