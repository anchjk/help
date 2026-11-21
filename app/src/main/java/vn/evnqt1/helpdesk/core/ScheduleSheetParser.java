package vn.evnqt1.helpdesk.core;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc lịch trực ca từ bảng tính (Excel/CSV) thành {@link ScheduleDraft} mà không cần AI hay mạng.
 * Nhận diện theo nhãn, không cố định ô: hàng "Ngày" (1,2,3…) kèm các hàng "Thứ", "Sáng", "Chiều", "Đêm";
 * mục "Kíp A…E" kèm danh sách tên bên dưới; dòng "Từ dd/MM/yyyy đến …" để biết tháng. Cũng hiểu bố cục xoay ngang/dọc.
 */
public final class ScheduleSheetParser {
    private ScheduleSheetParser() {}

    private static final Pattern DATE = Pattern.compile("(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s*/\\s*(\\d{4})");
    private static final Pattern MONTH_YEAR = Pattern.compile("thang\\s*(\\d{1,2})\\s*/\\s*(\\d{4})");
    private static final Pattern CREW_HEAD = Pattern.compile("^kip\\s*([a-e])\\b\\s*[:\\-–]?\\s*(.*)$");
    private static final Pattern TIME_RANGE = Pattern.compile("ca\\s*(sang|chieu|toi|dem)\\s*(?:tu)?\\s*(\\d{1,2})\\s*[:h.]\\s*(\\d{2})?\\s*(?:toi|den|-|~)\\s*(\\d{1,2})\\s*[:h.]\\s*(\\d{2})?");

    /** Thử lần lượt từng sheet, lấy sheet đầu tiên có bảng lịch ca. */
    public static ScheduleDraft parseSheets(List<String[][]> sheets, int fallbackYear, int fallbackMonth, String[] defStart, String[] defEnd) {
        IllegalArgumentException last = null;
        for (String[][] g : sheets) {
            try { return parse(g, fallbackYear, fallbackMonth, defStart, defEnd); }
            catch (IllegalArgumentException e) { last = e; }
        }
        throw last != null ? last : new IllegalArgumentException("Tệp không có dữ liệu.");
    }

    public static ScheduleDraft parse(String[][] g, int fallbackYear, int fallbackMonth, String[] defStart, String[] defEnd) {
        if (g == null || g.length == 0) throw new IllegalArgumentException("Tệp không có dữ liệu.");
        ScheduleDraft d = new ScheduleDraft();
        if (defStart != null && defEnd != null) for (int s = 0; s < 3; s++) { d.slotStart[s] = defStart[s]; d.slotEnd[s] = defEnd[s]; }

        // 1) Tháng/năm
        LocalDate from = null; int monthOnly = 0, yearOnly = 0;
        for (String[] row : g) for (String cell : row) {
            if (cell == null || cell.isEmpty()) continue;
            String n = TextNormalizer.normalize(cell);
            if (from == null && n.contains("tu") && DATE.matcher(n).find()) from = firstDate(n);
            if (monthOnly == 0) { Matcher m = MONTH_YEAR.matcher(n); if (m.find()) { monthOnly = Integer.parseInt(m.group(1)); yearOnly = Integer.parseInt(m.group(2)); } }
        }
        if (from != null) { d.year = from.getYear(); d.month = from.getMonthValue(); }
        else if (monthOnly >= 1 && monthOnly <= 12) { d.year = yearOnly; d.month = monthOnly; d.notes.add("Tháng/năm lấy từ tiêu đề: " + monthOnly + "/" + yearOnly + "."); }
        else { d.year = fallbackYear; d.month = fallbackMonth; d.notes.add("Tệp không ghi tháng/năm; đang dùng " + fallbackMonth + "/" + fallbackYear + ". Kiểm tra lại ở bước xem lại."); }

        // 2) Bảng ngày – ca
        int maxDay = loadHorizontal(g, d);
        if (maxDay == 0) maxDay = loadVertical(g, d);
        if (maxDay == 0) throw new IllegalArgumentException("Không tìm thấy bảng lịch ca trong tệp. Cần có hàng \"Ngày\" (1, 2, 3…) cùng các hàng \"Sáng\", \"Chiều\", \"Đêm\". Hãy tải tệp mẫu trong màn hình Lịch ca để đối chiếu.");

        // 3) Nhân sự các kíp
        loadRosters(g, d);

        // 4) Giờ ca ghi trong tệp (nếu có)
        loadTimes(g, d);
        return d;
    }

    private static LocalDate firstDate(String n) {
        Matcher m = DATE.matcher(n);
        if (!m.find()) return null;
        try { return LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1))); }
        catch (RuntimeException e) { return null; }
    }

    private static String cell(String[][] g, int r, int c) { return r >= 0 && r < g.length && c >= 0 && c < g[r].length && g[r][c] != null ? g[r][c].trim() : ""; }

    private static int asDay(String s) {
        if (!s.matches("\\d{1,2}(\\.0)?")) return 0;
        int v = Integer.parseInt(s.replace(".0", ""));
        return v >= 1 && v <= 31 ? v : 0;
    }

    private static int slotOf(String label) {
        String n = TextNormalizer.normalize(label);
        if (n.isEmpty() || n.length() > 24) return -1;
        if (n.contains("sang")) return 0;
        if (n.contains("chieu")) return 1;
        if (n.contains("dem") || n.equals("toi") || n.contains("ca toi")) return 2;
        return -1;
    }

    private static void applyDay(ScheduleDraft d, int day, int slot, String value) {
        if (day > d.days.length) d.resizeDays(day);
        d.days[day - 1].crew[slot] = ScheduleRules.normalizeCrew(value);
    }

    /** Ngày nằm ngang (mỗi cột một ngày). Trả về số ngày lớn nhất đọc được, 0 nếu không đúng bố cục. */
    private static int loadHorizontal(String[][] g, ScheduleDraft d) {
        for (int r = 0; r < g.length; r++) for (int c = 0; c < g[r].length; c++) {
            if (!TextNormalizer.normalize(cell(g, r, c)).equals("ngay")) continue;
            Map<Integer, Integer> dayCol = new LinkedHashMap<>();
            for (int cc = c + 1; cc < g[r].length; cc++) { int v = asDay(cell(g, r, cc)); if (v > 0) dayCol.put(v, cc); }
            if (dayCol.size() < 5) continue;
            int[] slotRow = {-1, -1, -1}; int weekdayRow = -1;
            for (int rr = r + 1; rr <= r + 12 && rr < g.length; rr++) {
                String label = cell(g, rr, c);
                int s = slotOf(label);
                if (s >= 0 && slotRow[s] < 0) slotRow[s] = rr;
                else if (TextNormalizer.normalize(label).equals("thu")) weekdayRow = rr;
            }
            if (slotRow[0] < 0 && slotRow[1] < 0 && slotRow[2] < 0) continue;
            int len = YearMonth.of(Math.max(2000, d.year), Math.max(1, Math.min(12, d.month))).lengthOfMonth();
            d.resizeDays(len);
            int maxDay = 0;
            for (Map.Entry<Integer, Integer> e : dayCol.entrySet()) {
                int day = e.getKey(), col = e.getValue();
                maxDay = Math.max(maxDay, day);
                if (day > d.days.length) d.resizeDays(day);
                if (weekdayRow >= 0) d.days[day - 1].weekday = ScheduleRules.normalizeWeekday(cell(g, weekdayRow, col));
                for (int s = 0; s < 3; s++) if (slotRow[s] >= 0) applyDay(d, day, s, cell(g, slotRow[s], col));
            }
            return maxDay;
        }
        return 0;
    }

    /** Ngày nằm dọc (mỗi hàng một ngày, cột Ngày/Thứ/Sáng/Chiều/Đêm). */
    private static int loadVertical(String[][] g, ScheduleDraft d) {
        for (int r = 0; r < g.length; r++) {
            int dayC = -1, wdC = -1; int[] slotC = {-1, -1, -1};
            for (int c = 0; c < g[r].length; c++) {
                String n = TextNormalizer.normalize(cell(g, r, c));
                if (n.equals("ngay")) dayC = c; else if (n.equals("thu")) wdC = c;
                else { int s = slotOf(cell(g, r, c)); if (s >= 0 && slotC[s] < 0) slotC[s] = c; }
            }
            int slotCount = (slotC[0] >= 0 ? 1 : 0) + (slotC[1] >= 0 ? 1 : 0) + (slotC[2] >= 0 ? 1 : 0);
            if (dayC < 0 || slotCount < 2) continue;
            int len = YearMonth.of(Math.max(2000, d.year), Math.max(1, Math.min(12, d.month))).lengthOfMonth();
            d.resizeDays(len);
            int maxDay = 0;
            for (int rr = r + 1; rr < g.length; rr++) {
                int day = asDay(cell(g, rr, dayC));
                if (day == 0) { if (maxDay > 0) break; else continue; }
                maxDay = Math.max(maxDay, day);
                if (day > d.days.length) d.resizeDays(day);
                if (wdC >= 0) d.days[day - 1].weekday = ScheduleRules.normalizeWeekday(cell(g, rr, wdC));
                for (int s = 0; s < 3; s++) if (slotC[s] >= 0) applyDay(d, day, s, cell(g, rr, slotC[s]));
            }
            if (maxDay > 0) return maxDay;
        }
        return 0;
    }

    private static void loadRosters(String[][] g, ScheduleDraft d) {
        for (int r = 0; r < g.length; r++) for (int c = 0; c < g[r].length; c++) {
            String raw = cell(g, r, c);
            if (raw.isEmpty()) continue;
            Matcher m = CREW_HEAD.matcher(TextNormalizer.normalize(raw));
            if (!m.matches()) continue;
            String crew = m.group(1).toUpperCase();
            List<String> names = new ArrayList<>();
            // Tên ngay trong ô tiêu đề: "Kíp A: Nguyễn A, Trần B"
            int colon = indexOfSeparator(raw);
            if (colon >= 0) addNames(names, raw.substring(colon + 1));
            // Tên ở các ô bên dưới cùng cột, dừng ở ô trống hoặc tiêu đề kíp khác
            if (names.isEmpty()) {
                for (int rr = r + 1; rr < g.length; rr++) {
                    String v = cell(g, rr, c);
                    if (v.isEmpty() || CREW_HEAD.matcher(TextNormalizer.normalize(v)).matches()) break;
                    addNames(names, v);
                }
            }
            // Bố cục "Kíp A | tên | tên | tên" trên cùng một hàng
            if (names.isEmpty()) {
                for (int cc = c + 1; cc < g[r].length; cc++) {
                    String v = cell(g, r, cc);
                    if (v.isEmpty()) continue;
                    if (CREW_HEAD.matcher(TextNormalizer.normalize(v)).matches()) break;
                    addNames(names, v);
                }
            }
            if (!names.isEmpty() && d.rosters.get(crew).isEmpty()) d.rosters.put(crew, String.join(", ", names));
        }
    }

    private static int indexOfSeparator(String raw) {
        int colon = raw.indexOf(':');
        return colon > 0 && colon < 12 ? colon : -1;
    }

    private static void addNames(List<String> out, String text) {
        for (String part : text.split("[\\n;,]+")) {
            String n = part.replaceFirst("^\\s*\\d+\\s*[.)\\-]\\s*", "").trim();
            if (!n.isEmpty() && !n.matches("\\d+")) out.add(n);
        }
    }

    private static void loadTimes(String[][] g, ScheduleDraft d) {
        boolean[] found = new boolean[3];
        for (String[] row : g) for (String cellText : row) {
            if (cellText == null || cellText.isEmpty()) continue;
            Matcher m = TIME_RANGE.matcher(TextNormalizer.normalize(cellText));
            while (m.find()) {
                int s = m.group(1).equals("sang") ? 0 : m.group(1).equals("chieu") ? 1 : 2;
                if (found[s]) continue;
                String st = hhmm(m.group(2), m.group(3)), en = hhmm(m.group(4), m.group(5));
                if (st != null && en != null) { d.slotStart[s] = st; d.slotEnd[s] = en; found[s] = true; }
            }
        }
        if (found[0] || found[1] || found[2]) d.notes.add("Giờ ca được đọc từ tệp: Sáng " + d.slotStart[0] + "–" + d.slotEnd[0] + ", Chiều " + d.slotStart[1] + "–" + d.slotEnd[1] + ", Đêm " + d.slotStart[2] + "–" + d.slotEnd[2] + ".");
    }

    private static String hhmm(String h, String m) {
        try {
            int hh = Integer.parseInt(h), mm = m == null ? 0 : Integer.parseInt(m);
            if (hh < 0 || hh > 24 || mm < 0 || mm > 59) return null;
            return String.format("%02d:%02d", hh % 24, mm);
        } catch (NumberFormatException e) { return null; }
    }
}
