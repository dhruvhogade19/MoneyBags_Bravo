package com.moneybags.statements.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "moneybags.statements", name = "stub-upstream-clients", havingValue = "true")
public class StubStatementSourceGateway implements StatementSourceGateway {
    public StatementSource load(String accountReference, LocalDate start, LocalDate end) {
        OffsetDateTime first = start.atTime(9, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime second = start.plusDays(1).atTime(10, 0).atOffset(ZoneOffset.UTC);
        return new StatementSource(List.of(
                new LedgerEntry("JRN-DEMO-1", start, first, "BOOK_TRANSFER", new BigDecimal("250.0000"), BigDecimal.ZERO, "INR", "Transfer sent"),
                new LedgerEntry("JRN-DEMO-2", start.plusDays(1), second, "BOOK_TRANSFER", BigDecimal.ZERO, new BigDecimal("50.0000"), "INR", "Transfer received")),
                List.of(new DepositActivity("DEP-DEMO-1", "PAY-DEMO-1", "DEBIT", new BigDecimal("250.0000"), "INR", new BigDecimal("1000.0000"), new BigDecimal("750.0000"), first),
                        new DepositActivity("DEP-DEMO-2", "PAY-DEMO-2", "CREDIT", new BigDecimal("50.0000"), "INR", new BigDecimal("750.0000"), new BigDecimal("800.0000"), second)));
    }
}
