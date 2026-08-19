package com.moneybags.statements.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moneybags.statements.api.StatementDtos.GenerateAccountStatementRequest;
import com.moneybags.statements.entity.AccountStatement;
import com.moneybags.statements.exception.StatementException;
import com.moneybags.statements.integration.StatementSourceGateway;
import com.moneybags.statements.integration.StatementSourceGateway.AccountContext;
import com.moneybags.statements.integration.StatementSourceGateway.DepositActivity;
import com.moneybags.statements.integration.StatementSourceGateway.LedgerEntry;
import com.moneybags.statements.integration.StatementSourceGateway.StatementSource;
import com.moneybags.statements.repository.AccountStatementLineRepository;
import com.moneybags.statements.repository.AccountStatementRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatementServiceTest {
    private final AccountStatementRepository statements = mock(AccountStatementRepository.class);
    private final AccountStatementLineRepository lines = mock(AccountStatementLineRepository.class);
    private final StatementSourceGateway source = mock(StatementSourceGateway.class);
    private StatementService service;
    private LocalDate start;
    private LocalDate end;

    @BeforeEach
    void setUp() {
        service = new StatementService(statements, lines, source, false);
        end = LocalDate.now();
        start = end.minusDays(29);
        when(source.context("ACC-1")).thenReturn(new AccountContext("ACC-1", "XXXXXXXX1234",
                "SAVINGS", "INR", List.of("1001")));
    }

    @Test
    void returnsReconciledAccountActivityForAnOwner() {
        OffsetDateTime occurredAt = start.atTime(10, 0).atOffset(ZoneOffset.UTC);
        when(source.load("ACC-1", start, end)).thenReturn(new StatementSource(
                List.of(new LedgerEntry("JRN-1", start, occurredAt, "BOOK_TRANSFER",
                        new BigDecimal("200.0000"), BigDecimal.ZERO, "INR", "Transfer sent")),
                List.of(new DepositActivity("TXN-1", "PAY-1", "DEBIT",
                        new BigDecimal("200.0000"), "INR", new BigDecimal("1000.0000"),
                        new BigDecimal("800.0000"), occurredAt)),
                new BigDecimal("1000.0000"), new BigDecimal("800.0000"), "INR"));

        var result = service.activity("ACC-1", start, end, "1001", false);

        assertTrue(result.reconciled());
        assertEquals(new BigDecimal("200.0000"), result.totalDebits());
        assertEquals("JRN-1", result.lines().getFirst().journalNumber());
        assertEquals("PAY-1", result.lines().getFirst().paymentId());
    }

    @Test
    void hidesAnAccountOwnedByAnotherCustomer() {
        assertThrows(StatementException.class,
                () -> service.activity("ACC-1", start, end, "OTHER", false));
    }

    @Test
    void allowsDepositActivityWhenAccountingReconciliationIsOptional() {
        OffsetDateTime occurredAt = start.atTime(10, 0).atOffset(ZoneOffset.UTC);
        when(source.load("ACC-1", start, end)).thenReturn(new StatementSource(List.of(),
                List.of(new DepositActivity("TXN-1", "PAY-1", "DEBIT",
                        new BigDecimal("200.0000"), "INR", new BigDecimal("1000.0000"),
                        new BigDecimal("800.0000"), occurredAt)),
                new BigDecimal("1000.0000"), new BigDecimal("800.0000"), "INR"));

        assertTrue(service.activity("ACC-1", start, end, "1001", false).reconciled());
    }

    @Test
    void refusesOfficialPdfWhenAccountingReconciliationIsRequired() {
        OffsetDateTime occurredAt = start.atTime(10, 0).atOffset(ZoneOffset.UTC);
        when(source.load("ACC-1", start, end)).thenReturn(new StatementSource(List.of(),
                List.of(new DepositActivity("TXN-1", "PAY-1", "DEBIT",
                        new BigDecimal("200.0000"), "INR", new BigDecimal("1000.0000"),
                        new BigDecimal("800.0000"), occurredAt)),
                new BigDecimal("1000.0000"), new BigDecimal("800.0000"), "INR"));
        StatementService strictService = new StatementService(statements, lines, source, true);

        assertThrows(StatementException.class, () -> strictService.generateForAccount(
                new GenerateAccountStatementRequest("ACC-1", start, end), "1001", false));
    }

    @Test
    void upgradesAStoredLegacyPdfWhenItIsDownloaded() {
        AccountStatement statement = new AccountStatement("STMT-1", "1001", "ACC-1", "SAVINGS",
                "XXXXXXXX1234", start, end, "INR", new BigDecimal("1000.0000"),
                new BigDecimal("1000.0000"), OffsetDateTime.now(), "0".repeat(64),
                "legacy plain document".getBytes());
        when(statements.findById("STMT-1")).thenReturn(Optional.of(statement));
        when(lines.findByStatementIdOrderBySequenceAsc("STMT-1")).thenReturn(List.of());

        AccountStatement downloaded = service.document("STMT-1", "1001", false);

        assertTrue(StatementPdfRenderer.usesCurrentTemplate(downloaded.getDocumentData()));
        verify(statements).save(statement);
    }
}
