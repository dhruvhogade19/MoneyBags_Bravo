package com.moneybags.statements.service;

import com.moneybags.statements.api.StatementDtos.*;
import com.moneybags.statements.entity.*;
import com.moneybags.statements.exception.StatementException;
import com.moneybags.statements.integration.StatementSourceGateway;
import com.moneybags.statements.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class StatementService {
    private final AccountStatementRepository statements; private final AccountStatementLineRepository lines; private final StatementSourceGateway source;
    public StatementService(AccountStatementRepository statements, AccountStatementLineRepository lines, StatementSourceGateway source) { this.statements = statements; this.lines = lines; this.source = source; }

    @Transactional
    public StatementView generate(GenerateStatementRequest request) {
        if (request.periodEnd().isBefore(request.periodStart())) throw new StatementException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD", "periodEnd must not be before periodStart");
        Optional<AccountStatement> existing = statements.findByAccountReferenceAndAccountTypeAndPeriodStartAndPeriodEnd(request.accountReference(), request.accountType(), request.periodStart(), request.periodEnd());
        if (existing.isPresent()) return view(existing.get());
        var data = source.load(request.accountReference(), request.periodStart(), request.periodEnd());
        if (data.depositActivities().isEmpty() || data.ledgerEntries().isEmpty()) throw new StatementException(HttpStatus.UNPROCESSABLE_ENTITY, "STATEMENT_ACTIVITY_NOT_FOUND", "Accounting and final Deposit activity are both required");
        var first = data.depositActivities().getFirst(); var last = data.depositActivities().getLast(); BigDecimal opening = money(first.balanceBefore());
        List<StatementLineView> draft = new ArrayList<>(); BigDecimal balance = opening;
        for (var entry : data.ledgerEntries()) {
            if (!first.currency().equals(entry.currencyCode().trim())) throw new StatementException(HttpStatus.CONFLICT, "CURRENCY_MISMATCH", "Accounting and Deposit currencies do not match");
            BigDecimal debit = money(entry.debitAmount()), credit = money(entry.creditAmount()); balance = balance.subtract(debit).add(credit).setScale(4, RoundingMode.HALF_EVEN);
            draft.add(new StatementLineView(draft.size() + 1, entry.occurredAt(), entry.narration(), debit, credit, balance, entry.journalNumber()));
        }
        if (balance.compareTo(money(last.balanceAfter())) != 0) throw new StatementException(HttpStatus.CONFLICT, "BALANCE_PROJECTION_MISMATCH", "Accounting aggregation does not reconcile to Deposit balance projection");
        String id = UUID.randomUUID().toString(); OffsetDateTime generated = OffsetDateTime.now(); byte[] pdf = StatementPdfRenderer.render(id, request.maskedAccountReference(), request.periodStart(), request.periodEnd(), first.currency(), opening, balance, draft);
        AccountStatement statement = statements.save(new AccountStatement(id, request.cifId(), request.accountReference(), request.accountType(), request.maskedAccountReference(), request.periodStart(), request.periodEnd(), first.currency(), opening, balance, generated, hash(pdf), pdf));
        for (StatementLineView line : draft) lines.save(new AccountStatementLine(UUID.randomUUID().toString(), statement, line.sequence(), line.occurredAt(), line.description(), line.debit(), line.credit(), line.balanceAfter(), line.journalNumber()));
        return view(statement, draft);
    }
    @Transactional(readOnly = true) public StatementView get(String id, String cifId, boolean privileged) { AccountStatement statement = load(id); if (!privileged && (cifId == null || !cifId.equals(statement.getCifId()))) throw new StatementException(HttpStatus.NOT_FOUND, "STATEMENT_NOT_FOUND", "Statement was not found"); return view(statement); }
    @Transactional(readOnly = true) public AccountStatement document(String id, String cifId, boolean privileged) { get(id, cifId, privileged); return load(id); }
    private StatementView view(AccountStatement value) { return view(value, lines.findByStatementIdOrderBySequenceAsc(value.getId()).stream().map(line -> new StatementLineView(line.getSequence(), line.getOccurredAt(), line.getDescription(), line.getDebit(), line.getCredit(), line.getBalanceAfter(), line.getJournalNumber())).toList()); }
    private StatementView view(AccountStatement value, List<StatementLineView> lines) { return new StatementView(value.getId(), value.getAccountReference(), value.getAccountType(), value.getMaskedAccountReference(), value.getPeriodStart(), value.getPeriodEnd(), value.getCurrency(), value.getOpeningBalance(), value.getClosingBalance(), value.getGeneratedAt(), value.getStatus(), lines); }
    private AccountStatement load(String id) { return statements.findById(id).orElseThrow(() -> new StatementException(HttpStatus.NOT_FOUND, "STATEMENT_NOT_FOUND", "Statement was not found")); }
    private static BigDecimal money(BigDecimal value) { return value == null ? BigDecimal.ZERO.setScale(4) : value.setScale(4, RoundingMode.HALF_EVEN); }
    private static String hash(byte[] value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); } catch (Exception e) { throw new IllegalStateException(e); } }
}
