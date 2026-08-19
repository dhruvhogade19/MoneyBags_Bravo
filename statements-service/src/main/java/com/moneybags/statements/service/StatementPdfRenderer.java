package com.moneybags.statements.service;

import com.moneybags.statements.api.StatementDtos.StatementLineView;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/** Print-safe A4 statement renderer using only fields already exposed by Statements Service. */
public final class StatementPdfRenderer {
    private static final String DOCUMENT_TITLE = "MoneyBags Account Statement";
    private static final String DOCUMENT_CREATOR = "MoneyBags Statements Service";
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 36f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - (2 * MARGIN);
    private static final float FOOTER_TOP = 52f;

    private static final Color BRAND_DARK = color("8B1F39");
    private static final Color BRAND_LIGHT = color("CC7C6F");
    private static final Color BLUSH = color("F8E9E8");
    private static final Color BLUSH_LIGHT = color("FCF6F5");
    private static final Color BORDER = color("E9C7C9");
    private static final Color TEXT = color("24191C");
    private static final Color MUTED = color("76666A");
    private static final Color WHITE = Color.WHITE;
    private static final Color CREDIT = color("26754B");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter GENERATED =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm XXX", Locale.ENGLISH);
    private static final float[] COLUMN_WIDTHS = {58f, 145f, 105f, 68f, 68f, 79f};

    private StatementPdfRenderer() {}

    public static byte[] render(StatementPdfModel model) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType0Font regular = loadFont(document, "/fonts/Poppins-Regular.ttf");
            PDType0Font semiBold = loadFont(document, "/fonts/Poppins-SemiBold.ttf");
            PDType0Font bold = loadFont(document, "/fonts/Poppins-Bold.ttf");
            Renderer renderer = new Renderer(document, model, regular, semiBold, bold);
            renderer.draw();
            setMetadata(document, model);
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not render the account statement PDF", exception);
        }
    }

    static boolean usesCurrentTemplate(byte[] documentBytes) {
        if (documentBytes == null || documentBytes.length == 0) return false;
        try (PDDocument document = Loader.loadPDF(documentBytes)) {
            PDDocumentInformation information = document.getDocumentInformation();
            return DOCUMENT_TITLE.equals(information.getTitle())
                    && DOCUMENT_CREATOR.equals(information.getCreator());
        } catch (IOException exception) {
            return false;
        }
    }

    private static PDType0Font loadFont(PDDocument document, String path) throws IOException {
        try (InputStream stream = StatementPdfRenderer.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing embedded PDF font: " + path);
            return PDType0Font.load(document, stream, true);
        }
    }

    private static void setMetadata(PDDocument document, StatementPdfModel model) {
        PDDocumentInformation information = new PDDocumentInformation();
        information.setAuthor("MoneyBags");
        information.setCreator(DOCUMENT_CREATOR);
        information.setTitle(DOCUMENT_TITLE);
        information.setSubject("Account statement " + model.periodStart() + " to " + model.periodEnd());
        information.setKeywords("MoneyBags, account statement, " + safe(model.maskedAccountReference()));
        document.setDocumentInformation(information);
    }

    private static final class Renderer {
        private final PDDocument document;
        private final StatementPdfModel model;
        private final PDFont regular;
        private final PDFont semiBold;
        private final PDFont bold;
        private PageState state;

        private Renderer(PDDocument document, StatementPdfModel model,
                         PDFont regular, PDFont semiBold, PDFont bold) {
            this.document = document;
            this.model = model;
            this.regular = regular;
            this.semiBold = semiBold;
            this.bold = bold;
        }

        private void draw() throws IOException {
            state = newPage(false);
            drawFirstPageHeader();
            drawAccountInformation();
            drawBalanceSummary();
            drawTransactionSection(state, false);

            if (model.lines().isEmpty()) {
                drawEmptyTransactions();
            } else {
                for (StatementLineView line : model.lines()) drawTransaction(line);
            }
            drawStatementSummary();
            closeState();
            drawFooters();
        }

        private PageState newPage(boolean continuation) throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            PageState next = new PageState(stream, PAGE_HEIGHT - 32f);
            if (continuation) {
                drawContinuationHeader(next);
                drawTransactionSection(next, true);
            }
            return next;
        }

        private void drawFirstPageHeader() throws IOException {
            float top = PAGE_HEIGHT - 24f;
            roundedFill(state.stream, MARGIN, top - 100f, CONTENT_WIDTH, 100f, 13f, BLUSH_LIGHT);

            state.stream.setNonStrokingColor(BRAND_DARK);
            state.stream.moveTo(MARGIN + 188f, top);
            state.stream.lineTo(MARGIN + 352f, top);
            state.stream.curveTo(MARGIN + 322f, top - 14f, MARGIN + 294f, top - 55f,
                    MARGIN + 252f, top - 68f);
            state.stream.curveTo(MARGIN + 226f, top - 76f, MARGIN + 205f, top - 70f,
                    MARGIN + 188f, top - 64f);
            state.stream.closePath();
            state.stream.fill();

            state.stream.setNonStrokingColor(BRAND_LIGHT);
            state.stream.moveTo(MARGIN + 220f, top);
            state.stream.lineTo(MARGIN + 304f, top);
            state.stream.curveTo(MARGIN + 280f, top - 16f, MARGIN + 263f, top - 37f,
                    MARGIN + 236f, top - 49f);
            state.stream.curveTo(MARGIN + 229f, top - 52f, MARGIN + 224f, top - 54f,
                    MARGIN + 220f, top - 55f);
            state.stream.closePath();
            state.stream.fill();

            drawBrandMark(state.stream, MARGIN + 18f, top - 58f, 34f);
            text(state.stream, bold, 17f, BRAND_DARK, MARGIN + 62f, top - 43f, "moneybags");

            float right = MARGIN + 337f;
            text(state.stream, semiBold, 9f, BRAND_DARK, right, top - 26f, "ACCOUNT STATEMENT");
            labelValue(state.stream, right, top - 45f, "Generated", GENERATED.format(model.generatedAt()), 71f);
            labelValue(state.stream, right, top - 62f, "Period",
                    DATE.format(model.periodStart()) + " - " + DATE.format(model.periodEnd()), 71f);
            labelValue(state.stream, right, top - 79f, "Statement ID", model.statementId(), 71f);

            state.stream.setStrokingColor(BRAND_DARK);
            state.stream.setLineWidth(1.2f);
            state.stream.moveTo(MARGIN, top - 104f);
            state.stream.lineTo(PAGE_WIDTH - MARGIN, top - 104f);
            state.stream.stroke();
            state.y = top - 119f;
        }

        private void drawAccountInformation() throws IOException {
            sectionTitle(state.stream, state.y, "ACCOUNT INFORMATION");
            float bottom = state.y - 78f;
            roundedFillAndStroke(state.stream, MARGIN, bottom, CONTENT_WIDTH, 61f, 9f, WHITE, BORDER);

            float firstX = MARGIN + 18f;
            float secondX = MARGIN + 286f;
            labelValue(state.stream, firstX, bottom + 38f, "Account number",
                    model.maskedAccountReference(), 83f);
            labelValue(state.stream, firstX, bottom + 19f, "Account type", model.accountType(), 83f);
            labelValue(state.stream, secondX, bottom + 38f, "Currency", model.currency(), 58f);
            labelValue(state.stream, secondX, bottom + 19f, "Transactions",
                    Integer.toString(model.lines().size()), 58f);
            state.y = bottom - 14f;
        }

        private void drawBalanceSummary() throws IOException {
            float height = 77f;
            float bottom = state.y - height;
            roundedFillAndStroke(state.stream, MARGIN, bottom, CONTENT_WIDTH, height, 10f, BLUSH_LIGHT, BORDER);
            float tileWidth = CONTENT_WIDTH / 4f;
            summaryTile(state.stream, MARGIN, bottom, tileWidth, "OPENING BALANCE", model.openingBalance());
            summaryTile(state.stream, MARGIN + tileWidth, bottom, tileWidth, "TOTAL CREDITS", model.totalCredits());
            summaryTile(state.stream, MARGIN + (2 * tileWidth), bottom, tileWidth, "TOTAL DEBITS", model.totalDebits());
            gradientTile(state.stream, MARGIN + (3 * tileWidth), bottom, tileWidth, height);
            text(state.stream, semiBold, 6.5f, WHITE, MARGIN + (3 * tileWidth) + 13f, bottom + 50f,
                    "CLOSING BALANCE");
            text(state.stream, bold, 12.5f, WHITE, MARGIN + (3 * tileWidth) + 13f, bottom + 26f,
                    moneyWithCurrency(model.closingBalance()));
            state.y = bottom - 18f;
        }

        private void summaryTile(PDPageContentStream stream, float x, float y, float width,
                                 String label, BigDecimal amount) throws IOException {
            if (x > MARGIN) {
                stream.setStrokingColor(BORDER);
                stream.setLineWidth(.6f);
                stream.moveTo(x, y + 15f);
                stream.lineTo(x, y + 62f);
                stream.stroke();
            }
            text(stream, semiBold, 6.2f, BRAND_DARK, x + 13f, y + 50f, label);
            text(stream, bold, 10.2f, TEXT, x + 13f, y + 27f, moneyWithCurrency(amount));
        }

        private void gradientTile(PDPageContentStream stream, float x, float y,
                                  float width, float height) throws IOException {
            roundedFill(stream, x, y, width, height, 10f, BRAND_DARK);
            int slices = 24;
            float innerX = x + 3f;
            float innerWidth = width - 6f;
            for (int index = 0; index < slices; index++) {
                float ratio = index / (float) (slices - 1);
                stream.setNonStrokingColor(interpolate(BRAND_DARK, BRAND_LIGHT, ratio));
                stream.addRect(innerX + (index * innerWidth / slices), y + 3f,
                        innerWidth / slices + .4f, height - 6f);
                stream.fill();
            }
        }

        private void drawTransactionSection(PageState target, boolean continuation) throws IOException {
            sectionTitle(target.stream, target.y,
                    continuation ? "TRANSACTION DETAILS - CONTINUED" : "TRANSACTION DETAILS");
            textRight(target.stream, regular, 6.5f, MUTED, PAGE_WIDTH - MARGIN, target.y,
                    "All amounts in " + model.currency());
            target.y -= 18f;
            drawTableHeader(target);
        }

        private void drawTableHeader(PageState target) throws IOException {
            float height = 26f;
            roundedFill(target.stream, MARGIN, target.y - height, CONTENT_WIDTH, height, 7f, BLUSH);
            String[] labels = {"DATE", "DESCRIPTION", "REFERENCE", "DEBIT", "CREDIT", "BALANCE"};
            float x = MARGIN;
            for (int index = 0; index < labels.length; index++) {
                if (index < 3) {
                    text(target.stream, semiBold, 6.5f, BRAND_DARK, x + 7f, target.y - 16.5f, labels[index]);
                } else {
                    textRight(target.stream, semiBold, 6.5f, BRAND_DARK,
                            x + COLUMN_WIDTHS[index] - 7f, target.y - 16.5f, labels[index]);
                }
                x += COLUMN_WIDTHS[index];
            }
            target.y -= height;
        }

        private void drawTransaction(StatementLineView line) throws IOException {
            List<String> description = wrap(line.description(), regular, 7.1f, COLUMN_WIDTHS[1] - 14f, 3);
            List<String> reference = wrap(reference(line), regular, 6.7f, COLUMN_WIDTHS[2] - 14f, 3);
            int lineCount = Math.max(description.size(), reference.size());
            float rowHeight = Math.max(26f, 11f + (lineCount * 8.4f));
            if (state.y - rowHeight < FOOTER_TOP) {
                closeState();
                state = newPage(true);
            }

            if (line.sequence() % 2 == 0) {
                state.stream.setNonStrokingColor(BLUSH_LIGHT);
                state.stream.addRect(MARGIN, state.y - rowHeight, CONTENT_WIDTH, rowHeight);
                state.stream.fill();
            }
            float x = MARGIN;
            float baseline = state.y - 12.8f;
            text(state.stream, regular, 6.9f, TEXT, x + 7f, baseline,
                    line.occurredAt() == null ? "-" : DATE.format(line.occurredAt().toLocalDate()));
            x += COLUMN_WIDTHS[0];
            drawLines(state.stream, regular, 7.1f, TEXT, x + 7f, baseline, 8.4f, description);
            x += COLUMN_WIDTHS[1];
            drawLines(state.stream, regular, 6.7f, MUTED, x + 7f, baseline, 8.4f, reference);
            x += COLUMN_WIDTHS[2];
            textRight(state.stream, regular, 6.9f, TEXT, x + COLUMN_WIDTHS[3] - 7f, baseline,
                    tableMoney(line.debit()));
            x += COLUMN_WIDTHS[3];
            textRight(state.stream, regular, 6.9f, CREDIT, x + COLUMN_WIDTHS[4] - 7f, baseline,
                    tableMoney(line.credit()));
            x += COLUMN_WIDTHS[4];
            textRight(state.stream, semiBold, 6.9f, TEXT, x + COLUMN_WIDTHS[5] - 7f, baseline,
                    amount(line.balanceAfter()));

            state.stream.setStrokingColor(BORDER);
            state.stream.setLineWidth(.35f);
            state.stream.moveTo(MARGIN, state.y - rowHeight);
            state.stream.lineTo(PAGE_WIDTH - MARGIN, state.y - rowHeight);
            state.stream.stroke();
            state.y -= rowHeight;
        }

        private void drawEmptyTransactions() throws IOException {
            float height = 52f;
            roundedFillAndStroke(state.stream, MARGIN, state.y - height, CONTENT_WIDTH, height,
                    7f, BLUSH_LIGHT, BORDER);
            text(state.stream, regular, 8f, MUTED, MARGIN + 16f, state.y - 31f,
                    "No posted transactions are available for this statement period.");
            state.y -= height;
        }

        private void drawStatementSummary() throws IOException {
            float sectionHeight = 105f;
            if (state.y - sectionHeight - 18f < FOOTER_TOP) {
                closeState();
                state = newPage(false);
                drawContinuationHeader(state);
            }
            state.y -= 18f;
            sectionTitle(state.stream, state.y, "STATEMENT SUMMARY");
            float bottom = state.y - 82f;
            roundedFillAndStroke(state.stream, MARGIN, bottom, CONTENT_WIDTH, 65f, 9f, WHITE, BORDER);

            float tileWidth = CONTENT_WIDTH / 4f;
            summaryLine(state.stream, MARGIN + 15f, bottom + 41f, "Total credits", model.totalCredits());
            summaryLine(state.stream, MARGIN + tileWidth + 12f, bottom + 41f, "Total debits", model.totalDebits());
            text(state.stream, regular, 6.6f, MUTED, MARGIN + (2 * tileWidth) + 12f, bottom + 45f,
                    "Number of transactions");
            text(state.stream, bold, 10.4f, TEXT, MARGIN + (2 * tileWidth) + 12f, bottom + 25f,
                    Integer.toString(model.lines().size()));
            roundedFill(state.stream, MARGIN + (3 * tileWidth), bottom, tileWidth, 65f, 9f, BLUSH);
            text(state.stream, semiBold, 6.4f, BRAND_DARK, MARGIN + (3 * tileWidth) + 12f, bottom + 44f,
                    "CLOSING BALANCE");
            text(state.stream, bold, 11f, BRAND_DARK, MARGIN + (3 * tileWidth) + 12f, bottom + 24f,
                    moneyWithCurrency(model.closingBalance()));
            state.y = bottom;
        }

        private void summaryLine(PDPageContentStream stream, float x, float y,
                                 String label, BigDecimal value) throws IOException {
            text(stream, regular, 6.6f, MUTED, x, y + 4f, label);
            text(stream, bold, 10.4f, TEXT, x, y - 16f, moneyWithCurrency(value));
        }

        private void drawContinuationHeader(PageState target) throws IOException {
            float top = PAGE_HEIGHT - 29f;
            drawBrandMark(target.stream, MARGIN, top - 30f, 25f);
            text(target.stream, bold, 13f, BRAND_DARK, MARGIN + 36f, top - 13f, "moneybags");
            text(target.stream, semiBold, 8f, BRAND_DARK, PAGE_WIDTH - MARGIN - 114f,
                    top - 10f, "ACCOUNT STATEMENT");
            textRight(target.stream, regular, 6.5f, MUTED, PAGE_WIDTH - MARGIN, top - 28f,
                    DATE.format(model.periodStart()) + " - " + DATE.format(model.periodEnd()));
            target.stream.setStrokingColor(BORDER);
            target.stream.moveTo(MARGIN, top - 42f);
            target.stream.lineTo(PAGE_WIDTH - MARGIN, top - 42f);
            target.stream.stroke();
            target.y = top - 63f;
        }

        private void drawFooters() throws IOException {
            int pages = document.getNumberOfPages();
            for (int index = 0; index < pages; index++) {
                PDPage page = document.getPage(index);
                try (PDPageContentStream stream = new PDPageContentStream(document, page,
                        AppendMode.APPEND, true, true)) {
                    stream.setStrokingColor(BRAND_DARK);
                    stream.setLineWidth(.8f);
                    stream.moveTo(MARGIN, 35f);
                    stream.lineTo(PAGE_WIDTH - MARGIN, 35f);
                    stream.stroke();
                    text(stream, semiBold, 6.5f, BRAND_DARK, MARGIN, 21f, "MoneyBags");
                    textCentered(stream, regular, 5.8f, MUTED, PAGE_WIDTH / 2f, 21f,
                            "Statement ID: " + model.statementId());
                    textRight(stream, semiBold, 6.2f, BRAND_DARK, PAGE_WIDTH - MARGIN, 21f,
                            "Page " + (index + 1) + " of " + pages);
                }
            }
        }

        private void sectionTitle(PDPageContentStream stream, float y, String value) throws IOException {
            roundedFill(stream, MARGIN, y - 11f, 22f, 22f, 6f, BLUSH);
            stream.setStrokingColor(BRAND_DARK);
            stream.setLineWidth(1f);
            for (int index = 0; index < 3; index++) {
                stream.moveTo(MARGIN + 6f, y + 5f - (index * 5f));
                stream.lineTo(MARGIN + 16f, y + 5f - (index * 5f));
                stream.stroke();
            }
            text(stream, semiBold, 8.2f, BRAND_DARK, MARGIN + 31f, y - 3f, value);
        }

        private void labelValue(PDPageContentStream stream, float x, float y,
                                String label, String value, float labelWidth) throws IOException {
            text(stream, regular, 6.5f, MUTED, x, y, label);
            text(stream, semiBold, 7f, TEXT, x + labelWidth, y, safe(value));
        }

        private void closeState() throws IOException {
            if (state != null && state.stream != null) {
                state.stream.close();
                state.stream = null;
            }
        }

        private String moneyWithCurrency(BigDecimal value) {
            return safe(model.currency()) + " " + amount(value);
        }
    }

    private static final class PageState {
        private PDPageContentStream stream;
        private float y;

        private PageState(PDPageContentStream stream, float y) {
            this.stream = stream;
            this.y = y;
        }
    }

    private static void drawBrandMark(PDPageContentStream stream, float x, float y, float size) throws IOException {
        roundedFill(stream, x, y, size, size, 7f, WHITE);
        float centerX = x + (size / 2f);
        float centerY = y + (size / 2f);
        float outer = size * .27f;
        float inner = size * .08f;
        stream.setNonStrokingColor(BRAND_DARK);
        stream.moveTo(centerX, centerY + outer);
        stream.lineTo(centerX + inner, centerY + inner);
        stream.lineTo(centerX + outer, centerY);
        stream.lineTo(centerX + inner, centerY - inner);
        stream.lineTo(centerX, centerY - outer);
        stream.lineTo(centerX - inner, centerY - inner);
        stream.lineTo(centerX - outer, centerY);
        stream.lineTo(centerX - inner, centerY + inner);
        stream.closePath();
        stream.fill();
    }

    private static void drawLines(PDPageContentStream stream, PDFont font, float size, Color color,
                                  float x, float y, float leading, List<String> lines) throws IOException {
        for (int index = 0; index < lines.size(); index++) {
            text(stream, font, size, color, x, y - (index * leading), lines.get(index));
        }
    }

    private static void text(PDPageContentStream stream, PDFont font, float size, Color color,
                             float x, float y, String value) throws IOException {
        if (font == null || value == null || value.isEmpty()) return;
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(color);
        stream.newLineAtOffset(x, y);
        stream.showText(safe(value));
        stream.endText();
    }

    private static void textRight(PDPageContentStream stream, PDFont font, float size, Color color,
                                  float right, float y, String value) throws IOException {
        String safeValue = safe(value);
        text(stream, font, size, color, right - width(font, size, safeValue), y, safeValue);
    }

    private static void textCentered(PDPageContentStream stream, PDFont font, float size, Color color,
                                     float center, float y, String value) throws IOException {
        String safeValue = safe(value);
        text(stream, font, size, color, center - (width(font, size, safeValue) / 2f), y, safeValue);
    }

    private static List<String> wrap(String value, PDFont font, float size,
                                     float maxWidth, int maxLines) throws IOException {
        String normalized = safe(value).trim();
        if (normalized.isEmpty()) return List.of("-");
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : normalized.split("\\s+")) {
            if (width(font, size, word) > maxWidth) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                splitLongWord(result, word, font, size, maxWidth);
            } else {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (width(font, size, candidate) <= maxWidth) {
                    current.setLength(0);
                    current.append(candidate);
                } else {
                    result.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                }
            }
        }
        if (!current.isEmpty()) result.add(current.toString());
        if (result.size() <= maxLines) return result;
        List<String> clipped = new ArrayList<>(result.subList(0, maxLines));
        String last = clipped.getLast();
        while (!last.isEmpty() && width(font, size, last + "...") > maxWidth) {
            last = last.substring(0, last.length() - 1);
        }
        clipped.set(maxLines - 1, last + "...");
        return clipped;
    }

    private static void splitLongWord(List<String> target, String word, PDFont font,
                                      float size, float maxWidth) throws IOException {
        StringBuilder part = new StringBuilder();
        for (int index = 0; index < word.length(); index++) {
            String candidate = part.toString() + word.charAt(index);
            if (!part.isEmpty() && width(font, size, candidate) > maxWidth) {
                target.add(part.toString());
                part.setLength(0);
            }
            part.append(word.charAt(index));
        }
        if (!part.isEmpty()) target.add(part.toString());
    }

    private static String reference(StatementLineView line) {
        String primary = firstNonBlank(line.paymentId(), line.transactionId(), "-");
        return line.journalNumber() == null || line.journalNumber().isBlank()
                ? primary : primary + " / " + line.journalNumber();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "-";
    }

    private static String tableMoney(BigDecimal value) {
        return value == null || value.signum() == 0 ? "-" : amount(value);
    }

    private static String amount(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        format.setGroupingUsed(true);
        return format.format(value == null ? BigDecimal.ZERO : value);
    }

    private static float width(PDFont font, float size, String value) throws IOException {
        return font.getStringWidth(safe(value)) * size / 1000f;
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.replace('\u2010', '-').replace('\u2011', '-').replace('\u2012', '-')
                .replace('\u2013', '-').replace('\u2014', '-').replace("·", " - ")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").replaceAll("[\\r\\n\\t]+", " ");
    }

    private static Color color(String hex) {
        return new Color(Integer.parseInt(hex, 16));
    }

    private static Color interpolate(Color start, Color end, float ratio) {
        int red = Math.round(start.getRed() + ((end.getRed() - start.getRed()) * ratio));
        int green = Math.round(start.getGreen() + ((end.getGreen() - start.getGreen()) * ratio));
        int blue = Math.round(start.getBlue() + ((end.getBlue() - start.getBlue()) * ratio));
        return new Color(red, green, blue);
    }

    private static void roundedFillAndStroke(PDPageContentStream stream, float x, float y, float width,
                                             float height, float radius, Color fill, Color stroke) throws IOException {
        roundedPath(stream, x, y, width, height, radius);
        stream.setNonStrokingColor(fill);
        stream.setStrokingColor(stroke);
        stream.setLineWidth(.7f);
        stream.fillAndStroke();
    }

    private static void roundedFill(PDPageContentStream stream, float x, float y, float width,
                                    float height, float radius, Color fill) throws IOException {
        roundedPath(stream, x, y, width, height, radius);
        stream.setNonStrokingColor(fill);
        stream.fill();
    }

    private static void roundedPath(PDPageContentStream stream, float x, float y, float width,
                                    float height, float radius) throws IOException {
        float r = Math.min(radius, Math.min(width, height) / 2f);
        float k = .55228475f;
        stream.moveTo(x + r, y);
        stream.lineTo(x + width - r, y);
        stream.curveTo(x + width - r + (r * k), y, x + width, y + r - (r * k), x + width, y + r);
        stream.lineTo(x + width, y + height - r);
        stream.curveTo(x + width, y + height - r + (r * k), x + width - r + (r * k), y + height,
                x + width - r, y + height);
        stream.lineTo(x + r, y + height);
        stream.curveTo(x + r - (r * k), y + height, x, y + height - r + (r * k), x, y + height - r);
        stream.lineTo(x, y + r);
        stream.curveTo(x, y + r - (r * k), x + r - (r * k), y, x + r, y);
        stream.closePath();
    }
}
