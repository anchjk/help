package vn.evnqt1.helpdesk.core;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** Kiểm thử hiểu câu hỏi và khoảng thời gian của trợ lý phân tích offline. */
public final class AssistantSmokeTest {
    public static void main(String[] args) {
        List<String> names = Arrays.asList("Nguyễn Anh Quân", "Trần Xuân An", "Nguyễn Văn Dũng(DP)", "Hoàng Minh Dương");
        check(type("Hôm nay kíp nào đang trực?", names).equals("WHO_ON_DUTY"), "ai trực");
        check(type("bất thường tồn đọng lâu nhất", names).equals("OLDEST_OPEN"), "tồn đọng");
        check(type("Thiết bị nào hỏng nhiều nhất trong 30 ngày", names).equals("TOP_EQUIPMENT") && AssistantIntent.detect("Thiết bị nào hỏng nhiều nhất trong 30 ngày", names).range == AnalysisRange.LAST30, "top thiết bị + 30 ngày");
        check(type("thong ke muc do bat thuong thang nay", names).equals("SEVERITY_STATUS"), "mức độ không dấu");
        check(type("thời gian xử lý trung bình", names).equals("RESOLUTION_TIME"), "thời gian xử lý");
        check(type("khối lượng công việc theo kíp tháng trước", names).equals("WORKLOAD_BY_CREW") && AssistantIntent.detect("khối lượng công việc theo kíp tháng trước", names).range == AnalysisRange.LAST_MONTH, "khối lượng theo kíp");
        check(type("bản vẽ nào sửa nhiều revision nhất", names).equals("DRAWING_REVISIONS"), "revision bản vẽ");
        check(type("cảnh báo đang có hiệu lực", names).equals("ALERTS"), "cảnh báo");
        check(type("đếm ca đêm tháng này của từng người", names).equals("SHIFT_COUNT"), "số ca");
        check(type("tóm tắt tình hình", names).equals("OVERVIEW"), "tổng quan");
        AssistantIntent p = AssistantIntent.detect("lịch trực của Nguyễn Anh Quân tháng sau", names);
        check(p.type.equals("PERSON_SCHEDULE") && "Nguyễn Anh Quân".equals(p.person) && p.range == AnalysisRange.NEXT_MONTH, "lịch trực của một người");
        check(AssistantIntent.detect("lich truc cua nguyen van dung", names).person.equals("Nguyễn Văn Dũng(DP)"), "nhận tên không dấu, bỏ chú thích (DP)");
        check(AssistantIntent.detect("kíp C xử lý gì", names).crew.equals("C"), "nhận kíp C");
        check(type("van hanh bom nuoc cap", names).equals("SEARCH"), "không hiểu -> tìm kiếm");

        long now = LocalDateTime.of(2026, 11, 20, 15, 30).atZone(ScheduleRules.ZONE).toInstant().toEpochMilli();
        long[] m = AnalysisRange.resolve(AnalysisRange.THIS_MONTH, now, ScheduleRules.ZONE);
        check(m[0] == LocalDateTime.of(2026, 11, 1, 0, 0).atZone(ScheduleRules.ZONE).toInstant().toEpochMilli() && m[1] == LocalDateTime.of(2026, 12, 1, 0, 0).atZone(ScheduleRules.ZONE).toInstant().toEpochMilli(), "tháng này");
        long[] lm = AnalysisRange.resolve(AnalysisRange.LAST_MONTH, now, ScheduleRules.ZONE);
        check(lm[1] == m[0], "tháng trước kết thúc đúng ngày 1 tháng này");
        long[] l7 = AnalysisRange.resolve(AnalysisRange.LAST7, now, ScheduleRules.ZONE);
        check(l7[1] - l7[0] == 7 * 86_400_000L, "7 ngày qua = 7 ngày");
        long[] all = AnalysisRange.resolve(AnalysisRange.ALL, now, ScheduleRules.ZONE);
        check(all[0] == 0 && all[1] == Long.MAX_VALUE, "toàn bộ");
        System.out.println("PASS: assistant intent/range assertions");
    }
    private static String type(String q, List<String> names) { return AssistantIntent.detect(q, names).type; }
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
}
