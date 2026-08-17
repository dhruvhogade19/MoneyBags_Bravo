package com.moneybags.statements.service;

import com.moneybags.statements.api.StatementDtos.StatementLineView;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Small valid PDF writer; avoids adding a rendering library for the first statement release. */
public final class StatementPdfRenderer {
    private StatementPdfRenderer() {}
    public static byte[] render(String id, String account, LocalDate start, LocalDate end, String currency,
            java.math.BigDecimal opening, java.math.BigDecimal closing, List<StatementLineView> lines) {
        StringBuilder stream = new StringBuilder("BT\n/F1 10 Tf\n45 800 Td\n");
        line(stream, "MONEYBAGS BANK"); line(stream, "Account Statement"); line(stream, "Statement ID: " + id);
        line(stream, "Account: " + account); line(stream, "Period: " + start + " to " + end);
        line(stream, "Opening balance: " + money(opening) + " " + currency); line(stream, " ");
        line(stream, "Date        Description                         Debit      Credit     Balance");
        for (StatementLineView value : lines) line(stream, "%-11s %-34s %10s %10s %10s".formatted(value.occurredAt().toLocalDate(),
                trim(value.description(), 34), money(value.debit()), money(value.credit()), money(value.balanceAfter())));
        line(stream, " "); line(stream, "Closing balance: " + money(closing) + " " + currency); stream.append("ET");
        byte[] content = stream.toString().getBytes(StandardCharsets.US_ASCII);
        String[] objects = {"<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [4 0 R] /Count 1 >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents 5 0 R >>",
                "<< /Length " + content.length + " >>\nstream\n" + new String(content, StandardCharsets.US_ASCII) + "\nendstream"};
        ByteArrayOutputStream output = new ByteArrayOutputStream(); output.writeBytes("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
        int[] offsets = new int[objects.length];
        for (int i = 0; i < objects.length; i++) { offsets[i] = output.size(); output.writeBytes((i + 1 + " 0 obj\n" + objects[i] + "\nendobj\n").getBytes(StandardCharsets.US_ASCII)); }
        int xref = output.size(); output.writeBytes(("xref\n0 6\n0000000000 65535 f \n").getBytes(StandardCharsets.US_ASCII));
        for (int offset : offsets) output.writeBytes(("%010d 00000 n \n".formatted(offset)).getBytes(StandardCharsets.US_ASCII));
        output.writeBytes(("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF").getBytes(StandardCharsets.US_ASCII));
        return output.toByteArray();
    }
    private static void line(StringBuilder stream, String value) { stream.append('(').append(value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replaceAll("[^\\x20-\\x7E]", "?")).append(") Tj\n0 -17 Td\n"); }
    private static String money(java.math.BigDecimal value) { return value == null || value.signum() == 0 ? "" : String.format(Locale.ROOT, "%.2f", value); }
    private static String trim(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 3) + "..."; }
}
