package com.pmis.docket.server.seed;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Generates small but real files (PDF, PNG, text) so the demo data opens in normal apps. */
final class SampleFiles {
    private SampleFiles() { }

    static byte[] text(String... lines) {
        return String.join("\r\n", lines).getBytes(StandardCharsets.UTF_8);
    }

    /** A valid one-page A4 PDF with a title and some lines of text (Helvetica, ASCII only). */
    static byte[] pdf(String title, String... lines) {
        StringBuilder content = new StringBuilder();
        content.append("BT /F1 22 Tf 60 770 Td (").append(esc(title)).append(") Tj ET\n");
        content.append("0.23 0.36 1 rg 60 750 120 4 re f\n");
        int y = 715;
        for (String l : lines) {
            content.append("BT /F1 11 Tf 60 ").append(y).append(" Td (").append(esc(l)).append(") Tj ET\n");
            y -= 18;
        }
        content.append("BT /F1 8 Tf 60 40 Td (PMIS Docket sample document) Tj ET\n");
        byte[] stream = content.toString().getBytes(StandardCharsets.ISO_8859_1);

        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>");
        objects.add(null); // stream object, written separately
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int[] offsets = new int[objects.size() + 1];
        write(out, "%PDF-1.4\n");
        for (int i = 0; i < objects.size(); i++) {
            offsets[i + 1] = out.size();
            write(out, (i + 1) + " 0 obj\n");
            if (objects.get(i) == null) {
                write(out, "<< /Length " + stream.length + " >>\nstream\n");
                out.writeBytes(stream);
                write(out, "\nendstream");
            } else {
                write(out, objects.get(i));
            }
            write(out, "\nendobj\n");
        }
        int xref = out.size();
        write(out, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int i = 1; i <= objects.size(); i++) write(out, String.format("%010d 00000 n \n", offsets[i]));
        write(out, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    /** A PNG picture: soft two-colour background with simple hills and a label. */
    static byte[] png(int w, int h, Color a, Color b, String label) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, a, w, h, b));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(255, 255, 255, 200));
        g.fillOval(w * 7 / 10, h / 6, h / 5, h / 5);
        g.setColor(new Color(255, 255, 255, 120));
        g.fillPolygon(new int[]{0, w / 4, w / 2, w * 2 / 3, w, w, 0}, new int[]{h, h / 2, h * 3 / 4, h * 9 / 20, h, h, h}, 7);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(14, h / 14)));
        g.drawString(label, w / 20, h - h / 14);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** A short WAV recording: a soft two-tone chime, so audio playback can be tested. */
    static byte[] wav(double seconds) {
        int rate = 22050;
        int n = (int) (rate * seconds);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, "RIFF");
        writeInt(out, 36 + n * 2);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeInt(out, 16);
        writeShort(out, 1);
        writeShort(out, 1);
        writeInt(out, rate);
        writeInt(out, rate * 2);
        writeShort(out, 2);
        writeShort(out, 16);
        writeAscii(out, "data");
        writeInt(out, n * 2);
        for (int i = 0; i < n; i++) {
            double t = (double) i / rate;
            double f = t < seconds / 2 ? 660 : 880;
            double env = Math.min(1, t * 20) * Math.exp(-3 * (t % (seconds / 2)));
            writeShort(out, (int) (Math.sin(2 * Math.PI * f * t) * env * 9000));
        }
        return out.toByteArray();
    }

    /** A ZIP with the given text files. */
    static byte[] zip(java.util.Map<String, String> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(out)) {
            for (java.util.Map.Entry<String, String> e : entries.entrySet()) {
                z.putNextEntry(new java.util.zip.ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream out, String s) { out.writeBytes(s.getBytes(StandardCharsets.US_ASCII)); }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }

    private static void writeShort(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') b.append('\\').append(c);
            else if (c < 256) b.append(c);
            else b.append('-');
        }
        return b.toString();
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
    }
}
