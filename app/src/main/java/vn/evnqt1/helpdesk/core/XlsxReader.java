package vn.evnqt1.helpdesk.core;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Đọc tệp .xlsx hoàn toàn offline, không thư viện ngoài (zip + SAX). Trả về từng sheet dưới dạng lưới chuỗi.
 * Chỉ đọc giá trị hiển thị (chuỗi, số, công thức đã tính); không thực thi macro hay tải tài nguyên ngoài.
 */
public final class XlsxReader {
    private XlsxReader() {}

    private static final int MAX_ENTRY_BYTES = 25 * 1024 * 1024, MAX_ROWS = 3000, MAX_COLS = 300;

    public static List<String[][]> read(InputStream in) throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        Map<Integer, String> sheetNames = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String n = e.getName();
                if (n.equals("xl/sharedStrings.xml") || n.startsWith("xl/worksheets/sheet")) files.put(n, readLimited(zip));
            }
        }
        if (files.isEmpty()) throw new IllegalArgumentException("Tệp không phải Excel .xlsx hợp lệ");
        List<String> shared = new ArrayList<>();
        byte[] ss = files.get("xl/sharedStrings.xml");
        if (ss != null) parse(ss, new SharedStringsHandler(shared));
        for (String n : files.keySet()) {
            if (!n.matches("xl/worksheets/sheet\\d+\\.xml")) continue;
            sheetNames.put(Integer.parseInt(n.replaceAll("\\D+", "")), n);
        }
        List<String[][]> out = new ArrayList<>();
        for (String name : sheetNames.values()) {
            SheetHandler h = new SheetHandler(shared);
            parse(files.get(name), h);
            out.add(h.toGrid());
        }
        return out;
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[16384]; int n; long total = 0;
        while ((n = in.read(b)) != -1) { total += n; if (total > MAX_ENTRY_BYTES) throw new IOException("Tệp Excel quá lớn"); out.write(b, 0, n); }
        return out.toByteArray();
    }

    private static void parse(byte[] xml, DefaultHandler h) throws Exception {
        SAXParserFactory f = SAXParserFactory.newInstance();
        f.setNamespaceAware(false);
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) { }
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) { }
        SAXParser p = f.newSAXParser();
        InputSource src = new InputSource(new ByteArrayInputStream(xml));
        p.parse(src, h);
    }

    private static final class SharedStringsHandler extends DefaultHandler {
        private final List<String> out;
        private StringBuilder cur; private boolean inT, inPhonetic;
        SharedStringsHandler(List<String> out) { this.out = out; }
        @Override public void startElement(String u, String l, String q, Attributes a) {
            if (q.equals("si")) cur = new StringBuilder();
            else if (q.equals("rPh")) inPhonetic = true;
            else if (q.equals("t") && !inPhonetic) inT = true;
        }
        @Override public void characters(char[] ch, int s, int len) { if (inT && cur != null) cur.append(ch, s, len); }
        @Override public void endElement(String u, String l, String q) {
            if (q.equals("t")) inT = false;
            else if (q.equals("rPh")) inPhonetic = false;
            else if (q.equals("si")) { out.add(cur == null ? "" : cur.toString()); cur = null; }
        }
    }

    private static final class SheetHandler extends DefaultHandler {
        private final List<String> shared;
        private final Map<Long, String> cells = new HashMap<>();
        private int maxRow = -1, maxCol = -1;
        private String ref, type; private StringBuilder text = new StringBuilder(); private boolean inV, inT;
        SheetHandler(List<String> shared) { this.shared = shared; }
        @Override public void startElement(String u, String l, String q, Attributes a) {
            if (q.equals("c")) { ref = a.getValue("r"); type = a.getValue("t"); text.setLength(0); }
            else if (q.equals("v")) inV = true;
            else if (q.equals("t")) inT = true;
        }
        @Override public void characters(char[] ch, int s, int len) { if ((inV || inT) && ref != null) text.append(ch, s, len); }
        @Override public void endElement(String u, String l, String q) {
            if (q.equals("v")) inV = false;
            else if (q.equals("t")) inT = false;
            else if (q.equals("c")) {
                if (ref != null) {
                    String v = text.toString();
                    String value;
                    if ("s".equals(type)) { try { value = shared.get(Integer.parseInt(v.trim())); } catch (RuntimeException e) { value = ""; } }
                    else if ("b".equals(type)) value = "1".equals(v.trim()) ? "TRUE" : "FALSE";
                    else if (type == null || "n".equals(type)) value = number(v);
                    else value = v;
                    int[] rc = cellRef(ref);
                    if (rc != null && rc[0] < MAX_ROWS && rc[1] < MAX_COLS && !value.trim().isEmpty()) {
                        cells.put(((long) rc[0] << 20) | rc[1], value.trim());
                        maxRow = Math.max(maxRow, rc[0]); maxCol = Math.max(maxCol, rc[1]);
                    }
                }
                ref = null; type = null;
            }
        }
        String[][] toGrid() {
            String[][] g = new String[maxRow + 1][maxCol + 1];
            for (String[] row : g) java.util.Arrays.fill(row, "");
            for (Map.Entry<Long, String> e : cells.entrySet()) g[(int) (e.getKey() >> 20)][(int) (e.getKey() & 0xFFFFF)] = e.getValue();
            return g;
        }
    }

    /** "1" và "1.0" đều thành "1"; số thực giữ nguyên. */
    private static String number(String v) {
        String t = v.trim();
        if (t.isEmpty()) return "";
        try { double d = Double.parseDouble(t); if (d == Math.rint(d) && Math.abs(d) < 1e12) return String.valueOf((long) d); } catch (NumberFormatException ignored) { }
        return t;
    }

    /** "AE12" -> {11, 30} (hàng, cột, đều bắt đầu từ 0). */
    static int[] cellRef(String ref) {
        int i = 0, col = 0;
        while (i < ref.length() && Character.isLetter(ref.charAt(i))) { col = col * 26 + (Character.toUpperCase(ref.charAt(i)) - 'A' + 1); i++; }
        if (i == 0 || i == ref.length()) return null;
        try { return new int[]{Integer.parseInt(ref.substring(i)) - 1, col - 1}; } catch (NumberFormatException e) { return null; }
    }
}
