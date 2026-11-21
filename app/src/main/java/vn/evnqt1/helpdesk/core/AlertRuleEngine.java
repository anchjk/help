package vn.evnqt1.helpdesk.core;

import java.util.ArrayList;
import java.util.List;

/** Deterministic local-only alert calculations. No scheduling or OS notifications are involved. */
public final class AlertRuleEngine {
    private AlertRuleEngine() {}

    public static final class AnomalySnapshot {
        public final String id, equipmentKey, symptomKey, status, severity;
        public final long detectedAt, lastUpdatedAt;
        public final boolean reopened;
        public AnomalySnapshot(String id, String equipmentKey, String symptomKey, String status,
                               String severity, long detectedAt, long lastUpdatedAt, boolean reopened) {
            this.id=id; this.equipmentKey=equipmentKey; this.symptomKey=symptomKey; this.status=status;
            this.severity=severity; this.detectedAt=detectedAt; this.lastUpdatedAt=lastUpdatedAt; this.reopened=reopened;
        }
    }
    public static final class Candidate {
        public final String anomalyId, type, reason, dedupeKey;
        public final int priority;
        public Candidate(String id, String type, String reason, int priority) {
            anomalyId=id; this.type=type; this.reason=reason; this.priority=priority;
            dedupeKey=id + "|" + type;
        }
    }

    public static List<Candidate> evaluate(List<AnomalySnapshot> rows, long now,
                                            long staleMs, long overdueMs, int repeatCount,
                                            long repeatWindowMs) {
        List<Candidate> out = new ArrayList<>();
        for (AnomalySnapshot a : rows) {
            boolean open = !"Đã xử lý".equals(a.status);
            if (open && overdueMs > 0 && now - a.detectedAt >= overdueMs)
                out.add(new Candidate(a.id, "OVERDUE", "Tồn tại " + days(now-a.detectedAt) + " ngày, vượt ngưỡng cấu hình", priority(a.severity)));
            if ("Đang xử lý".equals(a.status) && staleMs > 0 && now - a.lastUpdatedAt >= staleMs)
                out.add(new Candidate(a.id, "STALE", "Không có cập nhật trong " + days(now-a.lastUpdatedAt) + " ngày", priority(a.severity)));
            if (open && ("Cao".equalsIgnoreCase(a.severity) || "Nghiêm trọng".equalsIgnoreCase(a.severity)))
                out.add(new Candidate(a.id, "HIGH_OPEN", "Mức độ " + a.severity + " chưa hoàn thành", 1));
            if (a.reopened)
                out.add(new Candidate(a.id, "REOPENED", "Hồ sơ đã được mở lại do dấu hiệu tái diễn", 2));
        }
        if (repeatCount > 1 && repeatWindowMs > 0) {
            for (AnomalySnapshot current : rows) {
                if (now - current.detectedAt > repeatWindowMs) continue;
                int count = 0;
                for (AnomalySnapshot other : rows) {
                    if (now-other.detectedAt <= repeatWindowMs && equalKey(current.equipmentKey, other.equipmentKey)
                            && equalKey(current.symptomKey, other.symptomKey)) count++;
                }
                if (count >= repeatCount) {
                    out.add(new Candidate(current.id, "REPEAT", "Thiết bị/nhóm hiện tượng có " + count + " hồ sơ trong cửa sổ cấu hình", 2));
                }
            }
        }
        return out;
    }
    private static boolean equalKey(String a, String b) { return a != null && !a.trim().isEmpty() && a.equalsIgnoreCase(b == null ? "" : b.trim()); }
    private static long days(long millis) { return Math.max(0, millis / 86_400_000L); }
    private static int priority(String severity) { return "Nghiêm trọng".equalsIgnoreCase(severity) ? 1 : "Cao".equalsIgnoreCase(severity) ? 2 : 3; }
}
