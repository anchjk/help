package vn.evnqt1.helpdesk.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Kiểm thử thống kê bất thường: tồn đọng, phát sinh mới, đã xử lý, chia cột biểu đồ, phân loại tự động. */
public final class StatsSmokeTest {
    private static int passed = 0;
    private static final ZoneId Z = ScheduleRules.ZONE;

    public static void main(String[] args) {
        // ---- phân loại
        eq(AnomalyCategorizer.categorize("Rò rỉ nước phớt bơm cấp", ""), "Rò rỉ", "rò rỉ");
        eq(AnomalyCategorizer.categorize("Bơm tuần hoàn rung cao", "tiếng ồn bất thường ở ổ trục"), "Rung / ồn / tiếng kêu", "rung");
        eq(AnomalyCategorizer.categorize("Nhiệt độ cuộn dây tăng", ""), "Nhiệt độ bất thường", "nhiệt");
        eq(AnomalyCategorizer.categorize("Mất tín hiệu cảm biến mức", ""), "Điện / bảo vệ / tín hiệu", "tín hiệu");
        eq(AnomalyCategorizer.categorize("Kẹt van điều khiển lưu lượng", ""), "Van / điều khiển / cơ cấu chấp hành", "van (ưu tiên van/điều khiển hơn lưu lượng)");
        eq(AnomalyCategorizer.categorize("Cập nhật quy trình vận hành", "theo bản vẽ mới"), AnomalyCategorizer.OTHER, "'vận hành', 'bản vẽ' không bị nhầm thành van/bám bẩn");
        eq(AnomalyCategorizer.categorize("Vấn đề kỹ thuật", ""), AnomalyCategorizer.OTHER, "'vấn đề' không bị nhầm thành van");
        eq(AnomalyCategorizer.categorize("Thiếu dầu bôi trơn ổ đỡ", ""), "Dầu / bôi trơn", "dầu");
        eq(AnomalyCategorizer.categorize("Gỉ sét chân đế", ""), "Gỉ sét / ăn mòn", "gỉ sét");

        // ---- cột biểu đồ
        long from = ms(2026, 11, 1, 0), to = ms(2026, 12, 1, 0);
        StatsBuckets b = StatsBuckets.of(from, to, Z);
        check(b.unit == StatsBuckets.DAY && b.size() == 30, "tháng 11 = 30 cột ngày");
        check(b.indexOf(ms(2026, 11, 15, 10)) == 14 && b.indexOf(to) == -1 && b.indexOf(from - 1) == -1, "indexOf theo ngày");
        StatsBuckets w = StatsBuckets.of(ms(2026, 8, 1, 0), ms(2026, 11, 1, 0), Z);
        check(w.unit == StatsBuckets.WEEK && w.size() == 14, "92 ngày chia theo tuần");
        StatsBuckets m = StatsBuckets.of(ms(2026, 1, 1, 0), ms(2027, 1, 1, 0), Z);
        check(m.unit == StatsBuckets.MONTH && m.size() == 12 && m.labels[0].equals("01/26") && m.indexOf(ms(2026, 12, 31, 23)) == 11, "cả năm chia theo tháng");
        StatsBuckets q = StatsBuckets.of(ms(2023, 1, 1, 0), ms(2026, 1, 1, 0), Z);
        check(q.unit == StatsBuckets.QUARTER && q.size() == 12, "3 năm chia theo quý");

        // ---- thống kê: now = 21/11/2026
        long now = ms(2026, 11, 21, 9);
        List<StatsReport.Item> all = new ArrayList<>();
        all.add(item("1", "Rò rỉ nước bơm cấp 1", "Bơm cấp nước số 1", "Hệ thống nước cấp", "Cao", "Đang xử lý", ms(2026, 11, 3, 8), 0));
        all.add(item("2", "Rò rỉ phớt bơm cấp", "bom cap nuoc so 1", "hệ thống nước cấp", "Trung bình", "Chưa xử lý", ms(2026, 11, 10, 8), 0));
        all.add(item("3", "Rung cao quạt gió", "Quạt gió 1A", "Gió", "Nghiêm trọng", "Đã xử lý", ms(2026, 11, 5, 8), ms(2026, 11, 8, 8)));
        all.add(item("4", "Mất tín hiệu cảm biến", "Van điều khiển", "", "Thấp", "Đã xử lý", ms(2026, 10, 20, 8), ms(2026, 11, 2, 8)));
        all.add(item("5", "Nhiệt độ cao ổ trục", "Quạt gió 1A", "Gió", "Cao", "Chưa xử lý", ms(2026, 10, 12, 8), 0));
        all.add(item("6", "Kẹt van", "Van điều khiển", "Nước", "Thấp", "Đã xử lý", ms(2026, 9, 25, 8), ms(2026, 10, 15, 8)));
        StatsReport r = StatsCalculator.compute(all, from, to, now, Z);
        check(r.openTotal == 3 && r.openUnprocessed == 2 && r.openInProgress == 1, "đang tồn tại: 3 (2 chưa xử lý, 1 đang xử lý)");
        check(r.openBySeverity[1] == 2 && r.openBySeverity[2] == 1 && r.openBySeverity[0] == 0, "tồn đọng theo mức độ");
        check(r.openList.get(0).id.equals("5"), "tồn đọng cũ nhất xếp đầu");
        check(r.newList.size() == 3 && r.newStillOpen == 2, "tháng 11 phát sinh mới: 3 (2 còn mở)");
        check(r.resolvedList.size() == 2 && r.resolvedList.get(0).id.equals("3"), "đã xử lý trong tháng 11: 2, mới đóng xếp trước");
        check(r.net() == 1, "chênh lệch mới - đã xử lý = +1");
        check(r.avgResolveMs == ((3L * 24 + 13L * 24) * 3600_000L) / 2 , "thời gian xử lý trung bình");
        check(r.prevNew == 2 && r.prevResolved == 1, "kỳ liền trước (2/10–31/10, cùng 30 ngày): 2 mới (#4, #5), 1 đã xử lý (#6)");
        check(r.newPerBucket[2] == 1 && r.resolvedPerBucket[7] == 1 && r.resolvedPerBucket[1] == 1, "đếm theo cột ngày");
        check(r.byCategory.get(0).name.equals("Rò rỉ") && r.byCategory.get(0).count == 2, "loại hay xảy ra nhất");
        check(r.byEquipment.get(0).count == 2 && r.byEquipment.get(0).items.size() == 2, "gộp thiết bị không phân biệt hoa/dấu");
        check(r.bySystem.get(0).count == 2, "gộp hệ thống");
        check(r.totalResolvedAllTime == 3 && r.totalAnomalies == 6, "tổng");
        StatsReport empty = StatsCalculator.compute(new ArrayList<>(), from, to, now, Z);
        check(empty.openTotal == 0 && empty.newList.isEmpty() && empty.avgResolveMs == -1 && empty.byCategory.isEmpty(), "dữ liệu rỗng");
        System.out.println("PASS: " + passed + " stats assertions");
    }

    private static StatsReport.Item item(String id, String title, String equipment, String system, String sev, String status, long detected, long resolved) {
        StatsReport.Item i = new StatsReport.Item();
        i.id = id; i.code = "ABN-" + id; i.title = title; i.description = ""; i.equipment = equipment; i.system = system; i.severity = sev; i.status = status; i.detectedAt = detected; i.resolvedAt = resolved;
        return i;
    }
    private static long ms(int y, int mo, int d, int h) { return LocalDateTime.of(y, mo, d, h, 0).atZone(Z).toInstant().toEpochMilli(); }
    private static void eq(String actual, String expected, String name) { check(expected.equals(actual), name + " (được: " + actual + ")"); }
    private static void check(boolean ok, String name) { if (!ok) throw new AssertionError("FAIL: " + name); passed++; }
}
