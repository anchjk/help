package vn.evnqt1.helpdesk.core;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Quy tắc thuần Java cho lịch ca: kiểm tra chéo với lịch thật, chuẩn hóa dữ liệu đọc từ tệp, bung thành lần ca. */
public final class ScheduleRules {
    private ScheduleRules() {}

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Một lần ca đã bung ra thời điểm tuyệt đối, sẵn sàng ghi DB. */
    public static final class Occurrence {
        public final String date, code, slot, roster;
        public final long startMillis, endMillis;
        Occurrence(String date, String code, String slot, long start, long end, String roster) {
            this.date = date; this.code = code; this.slot = slot;
            this.startMillis = start; this.endMillis = end; this.roster = roster;
        }
    }

    public static String weekdayLabel(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w == DayOfWeek.SUNDAY ? "CN" : "T" + (w.getValue() + 1);
    }

    /** Chuẩn hóa mã kíp: " b " -> "B"; ngoài A–E -> null. */
    public static String normalizeCrew(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.startsWith("KÍP") || s.startsWith("KIP")) s = s.replaceAll("^K[ÍI]P\\s*", "");
        if (s.length() != 1) return null;
        for (String c : ScheduleDraft.CREWS) if (c.equals(s)) return c;
        return null;
    }

    /** "T2", "Thứ 2", "t.2", "CN", "Chủ nhật" -> "T2"/"CN"; không nhận ra -> null. */
    public static String normalizeWeekday(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        if (s.contains("CN") || s.contains("CHỦ") || s.contains("CHU NHAT") || s.startsWith("SUN")) return "CN";
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '2' && ch <= '7') return "T" + ch;
        }
        return null;
    }

    /** Nhận "2026-11-01" hoặc "01/11/2026" (hoặc "1/11/2026"); không đọc được -> null. */
    public static LocalDate parseDate(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        try {
            if (s.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
                String[] p = s.split("-");
                return LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            }
            if (s.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) {
                String[] p = s.split("/");
                return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
            }
        } catch (RuntimeException ignored) { }
        return null;
    }

    public static LocalTime parseTime(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (!s.matches("\\d{1,2}:\\d{2}")) return null;
        try {
            String[] p = s.split(":");
            return LocalTime.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        } catch (RuntimeException e) { return null; }
    }

    /**
     * Kiểm tra bản nháp và trả về danh sách cảnh báo tiếng Việt (rỗng = hợp lệ).
     * Chủ yếu đối chiếu "thứ" in trên ảnh với lịch thật để phát hiện đọc sai hoặc chọn sai tháng.
     */
    public static List<String> validate(ScheduleDraft d) {
        List<String> out = new ArrayList<>();
        if (d.month < 1 || d.month > 12 || d.year < 2000 || d.year > 2100) {
            out.add("Chưa xác định được tháng/năm của lịch.");
            return out;
        }
        int len = YearMonth.of(d.year, d.month).lengthOfMonth();
        if (d.days.length != len) out.add("Số cột ngày (" + d.days.length + ") khác số ngày của tháng " + d.month + "/" + d.year + " (" + len + ").");
        int weekdayChecked = 0, weekdayBad = 0;
        List<String> detail = new ArrayList<>();
        for (ScheduleDraft.Day day : d.days) {
            if (day == null || day.day < 1 || day.day > len) continue;
            String label = "Ngày " + day.day + ": ";
            for (int s = 0; s < 3; s++) {
                if (day.crew[s] == null) out.add(label + "thiếu mã kíp ca " + ScheduleDraft.SLOT_LABELS[s] + ".");
            }
            for (int a = 0; a < 3; a++) for (int b = a + 1; b < 3; b++) {
                if (day.crew[a] != null && day.crew[a].equals(day.crew[b])) {
                    out.add(label + "kíp " + day.crew[a] + " bị gán cho cả ca " + ScheduleDraft.SLOT_LABELS[a] + " và " + ScheduleDraft.SLOT_LABELS[b] + ".");
                }
            }
            if (day.weekday != null) {
                weekdayChecked++;
                String real = weekdayLabel(LocalDate.of(d.year, d.month, day.day));
                if (!real.equals(day.weekday)) {
                    weekdayBad++;
                    detail.add(label + "thứ trên ảnh là " + day.weekday + " nhưng lịch thật là " + real + ".");
                }
            }
        }
        if (weekdayChecked >= 7 && weekdayBad * 2 > weekdayChecked) {
            out.add("Hầu hết các thứ không khớp lịch tháng " + d.month + "/" + d.year + ": có thể chọn sai tháng/năm. Hãy đổi tháng/năm rồi kiểm tra lại.");
        } else {
            out.addAll(detail);
        }
        for (String c : ScheduleDraft.CREWS) {
            String r = d.rosters.get(c);
            if (r == null || r.trim().isEmpty()) out.add("Kíp " + c + " chưa có danh sách nhân sự.");
        }
        return out;
    }

    /** Các ngày có cảnh báo riêng (để tô nổi trong màn hình xem lại). */
    public static boolean dayHasIssue(ScheduleDraft d, ScheduleDraft.Day day) {
        if (day == null) return true;
        for (int a = 0; a < 3; a++) {
            if (day.crew[a] == null) return true;
            for (int b = a + 1; b < 3; b++) if (day.crew[a].equals(day.crew[b])) return true;
        }
        if (day.weekday != null && d.month >= 1 && d.month <= 12 && d.year >= 2000) {
            try { return !weekdayLabel(LocalDate.of(d.year, d.month, day.day)).equals(day.weekday); }
            catch (RuntimeException e) { return true; }
        }
        return false;
    }

    /** Bung bản nháp thành các lần ca có thời điểm tuyệt đối (ca đêm kết thúc ngày hôm sau). */
    public static List<Occurrence> expand(ScheduleDraft d) {
        int len = YearMonth.of(d.year, d.month).lengthOfMonth();
        LocalTime[] st = new LocalTime[3], en = new LocalTime[3];
        for (int s = 0; s < 3; s++) {
            st[s] = parseTime(d.slotStart[s]); en[s] = parseTime(d.slotEnd[s]);
            if (st[s] == null || en[s] == null) throw new IllegalArgumentException("Giờ ca " + ScheduleDraft.SLOT_LABELS[s] + " không hợp lệ (cần dạng HH:mm).");
        }
        DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE;
        List<Occurrence> out = new ArrayList<>();
        for (ScheduleDraft.Day day : d.days) {
            if (day == null || day.day < 1 || day.day > len) continue;
            LocalDate date = LocalDate.of(d.year, d.month, day.day);
            for (int s = 0; s < 3; s++) {
                String crew = day.crew[s];
                if (crew == null) continue;
                LocalDateTime start = LocalDateTime.of(date, st[s]);
                LocalDateTime end = LocalDateTime.of(date, en[s]);
                if (!end.isAfter(start)) end = end.plusDays(1);
                String roster = d.rosters.get(crew);
                out.add(new Occurrence(date.format(iso), crew, ScheduleDraft.SLOT_KEYS[s],
                        start.atZone(ZONE).toInstant().toEpochMilli(), end.atZone(ZONE).toInstant().toEpochMilli(),
                        roster == null ? "" : roster.trim()));
            }
        }
        return out;
    }

    public static String monthLabel(int month, int year) { return String.format(Locale.US, "%02d/%d", month, year); }
}
