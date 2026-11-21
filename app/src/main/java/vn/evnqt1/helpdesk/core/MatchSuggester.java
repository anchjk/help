package vn.evnqt1.helpdesk.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gợi ý hồ sơ liên quan nhất cho một phiếu ghi công việc (thuần Java, chạy offline, không phụ thuộc Android).
 * - Bất thường: so tên thiết bị + mã KKS (KKS trùng là tín hiệu mạnh nhất), có chịu lỗi gõ và không phân biệt dấu tiếng Việt.
 * - Bản vẽ: so số bản vẽ (bỏ khoảng trắng/gạch/dấu chấm) hoặc tên/tiêu đề bản vẽ.
 */
public final class MatchSuggester {
    private MatchSuggester() {}

    /** Điểm tối thiểu để một hồ sơ được coi là "có gợi ý". */
    public static final int MIN_SCORE = 25;
    public static final int STRONG_SCORE = 90;

    /** Hồ sơ ứng viên: a = tên thiết bị / số bản vẽ; b = KKS / tên-tiêu đề; text = văn bản phụ (tiêu đề, mô tả...). */
    public static final class Candidate {
        public final String id, a, b, text;
        public final boolean open;
        public final long updatedAt;
        public Candidate(String id, String a, String b, String text, boolean open, long updatedAt) {
            this.id = id; this.a = a == null ? "" : a; this.b = b == null ? "" : b; this.text = text == null ? "" : text;
            this.open = open; this.updatedAt = updatedAt;
        }
    }

    public static final class Match {
        public final Candidate candidate;
        public final int score;
        public final String reason;
        Match(Candidate c, int score, String reason) { this.candidate = c; this.score = score; this.reason = reason; }
    }

    // ------------------------------------------------------------------ bất thường
    public static List<Match> rankAnomalies(String equipment, String kks, String content, List<Candidate> list, int max) {
        String kq = compact(kks);
        List<String> nameTokens = tokens(equipment), contentTokens = significant(tokens(content));
        List<Match> out = new ArrayList<>();
        for (Candidate c : list) {
            List<String> why = new ArrayList<>();
            double score = 0;
            String ck = compact(c.b);
            boolean kksKnown = !kq.isEmpty() && !ck.isEmpty();
            boolean kksRelated = false;
            if (kksKnown) {
                if (ck.equals(kq)) { score += 100; kksRelated = true; why.add("Trùng mã KKS"); }
                else if (kq.length() >= 4 && ck.length() >= 4 && (ck.contains(kq) || kq.contains(ck))) { score += 50; kksRelated = true; why.add("KKS gần giống"); }
                else {
                    int p = commonPrefix(kq, ck);
                    if (p >= 8) { score += 40; kksRelated = true; why.add("KKS cùng tiền tố"); }
                    else if (p >= 5) { score += 20; kksRelated = true; why.add("KKS cùng hệ thống"); }
                }
            }
            double name = Math.max(similarity(nameTokens, tokens(c.a)), 0.8 * similarity(nameTokens, tokens(c.text)));
            String nq = String.join(" ", nameTokens), nc = String.join(" ", tokens(c.a));
            if (nq.length() >= 4 && nc.length() >= 4 && (nc.contains(nq) || nq.contains(nc))) name = Math.max(name, 0.85);
            double nameScore = 60 * name;
            // Có KKS ở cả hai phía nhưng khác hẳn → nhiều khả năng là thiết bị khác cùng loại (bơm A/bơm B).
            if (kksKnown && !kksRelated) nameScore *= 0.7;
            if (nameScore >= 15) why.add(name >= 0.9 ? "Tên thiết bị trùng khớp" : "Tên thiết bị gần giống");
            score += nameScore;
            double ct = contentOverlap(contentTokens, tokens(c.a + " " + c.text));
            if (ct > 0) { score += 15 * ct; if (ct >= 0.3) why.add("Nội dung liên quan"); }
            score += c.open ? 5 : -5;
            add(out, c, score, why);
        }
        return top(out, max);
    }

    // ------------------------------------------------------------------ bản vẽ
    public static List<Match> rankDrawings(String number, String title, String content, List<Candidate> list, int max) {
        String nq = compact(number);
        List<String> titleTokens = tokens(title), contentTokens = significant(tokens(content));
        List<Match> out = new ArrayList<>();
        for (Candidate c : list) {
            List<String> why = new ArrayList<>();
            double score = 0;
            String cn = compact(c.a);
            if (!nq.isEmpty() && !cn.isEmpty()) {
                if (cn.equals(nq)) { score += 100; why.add("Trùng số bản vẽ"); }
                else if (nq.length() >= 4 && cn.length() >= 4 && (cn.contains(nq) || nq.contains(cn))) { score += 65; why.add("Số bản vẽ gần giống"); }
                else if (nq.length() >= 6 && cn.length() >= 6 && levenshtein(nq, cn, 1) <= 1) { score += 55; why.add("Số bản vẽ lệch 1 ký tự"); }
                else if (commonPrefix(nq, cn) >= 6) { score += 25; why.add("Số bản vẽ cùng tiền tố"); }
            }
            if (!titleTokens.isEmpty()) {
                double t = Math.max(similarity(titleTokens, tokens(c.b)), 0.8 * similarity(titleTokens, tokens(c.text)));
                String tq = String.join(" ", titleTokens), tc = String.join(" ", tokens(c.b));
                if (tq.length() >= 4 && tc.length() >= 4 && (tc.contains(tq) || tq.contains(tc))) t = Math.max(t, 0.85);
                // Người dùng có thể gõ số bản vẽ vào ô tên/tiêu đề.
                String tqc = compact(title);
                if (tqc.length() >= 4 && !cn.isEmpty() && (cn.equals(tqc) || cn.contains(tqc))) t = Math.max(t, 0.9);
                if (t > 0.15) { score += 60 * t * (nq.isEmpty() ? 1.0 : 0.6); why.add(t >= 0.9 ? "Tên/tiêu đề trùng khớp" : "Tên/tiêu đề gần giống"); }
            }
            double ct = contentOverlap(contentTokens, tokens(c.b + " " + c.text));
            if (ct > 0) { score += 10 * ct; if (ct >= 0.3) why.add("Nội dung liên quan"); }
            add(out, c, score, why);
        }
        return top(out, max);
    }

    // ------------------------------------------------------------------ chung
    private static void add(List<Match> out, Candidate c, double score, List<String> why) {
        int s = (int) Math.round(score);
        if (s < MIN_SCORE) return;
        out.add(new Match(c, s, String.join(" · ", why)));
    }

    private static List<Match> top(List<Match> list, int max) {
        Collections.sort(list, (x, y) -> x.score != y.score ? Integer.compare(y.score, x.score) : Long.compare(y.candidate.updatedAt, x.candidate.updatedAt));
        return list.size() > max ? new ArrayList<>(list.subList(0, max)) : list;
    }

    /** Chuẩn hóa không dấu, chỉ giữ chữ-số: "10 LAB-10 AP.001" → "10lab10ap001". */
    public static String compact(String s) { return TextNormalizer.normalize(s).replaceAll("[^a-z0-9]", ""); }

    private static final Set<String> STOP = new HashSet<>(java.util.Arrays.asList(
            "so", "cua", "va", "bi", "tai", "o", "la", "cho", "cac", "nhung", "mot", "co", "khong", "da", "dang", "the", "may", "thiet", "bi"));

    static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        for (String t : TextNormalizer.normalize(s).split("[^a-z0-9]+")) if (!t.isEmpty()) out.add(t);
        return out;
    }

    private static List<String> significant(List<String> t) {
        List<String> out = new ArrayList<>();
        for (String x : t) if (x.length() >= 3 && !STOP.contains(x)) out.add(x);
        return out;
    }

    /** Dice trên token có chịu lỗi gõ: 1 = trùng hoàn toàn. Số hiệu ("1", "2", "a") phải khớp chính xác. */
    static double similarity(List<String> q, List<String> c) {
        if (q.isEmpty() || c.isEmpty()) return 0;
        boolean[] used = new boolean[c.size()];
        double matched = 0;
        for (String a : q) {
            int best = -1; double bv = 0;
            for (int i = 0; i < c.size(); i++) {
                if (used[i]) continue;
                double v = tokenMatch(a, c.get(i));
                if (v > bv) { bv = v; best = i; }
            }
            if (best >= 0) { used[best] = true; matched += bv; }
        }
        return 2 * matched / (q.size() + c.size());
    }

    private static double tokenMatch(String a, String b) {
        if (a.equals(b)) return 1;
        if (a.length() < 3 || b.length() < 3) return 0; // token ngắn (số hiệu) chỉ khớp tuyệt đối
        if (a.length() >= 3 && b.startsWith(a) || b.length() >= 3 && a.startsWith(b)) return 0.8;
        int len = Math.min(a.length(), b.length());
        if (len >= 4 && levenshtein(a, b, 1) <= 1) return 0.8;
        if (len >= 7 && levenshtein(a, b, 2) <= 2) return 0.7;
        return 0;
    }

    /** Tỷ lệ từ khóa của nội dung xuất hiện trong văn bản hồ sơ (0..1), tối đa tính 4 từ. */
    private static double contentOverlap(List<String> contentTokens, List<String> recordTokens) {
        if (contentTokens.isEmpty() || recordTokens.isEmpty()) return 0;
        int hit = 0;
        for (String t : new HashSet<>(contentTokens)) {
            for (String r : recordTokens) if (r.length() >= 3 && tokenMatch(t, r) >= 0.8) { hit++; break; }
        }
        return Math.min(1.0, hit / 4.0);
    }

    private static int commonPrefix(String a, String b) {
        int n = Math.min(a.length(), b.length()), i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    /** Khoảng cách chỉnh sửa; trả về limit+1 nếu vượt limit (dừng sớm). */
    static int levenshtein(String a, String b, int limit) {
        if (Math.abs(a.length() - b.length()) > limit) return limit + 1;
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i; int rowMin = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, cur[j]);
            }
            if (rowMin > limit) return limit + 1;
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }
}
