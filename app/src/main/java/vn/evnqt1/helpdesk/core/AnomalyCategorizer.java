package vn.evnqt1.helpdesk.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Phân loại bất thường tự động theo từ khóa trong tiêu đề + mô tả (không dấu, thuần Java, offline).
 * Hồ sơ chưa có trường "loại" riêng nên đây chỉ là gợi ý để thống kê xu hướng: không kết luận nguyên nhân hỏng hóc.
 * Từ khóa viết dạng cụm để tránh nhầm khi bỏ dấu (ví dụ "vận hành" → "van hanh", "bản vẽ" → "ban ve").
 */
public final class AnomalyCategorizer {
    private AnomalyCategorizer() {}

    public static final String OTHER = "Khác / chưa phân loại";

    private static final String[][] RULES = {
            {"Rò rỉ", "ro ri", "bi ri", "ri nuoc", "ri dau", "ri hoi", "ri khi", "ri mo", "ro nuoc", "ro dau", "ro hoi", "ro khi", "chay nuoc", "chay dau", "tham nuoc", "tham dau", "ri phot", "phot ro"},
            {"Rung / ồn / tiếng kêu", "rung", "rung lac", "rung cao", "tieng on", "on bat thuong", "tieng keu", "keu bat thuong", "keu to", "on to"},
            {"Nhiệt độ bất thường", "nhiet do", "nhiet", "qua nhiet", "nong", "qua nong", "tang nhiet", "nhiet cao"},
            {"Áp suất / lưu lượng", "ap suat", "ap luc", "luu luong", "tut ap", "tang ap", "giam ap", "qua ap", "chenh ap"},
            {"Điện / bảo vệ / tín hiệu", "mat dien", "nhay may", "nhay bao ve", "trip", "bao ve", "tin hieu", "cam bien", "sensor", "ro dien", "cach dien", "relay", "tiep dia", "dien ap", "dong dien", "chap dien", "bien tan", "mat nguon"},
            {"Van / điều khiển / cơ cấu chấp hành", "van", "ket van", "khong dong mo", "khong mo", "khong dong", "actuator", "dieu khien", "servo", "damper", "bo dieu khien", "ket cua"},
            {"Cơ khí / mài mòn", "mon", "mai mon", "bi mon", "gay", "nut", "lech tam", "lech truc", "vong bi", "bac lot", "goi do", "bi ket", "ket cung", "bu long", "bien dang"},
            {"Tắc nghẽn / bám bẩn", "tac nghen", "bi tac", "tac loc", "nghen", "bam ban", "bam bui", "bam tro", "can ban", "loc ban"},
            {"Dầu / bôi trơn", "thieu dau", "muc dau", "boi tron", "dau nhot", "nhot", "mo boi tron", "thay dau", "dau thuy luc", "dau lam mat"},
            {"Gỉ sét / ăn mòn", "gi set", "ri set", "an mon", "oxi hoa", "oxy hoa"},
            {"Cháy / khói / phóng điện", "boc khoi", "tia lua", "chay no", "hoa hoan", "phong dien", "khoi den", "mui khet"},
            {"Sai lệch thông số / đo lường", "sai lech", "lech thong so", "chi thi", "chi so", "doc sai", "sai so", "khong chinh xac", "dao dong", "khong on dinh", "thong so"},
    };
    /** Cụm dễ gây nhầm khi bỏ dấu: bị loại khỏi văn bản trước khi so khớp. */
    private static final String[] NOISE = {"van hanh", "van de", "van chuyen", "van toc", "tai lieu", "ban ve"};

    public static List<String> names() {
        List<String> out = new ArrayList<>();
        for (String[] r : RULES) out.add(r[0]);
        out.add(OTHER);
        return out;
    }

    /** Trả về tên loại phù hợp nhất; {@link #OTHER} nếu không có từ khóa nào khớp. */
    public static String categorize(String title, String description) {
        String t = prepare(title), d = prepare(description);
        int best = 0; String bestName = OTHER;
        for (String[] rule : RULES) {
            int score = 0;
            for (int i = 1; i < rule.length; i++) {
                int w = 1 + countWords(rule[i]);
                String kw = " " + rule[i] + " ";
                if (t.contains(kw)) score += 2 * w;
                if (d.contains(kw)) score += w;
            }
            if (score > best) { best = score; bestName = rule[0]; }
        }
        return bestName;
    }

    private static String prepare(String s) {
        String n = " " + TextNormalizer.normalize(s).replaceAll("[^a-z0-9]+", " ").trim() + " ";
        for (String x : NOISE) n = n.replace(" " + x + " ", "  ");
        return n;
    }

    private static int countWords(String s) { return s.split(" ").length - 1; }
}
