package vn.evnqt1.helpdesk.core;

import java.util.ArrayList;
import java.util.List;

/** Đọc CSV (dấu phẩy, chấm phẩy hoặc Tab; có hỗ trợ ngoặc kép) thành lưới chuỗi. */
public final class CsvReader {
    private CsvReader() {}

    public static String[][] parse(String text) {
        if (text == null) return new String[0][0];
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        char delim = detectDelimiter(text);
        List<String[]> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int max = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') { if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; } else quoted = false; }
                else cell.append(c);
            } else if (c == '"') quoted = true;
            else if (c == delim) { row.add(cell.toString().trim()); cell.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString().trim()); cell.setLength(0);
                max = Math.max(max, row.size()); rows.add(row.toArray(new String[0])); row = new ArrayList<>();
            } else cell.append(c);
        }
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString().trim()); max = Math.max(max, row.size()); rows.add(row.toArray(new String[0])); }
        String[][] g = new String[rows.size()][max];
        for (int r = 0; r < g.length; r++) for (int c = 0; c < max; c++) g[r][c] = c < rows.get(r).length ? rows.get(r)[c] : "";
        return g;
    }

    private static char detectDelimiter(String t) {
        int firstLines = 0, comma = 0, semi = 0, tab = 0;
        for (int i = 0; i < t.length() && firstLines < 8; i++) {
            char c = t.charAt(i);
            if (c == ',') comma++; else if (c == ';') semi++; else if (c == '\t') tab++; else if (c == '\n') firstLines++;
        }
        return tab > comma && tab > semi ? '\t' : semi > comma ? ';' : ',';
    }
}
