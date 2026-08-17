package com.moneybags.statements.service;

import com.moneybags.statements.api.StatementDtos.StatementLineView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementPdfRendererTest {
    @Test
    void rendersAValidPdfHeader() {
        byte[] document = StatementPdfRenderer.render("STMT-1", "XXXX1234", LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31), "INR", new BigDecimal("1000.0000"), new BigDecimal("800.0000"),
                List.of(new StatementLineView(1, OffsetDateTime.parse("2026-08-02T10:00:00Z"), "Transfer sent",
                        new BigDecimal("200.0000"), BigDecimal.ZERO, new BigDecimal("800.0000"), "JRN-1")));
        assertTrue(new String(document, 0, 4, StandardCharsets.US_ASCII).equals("%PDF"));
    }
}
