package com.moneybags.billing;

import com.moneybags.billing.BillGenerationApplication.BillLineResponse;
import com.moneybags.billing.BillGenerationApplication.BillResponse;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** A4 credit-card statement using the MoneyBags account-statement visual template. */
@Component
public class StatementPdfRenderer {
    private static final float W = PDRectangle.A4.getWidth(), H = PDRectangle.A4.getHeight(), M = 36f, CW = W - 72f, FOOTER = 52f;
    private static final Color WINE = c("8B1F39"), ROSE = c("CC7C6F"), BLUSH = c("F8E9E8"), PALE = c("FCF6F5"), BORDER = c("E9C7C9"), TEXT = c("24191C"), MUTED = c("76666A"), CREDIT = c("26754B");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm XXX", Locale.ENGLISH);
    private static final float[] COL = {58, 145, 105, 68, 68, 79};

    public byte[] render(BillResponse bill) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            new Renderer(document, bill, regular, bold).draw();
            PDDocumentInformation info = new PDDocumentInformation();
            info.setAuthor("MoneyBags"); info.setCreator("MoneyBags Bill Generation Service"); info.setTitle("MoneyBags Credit Card Statement");
            info.setSubject("Credit card statement " + bill.periodStart() + " to " + bill.periodEnd()); document.setDocumentInformation(info);
            document.save(out); return out.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("Could not create billing statement PDF", e); }
    }

    private static final class Renderer {
        private final PDDocument doc; private final BillResponse bill; private final PDFont regular, bold; private PageState page;
        private BigDecimal runningBalance = BigDecimal.ZERO;
        Renderer(PDDocument doc, BillResponse bill, PDFont regular, PDFont bold) { this.doc = doc; this.bill = bill; this.regular = regular; this.bold = bold; }
        void draw() throws IOException {
            page = newPage(false); firstHeader(); accountInfo(); balanceSummary(); paymentInfo(); transactionsHeader(false);
            List<BillLineResponse> lines = bill.lines().stream().sorted(Comparator.comparing(BillLineResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
            if (lines.isEmpty()) emptyTransactions(); else for (BillLineResponse line : lines) transaction(line);
            summary(lines.size()); closePage(); footers();
        }
        private PageState newPage(boolean continuing) throws IOException {
            PDPage page = new PDPage(PDRectangle.A4); doc.addPage(page); PageState result = new PageState(new PDPageContentStream(doc, page), H - 32);
            if (continuing) { continuationHeader(result); transactionsHeader(result, true); } return result;
        }
        private void firstHeader() throws IOException {
            float top = H - 24; rounded(page.stream, M, top - 100, CW, 100, 13, PALE);
            page.stream.setNonStrokingColor(WINE); page.stream.addRect(M + 188, top - 64, 140, 64); page.stream.fill();
            page.stream.setNonStrokingColor(ROSE); page.stream.addRect(M + 220, top - 55, 84, 55); page.stream.fill();
            brand(page.stream, M + 18, top - 58, 34); text(page.stream, bold, 17, WINE, M + 62, top - 43, "moneybags");
            float right = M + 337; text(page.stream, bold, 9, WINE, right, top - 26, "CREDIT CARD STATEMENT");
            label(page.stream, right, top - 45, "Generated", GENERATED.format(bill.generatedAt()), 71); label(page.stream, right, top - 62, "Period", DATE.format(bill.periodStart()) + " - " + DATE.format(bill.periodEnd()), 71); label(page.stream, right, top - 79, "Statement ID", bill.billId(), 71);
            line(page.stream, M, top - 104, W - M, top - 104, WINE, 1.2f); page.y = top - 119;
        }
        private void accountInfo() throws IOException {
            section(page.stream, page.y, "ACCOUNT INFORMATION"); float bottom = page.y - 78; roundedStroke(page.stream, M, bottom, CW, 61, 9, Color.WHITE, BORDER);
            label(page.stream, M + 18, bottom + 38, "Card account", mask(bill.accountId()), 83); label(page.stream, M + 18, bottom + 19, "Account type", "CREDIT CARD", 83);
            label(page.stream, M + 286, bottom + 38, "Currency", safe(bill.currency()), 58); label(page.stream, M + 286, bottom + 19, "Transactions", Integer.toString(bill.lines().size()), 58); page.y = bottom - 14;
        }
        private void balanceSummary() throws IOException {
            float height = 77, bottom = page.y - height, tile = CW / 4; roundedStroke(page.stream, M, bottom, CW, height, 10, PALE, BORDER);
            tile(M, bottom, tile, "PREVIOUS BALANCE", bill.previousBalance()); tile(M + tile, bottom, tile, "PAYMENTS RECEIVED", credits()); tile(M + 2 * tile, bottom, tile, "NEW CHARGES", debits());
            rounded(page.stream, M + 3 * tile, bottom, tile, height, 10, WINE); text(page.stream, bold, 6.5f, Color.WHITE, M + 3 * tile + 13, bottom + 50, "TOTAL AMOUNT DUE"); text(page.stream, bold, 12.5f, Color.WHITE, M + 3 * tile + 13, bottom + 26, money(bill.totalAmountDue())); page.y = bottom - 18;
        }
        private void tile(float x, float y, float width, String label, BigDecimal value) throws IOException { if (x > M) line(page.stream, x, y + 15, x, y + 62, BORDER, .6f); text(page.stream, bold, 6.2f, WINE, x + 13, y + 50, label); text(page.stream, bold, 10.2f, TEXT, x + 13, y + 27, money(value)); }
        private void paymentInfo() throws IOException { section(page.stream, page.y, "PAYMENT INFORMATION"); float bottom = page.y - 57; roundedStroke(page.stream, M, bottom, CW, 40, 8, Color.WHITE, BORDER); label(page.stream, M + 18, bottom + 22, "Minimum amount due", money(bill.minimumAmountDue()), 87); label(page.stream, M + 286, bottom + 22, "Payment due date", DATE.format(bill.paymentDueDate()), 78); page.y = bottom - 15; }
        private void transactionsHeader(boolean continued) throws IOException { transactionsHeader(page, continued); }
        private void transactionsHeader(PageState target, boolean continued) throws IOException {
            section(target.stream, target.y, continued ? "TRANSACTION DETAILS - CONTINUED" : "TRANSACTION DETAILS"); right(target.stream, regular, 6.5f, MUTED, W - M, target.y, "All amounts in " + safe(bill.currency())); target.y -= 18;
            rounded(target.stream, M, target.y - 26, CW, 26, 7, BLUSH); String[] headings = {"DATE", "DESCRIPTION", "REFERENCE", "DEBIT", "CREDIT", "BALANCE"}; float x = M;
            for (int i = 0; i < headings.length; i++) { if (i < 3) text(target.stream, bold, 6.5f, WINE, x + 7, target.y - 16.5f, headings[i]); else right(target.stream, bold, 6.5f, WINE, x + COL[i] - 7, target.y - 16.5f, headings[i]); x += COL[i]; } target.y -= 26;
        }
        private void transaction(BillLineResponse item) throws IOException {
            List<String> description = wrap(item.description(), regular, 7.1f, COL[1] - 14, 3), reference = wrap(item.sourceReference(), regular, 6.7f, COL[2] - 14, 3); float height = Math.max(26, 11 + Math.max(description.size(), reference.size()) * 8.4f);
            if (page.y - height < FOOTER) { closePage(); page = newPage(true); } float base = page.y - 12.8f, x = M; text(page.stream, regular, 6.9f, TEXT, x + 7, base, item.occurredAt() == null ? "-" : DATE.format(item.occurredAt().toLocalDate())); x += COL[0]; draws(page.stream, regular, 7.1f, TEXT, x + 7, base, description); x += COL[1]; draws(page.stream, regular, 6.7f, MUTED, x + 7, base, reference); x += COL[2];
            boolean paid = payment(item); runningBalance = runningBalance.add(paid ? item.amount().abs().negate() : item.amount()); right(page.stream, regular, 6.9f, TEXT, x + COL[3] - 7, base, paid ? "-" : amount(item.amount())); x += COL[3]; right(page.stream, regular, 6.9f, CREDIT, x + COL[4] - 7, base, paid ? amount(item.amount().abs()) : "-"); x += COL[4]; right(page.stream, bold, 6.9f, TEXT, x + COL[5] - 7, base, amount(runningBalance)); line(page.stream, M, page.y - height, W - M, page.y - height, BORDER, .35f); page.y -= height;
        }
        private void emptyTransactions() throws IOException { roundedStroke(page.stream, M, page.y - 52, CW, 52, 7, PALE, BORDER); text(page.stream, regular, 8, MUTED, M + 16, page.y - 31, "No posted transactions are available for this statement period."); page.y -= 52; }
        private void summary(int count) throws IOException {
            if (page.y - 123 < FOOTER) { closePage(); page = newPage(false); continuationHeader(page); } page.y -= 18; section(page.stream, page.y, "STATEMENT SUMMARY"); float bottom = page.y - 82, tile = CW / 4; roundedStroke(page.stream, M, bottom, CW, 65, 9, Color.WHITE, BORDER);
            summaryValue(M + 15, bottom + 41, "Payments received", credits()); summaryValue(M + tile + 12, bottom + 41, "New charges", debits()); text(page.stream, regular, 6.6f, MUTED, M + 2 * tile + 12, bottom + 45, "Number of transactions"); text(page.stream, bold, 10.4f, TEXT, M + 2 * tile + 12, bottom + 25, Integer.toString(count)); rounded(page.stream, M + 3 * tile, bottom, tile, 65, 9, BLUSH); text(page.stream, bold, 6.4f, WINE, M + 3 * tile + 12, bottom + 44, "TOTAL AMOUNT DUE"); text(page.stream, bold, 11, WINE, M + 3 * tile + 12, bottom + 24, money(bill.outstandingAmount()));
        }
        private void summaryValue(float x, float y, String name, BigDecimal value) throws IOException { text(page.stream, regular, 6.6f, MUTED, x, y + 4, name); text(page.stream, bold, 10.4f, TEXT, x, y - 16, money(value)); }
        private void continuationHeader(PageState target) throws IOException { float top = H - 29; brand(target.stream, M, top - 30, 25); text(target.stream, bold, 13, WINE, M + 36, top - 13, "moneybags"); text(target.stream, bold, 8, WINE, W - M - 130, top - 10, "CREDIT CARD STATEMENT"); right(target.stream, regular, 6.5f, MUTED, W - M, top - 28, DATE.format(bill.periodStart()) + " - " + DATE.format(bill.periodEnd())); line(target.stream, M, top - 42, W - M, top - 42, BORDER, .7f); target.y = top - 63; }
        private void footers() throws IOException { for (int i = 0; i < doc.getNumberOfPages(); i++) try (PDPageContentStream s = new PDPageContentStream(doc, doc.getPage(i), AppendMode.APPEND, true, true)) { line(s, M, 35, W - M, 35, WINE, .8f); text(s, bold, 6.5f, WINE, M, 21, "MoneyBags"); centered(s, regular, 5.8f, MUTED, W / 2, 21, "Statement ID: " + bill.billId()); right(s, bold, 6.2f, WINE, W - M, 21, "Page " + (i + 1) + " of " + doc.getNumberOfPages()); } }
        private BigDecimal credits() { return bill.lines().stream().filter(StatementPdfRenderer::payment).map(BillLineResponse::amount).map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add); }
        private BigDecimal debits() { return bill.lines().stream().filter(l -> !payment(l) && !"PREVIOUS_BALANCE".equals(l.lineType())).map(BillLineResponse::amount).map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add); }
        private void closePage() throws IOException { if (page.stream != null) { page.stream.close(); page.stream = null; } }
    }
    private static boolean payment(BillLineResponse item) { return "PAYMENT".equals(item.lineType()) || item.amount().signum() < 0; }
    private static final class PageState {
        private PDPageContentStream stream;
        private float y;

        private PageState(PDPageContentStream stream, float y) {
            this.stream = stream;
            this.y = y;
        }
    }
    private static void section(PDPageContentStream s, float y, String title) throws IOException { rounded(s, M, y - 11, 22, 22, 6, BLUSH); for (int i = 0; i < 3; i++) line(s, M + 6, y + 5 - i * 5, M + 16, y + 5 - i * 5, WINE, 1); text(s, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 8.2f, WINE, M + 31, y - 3, title); }
    private static void label(PDPageContentStream s, float x, float y, String name, String value, float offset) throws IOException { text(s, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 6.5f, MUTED, x, y, name); text(s, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 7, TEXT, x + offset, y, value); }
    private static void brand(PDPageContentStream s, float x, float y, float size) throws IOException { rounded(s, x, y, size, size, 7, Color.WHITE); float cx = x + size / 2, cy = y + size / 2, o = size * .27f, in = size * .08f; s.setNonStrokingColor(WINE); s.moveTo(cx, cy + o); s.lineTo(cx + in, cy + in); s.lineTo(cx + o, cy); s.lineTo(cx + in, cy - in); s.lineTo(cx, cy - o); s.lineTo(cx - in, cy - in); s.lineTo(cx - o, cy); s.lineTo(cx - in, cy + in); s.closePath(); s.fill(); }
    private static void draws(PDPageContentStream s, PDFont f, float size, Color color, float x, float y, List<String> lines) throws IOException { for (int i = 0; i < lines.size(); i++) text(s, f, size, color, x, y - i * 8.4f, lines.get(i)); }
    private static void text(PDPageContentStream s, PDFont f, float size, Color color, float x, float y, String value) throws IOException { if (value == null || value.isEmpty()) return; s.beginText(); s.setFont(f, size); s.setNonStrokingColor(color); s.newLineAtOffset(x, y); s.showText(safe(value)); s.endText(); }
    private static void right(PDPageContentStream s, PDFont f, float size, Color color, float x, float y, String value) throws IOException { text(s, f, size, color, x - width(f, size, value), y, value); }
    private static void centered(PDPageContentStream s, PDFont f, float size, Color color, float x, float y, String value) throws IOException { text(s, f, size, color, x - width(f, size, value) / 2, y, value); }
    private static List<String> wrap(String value, PDFont font, float size, float max, int limit) throws IOException { String normalized = safe(value).trim(); if (normalized.isEmpty()) return List.of("-"); List<String> out = new ArrayList<>(); String line = ""; for (String word : normalized.split("\\s+")) { String candidate = line.isEmpty() ? word : line + " " + word; if (width(font, size, candidate) <= max) line = candidate; else { if (!line.isEmpty()) out.add(line); line = word; } } if (!line.isEmpty()) out.add(line); return out.size() <= limit ? out : out.subList(0, limit); }
    private static float width(PDFont font, float size, String value) throws IOException { return font.getStringWidth(safe(value)) * size / 1000; }
    private static String amount(BigDecimal value) { return value == null || value.signum() == 0 ? "-" : number(value.abs()); }
    private static String money(BigDecimal value) { return "INR " + number(value); }
    private static String number(BigDecimal value) { return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format((value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP)); }
    private static String mask(String value) { String clean = safe(value); return "XXXXXXXXXX" + (clean.length() <= 4 ? clean : clean.substring(clean.length() - 4)); }
    private static String safe(String value) { return value == null ? "" : value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").replaceAll("[\\r\\n\\t]+", " ").replaceAll("[^\\x20-\\x7E]", "-"); }
    private static Color c(String hex) { return new Color(Integer.parseInt(hex, 16)); }
    private static void line(PDPageContentStream s, float x1, float y1, float x2, float y2, Color color, float width) throws IOException { s.setStrokingColor(color); s.setLineWidth(width); s.moveTo(x1, y1); s.lineTo(x2, y2); s.stroke(); }
    private static void rounded(PDPageContentStream s, float x, float y, float w, float h, float r, Color color) throws IOException { path(s, x, y, w, h, r); s.setNonStrokingColor(color); s.fill(); }
    private static void roundedStroke(PDPageContentStream s, float x, float y, float w, float h, float r, Color fill, Color stroke) throws IOException { path(s, x, y, w, h, r); s.setNonStrokingColor(fill); s.setStrokingColor(stroke); s.setLineWidth(.7f); s.fillAndStroke(); }
    private static void path(PDPageContentStream s, float x, float y, float w, float h, float r) throws IOException { float q = Math.min(r, Math.min(w, h) / 2), k = .55228475f; s.moveTo(x + q, y); s.lineTo(x + w - q, y); s.curveTo(x + w - q + q*k, y, x+w, y+q-q*k, x+w, y+q); s.lineTo(x+w, y+h-q); s.curveTo(x+w, y+h-q+q*k, x+w-q+q*k, y+h, x+w-q, y+h); s.lineTo(x+q, y+h); s.curveTo(x+q-q*k, y+h, x, y+h-q+q*k, x, y+h-q); s.lineTo(x, y+q); s.curveTo(x, y+q-q*k, x+q-q*k, y, x+q, y); s.closePath(); }
}
