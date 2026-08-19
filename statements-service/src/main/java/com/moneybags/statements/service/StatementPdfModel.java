package com.moneybags.statements.service;

import com.moneybags.statements.api.StatementDtos.StatementLineView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** The deliberately limited set of already-exposed statement data permitted in the PDF. */
public record StatementPdfModel(
        String statementId,
        String maskedAccountReference,
        String accountType,
        LocalDate periodStart,
        LocalDate periodEnd,
        OffsetDateTime generatedAt,
        String currency,
        BigDecimal openingBalance,
        BigDecimal totalCredits,
        BigDecimal totalDebits,
        BigDecimal closingBalance,
        List<StatementLineView> lines) {

    public StatementPdfModel {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
