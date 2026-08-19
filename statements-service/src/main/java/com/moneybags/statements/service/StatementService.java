package com.moneybags.statements.service;

import com.moneybags.statements.api.StatementDtos.AccountActivityView;
import com.moneybags.statements.api.StatementDtos.GenerateAccountStatementRequest;
import com.moneybags.statements.api.StatementDtos.GenerateStatementRequest;
import com.moneybags.statements.api.StatementDtos.StatementLineView;
import com.moneybags.statements.api.StatementDtos.StatementView;
import com.moneybags.statements.entity.AccountStatement;
import com.moneybags.statements.entity.AccountStatementLine;
import com.moneybags.statements.exception.StatementException;
import com.moneybags.statements.integration.StatementSourceGateway;
import com.moneybags.statements.integration.StatementSourceGateway.AccountContext;
import com.moneybags.statements.integration.StatementSourceGateway.DepositActivity;
import com.moneybags.statements.integration.StatementSourceGateway.LedgerEntry;
import com.moneybags.statements.repository.AccountStatementLineRepository;
import com.moneybags.statements.repository.AccountStatementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementService {
    private static final int MAX_PERIOD_DAYS = 366;

    private final AccountStatementRepository statements;
    private final AccountStatementLineRepository lines;
    private final StatementSourceGateway source;
    private final boolean requireAccountingReconciliation;

    public StatementService(AccountStatementRepository statements,
                            AccountStatementLineRepository lines,
                            StatementSourceGateway source,
                            @Value("${moneybags.statements.require-accounting-reconciliation:false}")
                            boolean requireAccountingReconciliation) {
        this.statements = statements;
        this.lines = lines;
        this.source = source;
        this.requireAccountingReconciliation = requireAccountingReconciliation;
    }

    @Transactional(readOnly = true)
    public AccountActivityView activity(String accountReference, LocalDate start, LocalDate end,
                                        String customerId, boolean privileged) {
        validatePeriod(start, end);
        AccountContext context = source.context(accountReference);
        authorize(context, customerId, privileged);
        return aggregate(context, start, end);
    }

    @Transactional
    public StatementView generateForAccount(GenerateAccountStatementRequest request,
                                            String customerId, boolean privileged) {
        AccountActivityView activity = activity(request.accountReference(), request.periodStart(),
                request.periodEnd(), customerId, privileged);
        if (!activity.reconciled()) {
            throw new StatementException(HttpStatus.CONFLICT, "STATEMENT_NOT_RECONCILED",
                    reconciliationFailureMessage());
        }
        String owner = privileged ? owner(source.context(request.accountReference()), customerId) : customerId;
        return persist(activity, owner);
    }

    @Transactional
    public StatementView generate(GenerateStatementRequest request) {
        validatePeriod(request.periodStart(), request.periodEnd());
        AccountContext context = source.context(request.accountReference());
        AccountActivityView activity = aggregate(context, request.periodStart(), request.periodEnd());
        if (!activity.reconciled()) {
            throw new StatementException(HttpStatus.CONFLICT, "STATEMENT_NOT_RECONCILED",
                    reconciliationFailureMessage());
        }
        return persist(activity, request.cifId());
    }

    private StatementView persist(AccountActivityView activity, String customerId) {
        Optional<AccountStatement> existing = statements
                .findByAccountReferenceAndAccountTypeAndPeriodStartAndPeriodEnd(
                        activity.accountReference(), activity.accountType(),
                        activity.periodStart(), activity.periodEnd());
        if (existing.isPresent()) {
            AccountStatement statement = existing.get();
            List<StatementLineView> statementLines = statementLines(statement);
            upgradeLegacyDocument(statement, statementLines);
            return view(statement, statementLines);
        }

        String id = UUID.randomUUID().toString();
        OffsetDateTime generated = OffsetDateTime.now();
        byte[] pdf = StatementPdfRenderer.render(new StatementPdfModel(id,
                activity.maskedAccountReference(), activity.accountType(),
                activity.periodStart(), activity.periodEnd(), generated, activity.currency(),
                activity.openingBalance(), activity.totalCredits(), activity.totalDebits(),
                activity.closingBalance(), activity.lines()));
        AccountStatement statement = statements.save(new AccountStatement(id, customerId,
                activity.accountReference(), activity.accountType(), activity.maskedAccountReference(),
                activity.periodStart(), activity.periodEnd(), activity.currency(),
                activity.openingBalance(), activity.closingBalance(), generated, hash(pdf), pdf));
        for (StatementLineView line : activity.lines()) {
            lines.save(new AccountStatementLine(UUID.randomUUID().toString(), statement,
                    line.sequence(), line.transactionId(), line.paymentId(), line.occurredAt(),
                    line.description(), line.debit(), line.credit(), line.balanceAfter(),
                    line.journalNumber()));
        }
        return view(statement, activity.lines());
    }

    private AccountActivityView aggregate(AccountContext context, LocalDate start, LocalDate end) {
        var data = source.load(context.accountId(), start, end);
        List<LedgerEntry> unmatchedLedger = new ArrayList<>(data.ledgerEntries());
        List<DepositActivity> activities = data.depositActivities().stream()
                .sorted(Comparator.comparing(DepositActivity::createdAt)
                        .thenComparing(DepositActivity::transactionId))
                .toList();
        List<StatementLineView> result = new ArrayList<>();
        BigDecimal totalDebits = zero();
        BigDecimal totalCredits = zero();
        boolean everyLineMatched = true;

        for (DepositActivity value : activities) {
            boolean debitDirection = "DEBIT".equals(value.direction());
            BigDecimal debit = debitDirection ? money(value.amount()) : zero();
            BigDecimal credit = debitDirection ? zero() : money(value.amount());
            LedgerEntry ledger = takeMatch(unmatchedLedger, debit, credit, value.currency());
            if (ledger == null) everyLineMatched = false;
            totalDebits = totalDebits.add(debit);
            totalCredits = totalCredits.add(credit);
            result.add(new StatementLineView(result.size() + 1, value.transactionId(),
                    value.paymentId(), value.createdAt(),
                    ledger == null ? fallbackDescription(value) : ledger.narration(),
                    debit, credit, money(value.balanceAfter()),
                    ledger == null ? null : ledger.journalNumber()));
        }

        BigDecimal opening = money(data.openingBalance());
        BigDecimal closing = money(data.closingBalance());
        BigDecimal projected = opening.subtract(totalDebits).add(totalCredits)
                .setScale(4, RoundingMode.HALF_EVEN);
        boolean currencyMatches = context.currency().trim().equals(data.currency().trim())
                && activities.stream().allMatch(value -> context.currency().trim()
                        .equals(value.currency().trim()));
        boolean depositBalancesMatch = projected.compareTo(closing) == 0 && currencyMatches;
        boolean accountingMatches = (activities.isEmpty() && data.ledgerEntries().isEmpty())
                || (everyLineMatched && unmatchedLedger.isEmpty());
        boolean reconciled = depositBalancesMatch
                && (!requireAccountingReconciliation || accountingMatches);

        return new AccountActivityView(context.accountId(), context.accountType(),
                context.maskedAccountReference(), start, end, context.currency().trim(),
                opening, closing, money(totalDebits), money(totalCredits), reconciled,
                List.copyOf(result));
    }

    private LedgerEntry takeMatch(List<LedgerEntry> ledger, BigDecimal debit,
                                  BigDecimal credit, String currency) {
        for (int index = 0; index < ledger.size(); index++) {
            LedgerEntry candidate = ledger.get(index);
            if (money(candidate.debitAmount()).compareTo(debit) == 0
                    && money(candidate.creditAmount()).compareTo(credit) == 0
                    && candidate.currencyCode().trim().equals(currency.trim())) {
                return ledger.remove(index);
            }
        }
        return null;
    }

    private String fallbackDescription(DepositActivity activity) {
        return ("DEBIT".equals(activity.direction()) ? "Account debit" : "Account credit")
                + " · " + activity.paymentId();
    }

    private String reconciliationFailureMessage() {
        return requireAccountingReconciliation
                ? "An official statement cannot be generated until Deposit and Accounting reconcile"
                : "An official statement cannot be generated because Deposit activity balances are inconsistent";
    }

    private void validatePeriod(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new StatementException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD",
                    "periodEnd must not be before periodStart");
        }
        if (start.plusDays(MAX_PERIOD_DAYS).isBefore(end)) {
            throw new StatementException(HttpStatus.BAD_REQUEST, "PERIOD_TOO_LARGE",
                    "Statement periods cannot exceed 366 days");
        }
        if (end.isAfter(LocalDate.now())) {
            throw new StatementException(HttpStatus.BAD_REQUEST, "FUTURE_PERIOD",
                    "Statement periods cannot end in the future");
        }
    }

    private void authorize(AccountContext context, String customerId, boolean privileged) {
        if (!privileged && (customerId == null || !context.customerIds().contains(customerId))) {
            throw new StatementException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND",
                    "Account was not found");
        }
    }

    private String owner(AccountContext context, String requested) {
        if (requested != null && context.customerIds().contains(requested)) return requested;
        if (!context.customerIds().isEmpty()) return context.customerIds().getFirst();
        throw new StatementException(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_OWNER_NOT_FOUND",
                "The account has no active holder");
    }

    @Transactional(readOnly = true)
    public StatementView get(String id, String customerId, boolean privileged) {
        AccountStatement statement = load(id);
        authorize(statement, customerId, privileged);
        return view(statement);
    }

    @Transactional
    public AccountStatement document(String id, String customerId, boolean privileged) {
        AccountStatement statement = load(id);
        authorize(statement, customerId, privileged);
        upgradeLegacyDocument(statement, statementLines(statement));
        return statement;
    }

    private StatementView view(AccountStatement value) {
        return view(value, statementLines(value));
    }

    private List<StatementLineView> statementLines(AccountStatement value) {
        return lines.findByStatementIdOrderBySequenceAsc(value.getId()).stream()
                .map(line -> new StatementLineView(line.getSequence(), line.getTransactionId(),
                        line.getPaymentId(), line.getOccurredAt(), line.getDescription(),
                        line.getDebit(), line.getCredit(), line.getBalanceAfter(),
                        line.getJournalNumber())).toList();
    }

    private void upgradeLegacyDocument(AccountStatement statement,
                                       List<StatementLineView> statementLines) {
        if (StatementPdfRenderer.usesCurrentTemplate(statement.getDocumentData())) return;
        BigDecimal totalCredits = statementLines.stream().map(StatementLineView::credit)
                .map(StatementService::money).reduce(zero(), BigDecimal::add);
        BigDecimal totalDebits = statementLines.stream().map(StatementLineView::debit)
                .map(StatementService::money).reduce(zero(), BigDecimal::add);
        byte[] pdf = StatementPdfRenderer.render(new StatementPdfModel(statement.getId(),
                statement.getMaskedAccountReference(), statement.getAccountType(),
                statement.getPeriodStart(), statement.getPeriodEnd(), statement.getGeneratedAt(),
                statement.getCurrency().trim(), statement.getOpeningBalance(), totalCredits,
                totalDebits, statement.getClosingBalance(), statementLines));
        statement.replaceDocument(hash(pdf), pdf);
        statements.save(statement);
    }

    private void authorize(AccountStatement statement, String customerId, boolean privileged) {
        if (!privileged && (customerId == null || !customerId.equals(statement.getCifId()))) {
            throw new StatementException(HttpStatus.NOT_FOUND, "STATEMENT_NOT_FOUND",
                    "Statement was not found");
        }
    }

    private StatementView view(AccountStatement value, List<StatementLineView> statementLines) {
        return new StatementView(value.getId(), value.getAccountReference(), value.getAccountType(),
                value.getMaskedAccountReference(), value.getPeriodStart(), value.getPeriodEnd(),
                value.getCurrency(), value.getOpeningBalance(), value.getClosingBalance(),
                value.getGeneratedAt(), value.getStatus(), statementLines);
    }

    private AccountStatement load(String id) {
        return statements.findById(id).orElseThrow(() -> new StatementException(
                HttpStatus.NOT_FOUND, "STATEMENT_NOT_FOUND", "Statement was not found"));
    }

    private static BigDecimal zero() { return BigDecimal.ZERO.setScale(4); }
    private static BigDecimal money(BigDecimal value) {
        return value == null ? zero() : value.setScale(4, RoundingMode.HALF_EVEN);
    }

    private static String hash(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
