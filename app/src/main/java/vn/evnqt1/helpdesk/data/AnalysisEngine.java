package vn.evnqt1.helpdesk.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import vn.evnqt1.helpdesk.core.AnalysisRange;
import vn.evnqt1.helpdesk.core.AnalysisResult;
import vn.evnqt1.helpdesk.core.ScheduleDraft;
import vn.evnqt1.helpdesk.core.ScheduleRules;
import vn.evnqt1.helpdesk.core.ShiftOccurrence;
import vn.evnqt1.helpdesk.core.TextNormalizer;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Các mẫu phân tích dữ liệu cục bộ cho trợ lý offline: chỉ đọc SQLite của ứng dụng, không dùng mạng hay mô hình AI.
 * Mọi kết quả là số liệu đếm/tổng hợp từ hồ sơ đã nhập; không suy đoán nguyên nhân hỏng hóc.
 */
public final class AnalysisEngine {
    // ---- SQL (tách riêng để dễ kiểm tra) ----
    static final String SQL_STATUS = "SELECT status, COUNT(*) FROM anomalies WHERE deleted_at IS NULL GROUP BY status";
    static final String SQL_OPEN_BY_SEVERITY = "SELECT severity, COUNT(*) FROM anomalies WHERE deleted_at IS NULL AND status<>'Đã xử lý' GROUP BY severity";
    static final String SQL_OLDEST_OPEN = "SELECT id, code, title, equipment_name, status, severity, detected_at FROM anomalies WHERE deleted_at IS NULL AND status<>'Đã xử lý' ORDER BY detected_at ASC LIMIT ?";
    static final String SQL_NEW = "SELECT id, code, title, equipment_name, status, severity, detected_at FROM anomalies WHERE deleted_at IS NULL AND detected_at>=? AND detected_at<? ORDER BY detected_at DESC LIMIT 30";
    static final String SQL_SEVERITY_STATUS = "SELECT severity, status, COUNT(*) FROM anomalies WHERE deleted_at IS NULL AND detected_at>=? AND detected_at<? GROUP BY severity, status";
    static final String SQL_TOP_EQUIPMENT = "SELECT MIN(TRIM(equipment_name)), COUNT(*), SUM(CASE WHEN status<>'Đã xử lý' THEN 1 ELSE 0 END), MAX(detected_at) FROM anomalies WHERE deleted_at IS NULL AND detected_at>=? AND detected_at<? AND TRIM(equipment_name)<>'' GROUP BY LOWER(TRIM(equipment_name)) ORDER BY COUNT(*) DESC, MAX(detected_at) DESC LIMIT 10";
    static final String SQL_TOP_KKS = "SELECT MIN(TRIM(kks)), COUNT(*), SUM(CASE WHEN status<>'Đã xử lý' THEN 1 ELSE 0 END), MAX(detected_at) FROM anomalies WHERE deleted_at IS NULL AND detected_at>=? AND detected_at<? AND kks IS NOT NULL AND TRIM(kks)<>'' GROUP BY LOWER(TRIM(kks)) ORDER BY COUNT(*) DESC, MAX(detected_at) DESC LIMIT 10";
    static final String SQL_TOP_SYSTEM = "SELECT MIN(TRIM(system_area)), COUNT(*), SUM(CASE WHEN status<>'Đã xử lý' THEN 1 ELSE 0 END), MAX(detected_at) FROM anomalies WHERE deleted_at IS NULL AND detected_at>=? AND detected_at<? AND system_area IS NOT NULL AND TRIM(system_area)<>'' GROUP BY LOWER(TRIM(system_area)) ORDER BY COUNT(*) DESC, MAX(detected_at) DESC LIMIT 10";
    static final String SQL_RESOLVED = "SELECT id, code, title, equipment_name, detected_at, resolved_at, reopened FROM anomalies WHERE deleted_at IS NULL AND status='Đã xử lý' AND resolved_at IS NOT NULL AND resolved_at>=? AND resolved_at<? AND resolved_at>=detected_at ORDER BY (resolved_at-detected_at) DESC";
    static final String SQL_WORK_ANOMALY = "SELECT COALESCE(s.shift_code,'?'), COUNT(*) FROM work_entries e LEFT JOIN repair_sessions s ON s.id=e.session_id WHERE e.entity_type='ANOMALY' AND e.work_at>=? AND e.work_at<? GROUP BY 1";
    static final String SQL_WORK_DRAWING = "SELECT COALESCE(s.shift_code,'?'), COUNT(*) FROM drawing_work_entries e LEFT JOIN drawing_work_sessions s ON s.id=e.drawing_work_session_id WHERE e.work_at>=? AND e.work_at<? GROUP BY 1";
    static final String SQL_DRAWING_REV = "SELECT d.id, d.code, d.drawing_number, d.title, COUNT(r.id), MAX(r.revision_date) FROM drawing_revisions r JOIN drawings d ON d.id=r.drawing_id WHERE d.deleted_at IS NULL AND r.revision_date>=? AND r.revision_date<? GROUP BY d.id ORDER BY COUNT(r.id) DESC, MAX(r.revision_date) DESC LIMIT 10";
    static final String SQL_ROSTERS = "SELECT roster FROM shift_occurrences WHERE roster IS NOT NULL AND roster<>''";

    private final HelpDeskRepository repo;
    private final SQLiteDatabase db;
    private final SimpleDateFormat day = new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));
    private final SimpleDateFormat dateTime = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"));

    public AnalysisEngine(Context context, HelpDeskRepository repo) {
        this.repo = repo;
        this.db = LocalDatabase.get(context).getReadableDatabase();
        day.setTimeZone(java.util.TimeZone.getTimeZone(ScheduleRules.ZONE));
        dateTime.setTimeZone(java.util.TimeZone.getTimeZone(ScheduleRules.ZONE));
    }

    private String d(long t) { return day.format(new Date(t)); }
    private String days(long ms) { return ms < 0 ? "—" : (ms / 86_400_000L) + " ngày " + (ms % 86_400_000L) / 3_600_000L + " giờ"; }
    private String[] range(int id, long now) { long[] r = AnalysisRange.resolve(id, now, ScheduleRules.ZONE); return new String[]{String.valueOf(r[0]), String.valueOf(r[1])}; }
    private String rangeText(int id, long now) {
        if (id == AnalysisRange.ALL) return "toàn bộ thời gian";
        long[] r = AnalysisRange.resolve(id, now, ScheduleRules.ZONE);
        return AnalysisRange.label(id).toLowerCase(Locale.ROOT) + " (" + d(r[0]) + " – " + d(r[1] - 1) + ")";
    }
    private long scalar(String sql, String... args) { try (Cursor c = db.rawQuery(sql, args)) { return c.moveToFirst() ? c.getLong(0) : 0; } }
    private static String s(Cursor c, int i) { String v = c.getString(i); return v == null ? "" : v; }

    // ---------------------------------------------------------------- tên nhân sự đã biết
    /** Tên nhân sự (không trùng) xuất hiện trong lịch ca, dùng cho lựa chọn "lịch trực của…". */
    public List<String> knownNames() {
        Map<String, String> names = new TreeMap<>();
        try (Cursor c = db.rawQuery(SQL_ROSTERS, null)) {
            while (c.moveToNext()) for (String n : s(c, 0).split("\\s*,\\s*")) { String t = n.trim(); if (!t.isEmpty()) names.put(TextNormalizer.normalize(t), t); }
        }
        return new ArrayList<>(names.values());
    }

    // ---------------------------------------------------------------- 1. Tổng quan
    public AnalysisResult overview(long now) {
        AnalysisResult r = new AnalysisResult("Tổng quan tình hình");
        long total = 0, open = 0;
        Map<String, Long> st = new LinkedHashMap<>();
        try (Cursor c = db.rawQuery(SQL_STATUS, null)) { while (c.moveToNext()) { st.put(s(c, 0), c.getLong(1)); total += c.getLong(1); } }
        long chua = st.containsKey("Chưa xử lý") ? st.get("Chưa xử lý") : 0, dang = st.containsKey("Đang xử lý") ? st.get("Đang xử lý") : 0, xong = st.containsKey("Đã xử lý") ? st.get("Đã xử lý") : 0;
        open = chua + dang;
        r.line("Bất thường: " + total + " hồ sơ — chưa xử lý " + chua + ", đang xử lý " + dang + ", đã xử lý " + xong + ".");
        if (open > 0) {
            StringBuilder sb = new StringBuilder("Đang tồn đọng theo mức độ: ");
            boolean first = true;
            try (Cursor c = db.rawQuery(SQL_OPEN_BY_SEVERITY, null)) { while (c.moveToNext()) { if (!first) sb.append(", "); sb.append(s(c, 0)).append(" ").append(c.getLong(1)); first = false; } }
            r.line(sb.append(".").toString());
            try (Cursor c = db.rawQuery(SQL_OLDEST_OPEN, new String[]{"1"})) { if (c.moveToFirst()) r.line("Tồn đọng lâu nhất: " + s(c, 1) + " · " + s(c, 3) + " — " + days(now - c.getLong(6)) + " kể từ khi phát hiện."); }
        }
        r.line("Bản vẽ: " + repo.count("drawings", "deleted_at IS NULL") + " hồ sơ, " + repo.count("drawing_revisions", null) + " revision, " + repo.count("drawing_work_sessions", null) + " phiên công việc.");
        r.line("Cảnh báo còn hiệu lực: " + repo.count("alerts", "condition_status='active'") + " (chưa xem " + repo.count("alerts", "condition_status='active' AND is_read=0") + ").");
        ShiftOccurrence cur = repo.currentShift(now);
        r.line(cur == null ? "Ca trực hiện tại: chưa có lịch ca khớp thời điểm này." : "Ca trực hiện tại: Kíp " + cur.code + " (" + slotLabel(cur.slot) + "): " + cur.roster);
        return r;
    }

    // ---------------------------------------------------------------- 2. Tồn đọng lâu nhất
    public AnalysisResult oldestOpen(long now, int limit) {
        AnalysisResult r = new AnalysisResult("Bất thường tồn đọng lâu nhất").table("Mã", "Thiết bị / nội dung", "Trạng thái", "Đã tồn tại");
        r.linksTitle = "Mở hồ sơ";
        try (Cursor c = db.rawQuery(SQL_OLDEST_OPEN, new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) {
                r.row(s(c, 1), s(c, 3) + " — " + s(c, 2), s(c, 4) + " · " + s(c, 5), days(now - c.getLong(6)));
                r.link("ANOMALY", s(c, 0), s(c, 1) + " · " + s(c, 3));
            }
        }
        r.line(r.rows.isEmpty() ? "Không có bất thường nào đang tồn đọng." : "Các bất thường chưa xử lý xong, sắp theo thời gian phát hiện cũ nhất.");
        return r;
    }

    // ---------------------------------------------------------------- 3. Bất thường mới trong khoảng
    public AnalysisResult newAnomalies(int rangeId, long now) {
        AnalysisResult r = new AnalysisResult("Bất thường phát sinh: " + AnalysisRange.label(rangeId)).table("Ngày", "Mã", "Nội dung", "Mức độ");
        String[] rg = range(rangeId, now);
        long n = scalar("SELECT COUNT(*) FROM anomalies WHERE deleted_at IS NULL AND detected_at>=? AND detected_at<?", rg);
        r.line("Có " + n + " bất thường được phát hiện trong " + rangeText(rangeId, now) + (n > 30 ? " (hiển thị 30 mới nhất)." : "."));
        try (Cursor c = db.rawQuery(SQL_NEW, rg)) {
            while (c.moveToNext()) { r.row(d(c.getLong(6)), s(c, 1), s(c, 3) + " — " + s(c, 2), s(c, 5)); r.link("ANOMALY", s(c, 0), s(c, 1) + " · " + s(c, 3)); }
        }
        return r;
    }

    // ---------------------------------------------------------------- 4. Mức độ × trạng thái
    public AnalysisResult severityStatus(int rangeId, long now) {
        AnalysisResult r = new AnalysisResult("Thống kê theo mức độ và trạng thái").table("Mức độ", "Chưa xử lý", "Đang xử lý", "Đã xử lý", "Tổng");
        String[] sev = {"Nghiêm trọng", "Cao", "Trung bình", "Thấp"}, sts = {"Chưa xử lý", "Đang xử lý", "Đã xử lý"};
        long[][] m = new long[4][3];
        try (Cursor c = db.rawQuery(SQL_SEVERITY_STATUS, range(rangeId, now))) {
            while (c.moveToNext()) { int i = idx(sev, s(c, 0)), j = idx(sts, s(c, 1)); if (i >= 0 && j >= 0) m[i][j] += c.getLong(2); }
        }
        long grand = 0;
        for (int i = 0; i < 4; i++) { long t = m[i][0] + m[i][1] + m[i][2]; grand += t; r.row(sev[i], String.valueOf(m[i][0]), String.valueOf(m[i][1]), String.valueOf(m[i][2]), String.valueOf(t)); }
        r.line("Tính theo ngày phát hiện trong " + rangeText(rangeId, now) + ": tổng " + grand + " bất thường.");
        return r;
    }
    private static int idx(String[] a, String v) { for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i; return -1; }

    // ---------------------------------------------------------------- 5. Thiết bị / KKS / hệ thống lặp lại nhiều nhất
    public AnalysisResult topEquipment(int rangeId, int groupBy, long now) {
        String[] names = {"thiết bị", "KKS", "hệ thống/khu vực"};
        String sql = groupBy == 1 ? SQL_TOP_KKS : groupBy == 2 ? SQL_TOP_SYSTEM : SQL_TOP_EQUIPMENT;
        AnalysisResult r = new AnalysisResult("Bất thường lặp lại nhiều nhất theo " + names[Math.max(0, Math.min(2, groupBy))]).table(names[Math.max(0, Math.min(2, groupBy))].substring(0, 1).toUpperCase(Locale.ROOT) + names[Math.max(0, Math.min(2, groupBy))].substring(1), "Số lần", "Chưa xong", "Gần nhất");
        try (Cursor c = db.rawQuery(sql, range(rangeId, now))) { while (c.moveToNext()) r.row(s(c, 0), String.valueOf(c.getLong(1)), String.valueOf(c.getLong(2)), d(c.getLong(3))); }
        r.line(r.rows.isEmpty() ? "Chưa có dữ liệu trong " + rangeText(rangeId, now) + "." : "Xếp theo số lần xuất hiện trong " + rangeText(rangeId, now) + ". Số liệu chỉ phản ánh số hồ sơ đã nhập, không kết luận nguyên nhân.");
        return r;
    }

    // ---------------------------------------------------------------- 6. Thời gian xử lý
    public AnalysisResult resolutionTime(int rangeId, long now) {
        AnalysisResult r = new AnalysisResult("Thời gian xử lý bất thường").table("Mã", "Thiết bị", "Thời gian xử lý");
        long count = 0, sum = 0, min = Long.MAX_VALUE, max = 0, reopened = 0;
        try (Cursor c = db.rawQuery(SQL_RESOLVED, range(rangeId, now))) {
            while (c.moveToNext()) {
                long t = c.getLong(5) - c.getLong(4);
                count++; sum += t; min = Math.min(min, t); max = Math.max(max, t); reopened += c.getLong(6) > 0 ? 1 : 0;
                if (r.rows.size() < 8) { r.row(s(c, 1), s(c, 3), days(t)); r.link("ANOMALY", s(c, 0), s(c, 1) + " · " + s(c, 3)); }
            }
        }
        if (count == 0) r.line("Chưa có bất thường nào được đóng trong " + rangeText(rangeId, now) + ".");
        else {
            r.line("Số bất thường đã xử lý xong: " + count + " (tính theo ngày đóng trong " + rangeText(rangeId, now) + ").");
            r.line("Trung bình " + days(sum / count) + " · nhanh nhất " + days(min) + " · lâu nhất " + days(max) + ".");
            if (reopened > 0) r.line("Trong đó " + reopened + " bất thường từng được mở lại.");
            r.line("Bảng liệt kê các trường hợp lâu nhất.");
        }
        r.linksTitle = "Mở hồ sơ";
        return r;
    }

    // ---------------------------------------------------------------- 7. Khối lượng công việc theo kíp
    public AnalysisResult workloadByCrew(int rangeId, long now, String onlyCrew) {
        AnalysisResult r = new AnalysisResult("Khối lượng công việc theo kíp").table("Kíp", "Việc bất thường", "Việc bản vẽ", "Tổng");
        Map<String, long[]> m = new LinkedHashMap<>();
        for (String c : ScheduleDraft.CREWS) m.put(c, new long[2]);
        m.put("?", new long[2]);
        String[] rg = range(rangeId, now);
        try (Cursor c = db.rawQuery(SQL_WORK_ANOMALY, rg)) { while (c.moveToNext()) { long[] v = m.get(m.containsKey(s(c, 0)) ? s(c, 0) : "?"); v[0] += c.getLong(1); } }
        try (Cursor c = db.rawQuery(SQL_WORK_DRAWING, rg)) { while (c.moveToNext()) { long[] v = m.get(m.containsKey(s(c, 0)) ? s(c, 0) : "?"); v[1] += c.getLong(1); } }
        long total = 0;
        for (Map.Entry<String, long[]> e : m.entrySet()) {
            long[] v = e.getValue(); long t = v[0] + v[1];
            if (onlyCrew != null && !onlyCrew.equals(e.getKey())) continue;
            if (t == 0 && e.getKey().equals("?")) continue;
            total += t;
            r.row(e.getKey().equals("?") ? "Chưa xác định ca" : "Kíp " + e.getKey(), String.valueOf(v[0]), String.valueOf(v[1]), String.valueOf(t));
        }
        r.line("Số lần ghi nhận công việc trong " + rangeText(rangeId, now) + ": " + total + ". Ca được gán tự động theo lịch ca tại thời điểm ghi.");
        return r;
    }

    // ---------------------------------------------------------------- 8. Bản vẽ chỉnh sửa nhiều nhất
    public AnalysisResult drawingRevisions(int rangeId, long now) {
        AnalysisResult r = new AnalysisResult("Bản vẽ có nhiều lần chỉnh sửa nhất").table("Số bản vẽ", "Tên", "Số revision", "Gần nhất");
        r.linksTitle = "Mở hồ sơ";
        try (Cursor c = db.rawQuery(SQL_DRAWING_REV, range(rangeId, now))) {
            while (c.moveToNext()) { r.row(s(c, 2), s(c, 3), String.valueOf(c.getLong(4)), d(c.getLong(5))); r.link("DRAWING", s(c, 0), s(c, 2)); }
        }
        r.line(r.rows.isEmpty() ? "Chưa có revision nào trong " + rangeText(rangeId, now) + "." : "Xếp theo số revision ghi nhận trong " + rangeText(rangeId, now) + ".");
        return r;
    }

    // ---------------------------------------------------------------- 9. Ai trực
    public AnalysisResult whoOnDuty(long when) {
        AnalysisResult r = new AnalysisResult("Ca trực tại " + dateTime.format(new Date(when)));
        ShiftOccurrence cur = repo.currentShift(when);
        if (cur == null) r.line("Không có lịch ca khớp thời điểm này (chưa nhập lịch hoặc các ca chồng lấn).");
        else {
            r.line("Đang trực: Kíp " + cur.code + " · " + slotLabel(cur.slot) + " (" + hm(cur.startMillis) + " – " + hm(cur.endMillis) + ").");
            r.line("Nhân sự: " + (cur.roster.isEmpty() ? "chưa nhập" : cur.roster));
        }
        LocalDate date = Instant.ofEpochMilli(when).atZone(ScheduleRules.ZONE).toLocalDate();
        long from = date.atStartOfDay(ScheduleRules.ZONE).toInstant().toEpochMilli();
        List<ShiftOccurrence> list = repo.shiftsBetween(from - 86_400_000L, from + 2 * 86_400_000L);
        r.table("Ca", "Giờ", "Kíp", "Nhân sự");
        for (ShiftOccurrence o : list) if (o.startMillis >= from && o.startMillis < from + 86_400_000L) r.row(slotLabel(o.slot), hm(o.startMillis) + "–" + hm(o.endMillis), o.code, o.roster);
        return r;
    }

    // ---------------------------------------------------------------- 10. Lịch trực của một người
    public AnalysisResult personSchedule(String person, int rangeId, long now) {
        AnalysisResult r = new AnalysisResult("Lịch trực của " + person).table("Ngày", "Thứ", "Ca", "Kíp");
        long[] rg = AnalysisRange.resolve(rangeId, now, ScheduleRules.ZONE);
        long to = rg[1] == Long.MAX_VALUE ? Long.MAX_VALUE : rg[1];
        int[] cnt = new int[3]; int other = 0;
        for (ShiftOccurrence o : repo.shiftsBetween(rg[0], to)) {
            if (!TextNormalizer.contains(o.roster, person)) continue;
            LocalDate dt = LocalDate.parse(o.shiftDate);
            r.row(String.format(Locale.US, "%02d/%02d", dt.getDayOfMonth(), dt.getMonthValue()), ScheduleRules.weekdayLabel(dt), slotLabel(o.slot) + " " + hm(o.startMillis) + "–" + hm(o.endMillis), o.code);
            int sl = slotIndex(o.slot); if (sl >= 0) cnt[sl]++; else other++;
        }
        if (r.rows.isEmpty()) r.line(person + " không có ca nào trong " + rangeText(rangeId, now) + ".");
        else r.line(person + ": " + r.rows.size() + " ca trong " + rangeText(rangeId, now) + " — sáng " + cnt[0] + ", chiều " + cnt[1] + ", đêm " + cnt[2] + (other > 0 ? ", ca khác " + other : "") + ".");
        return r;
    }

    // ---------------------------------------------------------------- 11. Số ca của từng người
    public AnalysisResult shiftCount(int rangeId, long now) {
        AnalysisResult r = new AnalysisResult("Số ca trực của từng người").table("Họ tên", "Sáng", "Chiều", "Đêm", "Tổng");
        long[] rg = AnalysisRange.resolve(rangeId, now, ScheduleRules.ZONE);
        Map<String, int[]> m = new TreeMap<>();
        Map<String, String> display = new LinkedHashMap<>();
        for (ShiftOccurrence o : repo.shiftsBetween(rg[0], rg[1])) {
            if (o.roster == null || o.roster.isEmpty()) continue;
            int sl = slotIndex(o.slot);
            for (String name : o.roster.split("\\s*,\\s*")) {
                String t = name.trim(); if (t.isEmpty()) continue;
                String key = TextNormalizer.normalize(t); display.put(key, t);
                int[] v = m.get(key); if (v == null) { v = new int[4]; m.put(key, v); }
                if (sl >= 0) v[sl]++; v[3]++;
            }
        }
        for (Map.Entry<String, int[]> e : m.entrySet()) { int[] v = e.getValue(); r.row(display.get(e.getKey()), String.valueOf(v[0]), String.valueOf(v[1]), String.valueOf(v[2]), String.valueOf(v[3])); }
        r.line(m.isEmpty() ? "Chưa có lịch ca trong " + rangeText(rangeId, now) + "." : "Đếm số ca mỗi người theo lịch trong " + rangeText(rangeId, now) + " (hữu ích để kiểm tra chia đều ca đêm).");
        return r;
    }

    // ---------------------------------------------------------------- 12. Cảnh báo
    public AnalysisResult alerts() {
        AnalysisResult r = new AnalysisResult("Cảnh báo đang có hiệu lực").table("Mã", "Loại", "Lý do");
        r.linksTitle = "Mở hồ sơ";
        for (HelpDeskRepository.AlertItem a : repo.alerts()) {
            if (!"active".equals(a.condition)) continue;
            r.row(a.anomalyCode, a.type, a.reason); r.link("ANOMALY", a.anomalyId, a.anomalyCode + " · " + a.title);
        }
        r.line(r.rows.isEmpty() ? "Không có cảnh báo nào còn hiệu lực." : r.rows.size() + " cảnh báo còn hiệu lực theo ngưỡng đã cài.");
        return r;
    }

    // ---------------------------------------------------------------- tìm kiếm từ khóa
    public AnalysisResult search(String query) {
        AnalysisResult r = new AnalysisResult("Kết quả tìm kiếm");
        List<HelpDeskRepository.Record> found = repo.search(query);
        r.line(found.isEmpty() ? "Không tìm thấy hồ sơ nào chứa “" + query + "”. Bạn có thể chọn một mẫu phân tích có sẵn bên dưới." : "Tìm thấy " + found.size() + " hồ sơ chứa “" + query + "”.");
        r.linksTitle = "Hồ sơ tìm thấy";
        for (HelpDeskRepository.Record x : found) {
            if (r.links.size() >= 30) break;
            boolean drawing = x.drawingNumber != null;
            r.link(drawing ? "DRAWING" : "ANOMALY", x.id, x.code + " · " + (drawing ? x.drawingNumber : x.title));
        }
        return r;
    }

    // ---------------------------------------------------------------- tiện ích
    private String hm(long t) { return new SimpleDateFormat("HH:mm", Locale.US) {{ setTimeZone(java.util.TimeZone.getTimeZone(ScheduleRules.ZONE)); }}.format(new Date(t)); }
    private static int slotIndex(String slot) { return "MORNING".equals(slot) ? 0 : "AFTERNOON".equals(slot) ? 1 : "NIGHT".equals(slot) ? 2 : -1; }
    private static String slotLabel(String slot) { int i = slotIndex(slot); return i < 0 ? "Ca" : "Ca " + ScheduleDraft.SLOT_LABELS[i].toLowerCase(Locale.ROOT); }
}
