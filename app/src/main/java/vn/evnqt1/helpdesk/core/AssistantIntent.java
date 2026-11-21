package vn.evnqt1.helpdesk.core;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hiểu câu hỏi tiếng Việt (có hoặc không dấu) bằng từ khóa để chọn đúng mẫu phân tích; chạy hoàn toàn offline, không dùng mô hình AI.
 * Không hiểu thì trả về SEARCH (tìm kiếm từ khóa trong hồ sơ).
 */
public final class AssistantIntent {
    public static final String OVERVIEW = "OVERVIEW", OLDEST_OPEN = "OLDEST_OPEN", NEW_ANOMALIES = "NEW_ANOMALIES", SEVERITY_STATUS = "SEVERITY_STATUS",
            TOP_EQUIPMENT = "TOP_EQUIPMENT", RESOLUTION_TIME = "RESOLUTION_TIME", WORKLOAD_BY_CREW = "WORKLOAD_BY_CREW", DRAWING_REVISIONS = "DRAWING_REVISIONS",
            WHO_ON_DUTY = "WHO_ON_DUTY", PERSON_SCHEDULE = "PERSON_SCHEDULE", SHIFT_COUNT = "SHIFT_COUNT", ALERTS = "ALERTS", SEARCH = "SEARCH";

    public String type = SEARCH;
    public int range = AnalysisRange.THIS_MONTH;
    public boolean rangeGiven;
    public String person;       // PERSON_SCHEDULE
    public String crew;         // kíp A–E nếu câu hỏi nhắc tới
    public String query;        // SEARCH

    private static boolean has(String n, String... words) { for (String w : words) if (n.contains(w)) return true; return false; }

    /** @param knownNames tên nhân sự đã có trong lịch ca (để nhận ra "lịch trực của Nguyễn Anh Quân"). */
    public static AssistantIntent detect(String question, List<String> knownNames) {
        AssistantIntent r = new AssistantIntent();
        String n = TextNormalizer.normalize(question);
        r.query = question == null ? "" : question.trim();
        if (n.isEmpty()) return r;

        // Khoảng thời gian
        if (has(n, "hom nay")) { r.range = AnalysisRange.TODAY; r.rangeGiven = true; }
        else if (has(n, "thang truoc", "thang vua roi")) { r.range = AnalysisRange.LAST_MONTH; r.rangeGiven = true; }
        else if (has(n, "thang sau", "thang toi")) { r.range = AnalysisRange.NEXT_MONTH; r.rangeGiven = true; }
        else if (has(n, "thang nay")) { r.range = AnalysisRange.THIS_MONTH; r.rangeGiven = true; }
        else if (has(n, "7 ngay toi", "tuan toi")) { r.range = AnalysisRange.NEXT7; r.rangeGiven = true; }
        else if (has(n, "7 ngay", "tuan nay", "tuan qua", "mot tuan")) { r.range = AnalysisRange.LAST7; r.rangeGiven = true; }
        else if (has(n, "30 ngay")) { r.range = AnalysisRange.LAST30; r.rangeGiven = true; }
        else if (has(n, "nam nay")) { r.range = AnalysisRange.THIS_YEAR; r.rangeGiven = true; }
        else if (has(n, "toan bo", "tat ca", "tu truoc den nay")) { r.range = AnalysisRange.ALL; r.rangeGiven = true; }

        Matcher km = Pattern.compile("\\bkip\\s*([a-e])\\b").matcher(n);
        if (km.find()) r.crew = km.group(1).toUpperCase();

        // Lịch trực của một người cụ thể
        if (knownNames != null) {
            String best = null;
            for (String name : knownNames) {
                String nn = TextNormalizer.normalize(name).replaceAll("\\(.*?\\)", "").trim();
                if (nn.length() >= 4 && n.contains(nn) && (best == null || nn.length() > TextNormalizer.normalize(best).length())) best = name;
            }
            if (best != null) { r.type = PERSON_SCHEDULE; r.person = best; if (!r.rangeGiven) r.range = AnalysisRange.THIS_MONTH; return r; }
        }

        if (has(n, "ai truc", "dang truc", "kip nao", "ai dang", "ca truc hien tai", "truc ca nao")) { r.type = WHO_ON_DUTY; return r; }
        if (has(n, "so ca", "dem ca", "bao nhieu ca", "ca dem", "chia ca", "can bang ca")) { r.type = SHIFT_COUNT; return r; }
        if (has(n, "canh bao")) { r.type = ALERTS; return r; }
        if (has(n, "ban ve") && has(n, "revision", "rev", "sua", "chinh sua", "thay doi", "nhieu nhat")) { r.type = DRAWING_REVISIONS; if (!r.rangeGiven) r.range = AnalysisRange.ALL; return r; }
        if (has(n, "xu ly trung binh", "thoi gian xu ly", "mat bao lau", "bao lau", "trung binh")) { r.type = RESOLUTION_TIME; if (!r.rangeGiven) r.range = AnalysisRange.ALL; return r; }
        if (has(n, "ton dong", "lau nhat", "ton tai lau", "chua xu ly lau", "qua han", "cu nhat")) { r.type = OLDEST_OPEN; return r; }
        if (has(n, "lap lai", "nhieu nhat", "hay hong", "hong nhieu", "thiet bi nao", "he thong nao")) { r.type = TOP_EQUIPMENT; if (!r.rangeGiven) r.range = AnalysisRange.ALL; return r; }
        if (has(n, "muc do", "nghiem trong", "thong ke", "phan loai")) { r.type = SEVERITY_STATUS; if (!r.rangeGiven) r.range = AnalysisRange.ALL; return r; }
        if (has(n, "khoi luong", "cong viec theo kip", "kip nao lam", "viec cua kip")) { r.type = WORKLOAD_BY_CREW; return r; }
        if (has(n, "bat thuong moi", "moi phat sinh", "phat hien", "phat sinh", "co bao nhieu bat thuong")) { r.type = NEW_ANOMALIES; return r; }
        if (has(n, "tong quan", "tinh hinh", "tom tat", "bao cao nhanh", "hien trang")) { r.type = OVERVIEW; return r; }
        return r; // SEARCH
    }
}
