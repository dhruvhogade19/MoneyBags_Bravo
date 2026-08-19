package com.moneybags.billing;

import com.moneybags.billing.BillGenerationApplication.BillLineResponse;
import com.moneybags.billing.BillGenerationApplication.BillResponse;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class StatementPdfRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM uuuu");
    private static final byte[] PASSWORD_PADDING = new byte[]{
            0x28, (byte) 0xBF, 0x4E, 0x5E, 0x4E, 0x75, (byte) 0x8A, 0x41,
            0x64, 0x00, 0x4E, 0x56, (byte) 0xFF, (byte) 0xFA, 0x01, 0x08,
            0x2E, 0x2E, 0x00, (byte) 0xB6, (byte) 0xD0, 0x68, 0x3E, (byte) 0x80,
            0x2F, 0x0C, (byte) 0xA9, (byte) 0xFE, 0x64, 0x53, 0x69, 0x7A
    };

    public byte[] render(BillResponse bill) {
        try {
            byte[] fileId = Arrays.copyOf(md5((bill.billId() + bill.generatedAt()).getBytes(StandardCharsets.UTF_8)), 16);
            byte[] owner = ownerEntry(bill.pdfPassword());
            int permissions = -4;
            byte[] fileKey = encryptionKey(bill.pdfPassword(), owner, permissions, fileId);
            byte[] user = rc4(fileKey, PASSWORD_PADDING);

            List<byte[]> objects = new ArrayList<>();
            objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));
            objects.add(bytes("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"));
            objects.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>"));
            objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"));
            objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>"));
            byte[] encryptedStream = encryptObject(fileKey, 6, bytes(content(bill)));
            objects.add(concat(bytes("<< /Length " + encryptedStream.length + " >>\nstream\n"), encryptedStream, bytes("\nendstream")));
            objects.add(bytes("<< /Filter /Standard /V 1 /R 2 /Length 40 /O <" + hex(owner) + "> /U <" + hex(user) + "> /P " + permissions + " >>"));
            return writePdf(objects, fileId);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create billing statement PDF", exception);
        }
    }

    private static String content(BillResponse bill) {
        StringBuilder content = new StringBuilder();
        text(content, "F2", 22, 48, 790, "MoneyBags billing statement");
        text(content, "F1", 10, 48, 770, "Statement reference: " + bill.billId());
        text(content, "F1", 10, 48, 755, "Account: " + mask(bill.accountId()));
        text(content, "F1", 10, 48, 740, "Billing period: " + DATE.format(bill.periodStart()) + " - " + DATE.format(bill.periodEnd()));
        text(content, "F2", 15, 48, 710, "Total amount due: " + amount(bill.totalAmountDue(), bill.currency()));
        text(content, "F1", 11, 48, 690, "Minimum due: " + amount(bill.minimumAmountDue(), bill.currency()) + "    Due: " + DATE.format(bill.paymentDueDate()));
        text(content, "F2", 13, 48, 655, "Activity");
        int y = 630;
        for (BillLineResponse line : bill.lines()) {
            if (y < 70) break;
            String label = DATE.format(line.occurredAt().toLocalDate()) + "  " + line.lineType().replace('_', ' ') + "  " + line.description();
            text(content, "F1", 9, 48, y, truncate(label, 72));
            text(content, "F2", 9, 440, y, amount(line.amount(), bill.currency()));
            y -= 22;
        }
        text(content, "F1", 8, 48, 42, "Generated " + DATE.format(bill.generatedAt().toLocalDate()) + " | Password protected by MoneyBags");
        return content.toString();
    }

    private static byte[] writePdf(List<byte[]> objects, byte[] fileId) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(bytes("%PDF-1.4\n%MBGS\n"));
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(output.size());
            output.write(bytes((index + 1) + " 0 obj\n"));
            output.write(objects.get(index));
            output.write(bytes("\nendobj\n"));
        }
        int xref = output.size();
        output.write(bytes("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n"));
        for (int index = 1; index < offsets.size(); index++) {
            output.write(bytes(String.format("%010d 00000 n \n", offsets.get(index))));
        }
        String id = hex(fileId);
        output.write(bytes("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R /Encrypt 7 0 R /ID [<" + id + "><" + id + ">] >>\n"));
        output.write(bytes("startxref\n" + xref + "\n%%EOF\n"));
        return output.toByteArray();
    }

    private static byte[] ownerEntry(String password) throws Exception {
        byte[] ownerKey = Arrays.copyOf(md5(pad("MoneyBags-Statement-Owner")), 5);
        return rc4(ownerKey, pad(password));
    }

    private static byte[] encryptionKey(String password, byte[] owner, int permissions, byte[] fileId) throws Exception {
        ByteArrayOutputStream input = new ByteArrayOutputStream();
        input.write(pad(password));
        input.write(owner);
        input.write(new byte[]{(byte) permissions, (byte) (permissions >> 8), (byte) (permissions >> 16), (byte) (permissions >> 24)});
        input.write(fileId);
        return Arrays.copyOf(md5(input.toByteArray()), 5);
    }

    private static byte[] encryptObject(byte[] fileKey, int objectNumber, byte[] value) throws Exception {
        byte[] seed = concat(fileKey, new byte[]{(byte) objectNumber, (byte) (objectNumber >> 8), (byte) (objectNumber >> 16), 0, 0});
        byte[] key = Arrays.copyOf(md5(seed), Math.min(fileKey.length + 5, 16));
        return rc4(key, value);
    }

    private static byte[] rc4(byte[] key, byte[] value) throws Exception {
        Cipher cipher = Cipher.getInstance("RC4");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "RC4"));
        return cipher.doFinal(value);
    }

    private static byte[] pad(String password) {
        byte[] source = password.getBytes(StandardCharsets.ISO_8859_1);
        byte[] result = new byte[32];
        int length = Math.min(source.length, 32);
        System.arraycopy(source, 0, result, 0, length);
        if (length < 32) System.arraycopy(PASSWORD_PADDING, 0, result, length, 32 - length);
        return result;
    }

    private static byte[] md5(byte[] input) throws Exception {
        return MessageDigest.getInstance("MD5").digest(input);
    }

    private static void text(StringBuilder content, String font, int size, int x, int y, String value) {
        content.append("BT /").append(font).append(' ').append(size).append(" Tf ")
                .append(x).append(' ').append(y).append(" Td (").append(escape(value)).append(") Tj ET\n");
    }

    private static String escape(String value) {
        return value.replaceAll("[^\\x20-\\x7E]", " ").replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static String amount(BigDecimal value, String currency) {
        return currency + " " + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String mask(String account) {
        String suffix = account.length() <= 4 ? account : account.substring(account.length() - 4);
        return "XXXXXXXX" + suffix;
    }

    private static String truncate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length - 3) + "...";
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] concat(byte[]... values) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] value : values) output.write(value);
        return output.toByteArray();
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format("%02X", item));
        return result.toString();
    }
}
