package com.moneybags.statements.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moneybags.statements.api.StatementDtos.StatementLineView;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class StatementPdfRendererTest {
    @Test
    void rendersOnlyExposedStatementDataInAnA4Pdf() throws Exception {
        byte[] bytes = StatementPdfRenderer.render(model(1));

        assertTrue(StatementPdfRenderer.usesCurrentTemplate(bytes));
        assertFalse(StatementPdfRenderer.usesCurrentTemplate("legacy plain document".getBytes()));
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertEquals(1, document.getNumberOfPages());
            assertEquals(595.27563f, document.getPage(0).getMediaBox().getWidth(), .01f);
            assertEquals(841.8898f, document.getPage(0).getMediaBox().getHeight(), .01f);
            assertTrue(text.contains("ACCOUNT STATEMENT"));
            assertTrue(text.contains("XXXXXXXX1234"));
            assertTrue(text.contains("SAVINGS"));
            assertTrue(text.contains("INR 125,430.50"));
            assertTrue(text.contains("PAY-0001 / JRN-0001"));
            assertTrue(text.contains("Page 1 of 1"));
            assertFalse(text.contains("ACC-UNMASKED-1234"));
            assertFalse(text.contains("Account holder"));
            assertFalse(text.contains("IFSC"));
            assertFalse(text.contains("Nominee"));
            assertFalse(text.contains("QR"));
        }
    }

    @Test
    void paginatesLongStatementsAndRepeatsContinuationTableHeaders() throws Exception {
        byte[] bytes = StatementPdfRenderer.render(model(72));

        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(document.getNumberOfPages() >= 4);
            assertTrue(occurrences(text, "TRANSACTION DETAILS - CONTINUED") >= 3);
            assertTrue(text.contains("Page 1 of " + document.getNumberOfPages()));
            assertTrue(text.contains("Page " + document.getNumberOfPages() + " of " + document.getNumberOfPages()));
            assertTrue(text.contains("PAY-0072 / JRN-0072"));
        }
    }

    @Test
    void writesARepresentativeVisualFixtureWhenRequested() throws Exception {
        String fixture = System.getProperty("moneybags.pdf.fixture");
        if (fixture == null || fixture.isBlank()) return;
        Path output = Path.of(fixture).toAbsolutePath();
        Files.createDirectories(output.getParent());
        Files.write(output, StatementPdfRenderer.render(model(32)));
        assertTrue(Files.size(output) > 1_000);
    }

    private static StatementPdfModel model(int transactionCount) {
        LocalDate start = LocalDate.of(2026, 7, 20);
        OffsetDateTime generated = OffsetDateTime.of(2026, 8, 18, 16, 45, 0, 0,
                ZoneOffset.ofHoursMinutes(5, 30));
        List<StatementLineView> lines = new ArrayList<>();
        BigDecimal balance = new BigDecimal("125430.50");
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (int index = 1; index <= transactionCount; index++) {
            boolean credit = index % 6 == 0;
            BigDecimal amount = credit ? new BigDecimal("12500.00")
                    : new BigDecimal(Integer.toString(210 + (index * 37))).setScale(2);
            BigDecimal debit = credit ? BigDecimal.ZERO : amount;
            BigDecimal credited = credit ? amount : BigDecimal.ZERO;
            debits = debits.add(debit);
            credits = credits.add(credited);
            balance = balance.subtract(debit).add(credited);
            String description = credit ? "Account credit received" :
                    "Digital account payment with a clear posted transaction description";
            lines.add(new StatementLineView(index, "TXN-%04d".formatted(index),
                    "PAY-%04d".formatted(index), generated.minusDays(transactionCount - index),
                    description, debit, credited, balance, "JRN-%04d".formatted(index)));
        }
        return new StatementPdfModel("STMT-20260818-0001", "XXXXXXXX1234", "SAVINGS", start,
                LocalDate.of(2026, 8, 18), generated, "INR", new BigDecimal("125430.50"),
                credits, debits, balance, lines);
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        for (int index = value.indexOf(needle); index >= 0;
             index = value.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }
}
