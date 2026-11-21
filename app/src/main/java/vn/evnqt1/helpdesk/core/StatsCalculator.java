package vn.evnqt1.helpdesk.core;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tính thống kê bất thường từ danh sách hồ sơ (thuần Java, kiểm thử được trên JVM). */
public final class StatsCalculator {
    private StatsCalculator() {}

    public static final String NO_SYSTEM = "(chưa nhập hệ thống)";

    public static StatsReport compute(List<StatsReport.Item> all, long from, long to, long now, ZoneId zone) {
        StatsReport r = new StatsReport();
        r.from = from; r.to = to; r.now = now;
        r.buckets = StatsBuckets.of(from, to, zone);
        r.newPerBucket = new int[r.buckets.size()];
        r.resolvedPerBucket = new int[r.buckets.size()];
        r.totalAnomalies = all.size();
        long span = Math.max(1, to - from), ageSum = 0, resolveSum = 0;
        for (StatsReport.Item it : all) {
            boolean done = !it.isOpen() && it.resolvedAt > 0;
            if (!it.isOpen()) r.totalResolvedAllTime++;
            if (it.isOpen()) {
                r.openTotal++;
                if ("Đang xử lý".equals(it.status)) r.openInProgress++; else r.openUnprocessed++;
                int s = severityIndex(it.severity);
                if (s >= 0) r.openBySeverity[s]++;
                ageSum += Math.max(0, now - it.detectedAt);
                r.openList.add(it);
            }
            if (it.detectedAt >= from && it.detectedAt < to) {
                if (it.category == null) it.category = AnomalyCategorizer.categorize(it.title, it.description);
                r.newList.add(it);
                if (it.isOpen()) r.newStillOpen++;
                int b = r.buckets.indexOf(it.detectedAt); if (b >= 0) r.newPerBucket[b]++;
            } else if (it.detectedAt >= from - span && it.detectedAt < from) r.prevNew++;
            if (done) {
                if (it.resolvedAt >= from && it.resolvedAt < to) {
                    r.resolvedList.add(it);
                    resolveSum += Math.max(0, it.resolvedAt - it.detectedAt);
                    if (it.reopened) r.reopenedInRange++;
                    int b = r.buckets.indexOf(it.resolvedAt); if (b >= 0) r.resolvedPerBucket[b]++;
                } else if (it.resolvedAt >= from - span && it.resolvedAt < from) r.prevResolved++;
            }
        }
        if (r.openTotal > 0) r.avgOpenAgeMs = ageSum / r.openTotal;
        if (!r.resolvedList.isEmpty()) r.avgResolveMs = resolveSum / r.resolvedList.size();
        Collections.sort(r.openList, (a, b) -> Long.compare(a.detectedAt, b.detectedAt)); // cũ nhất trước
        Collections.sort(r.newList, (a, b) -> Long.compare(b.detectedAt, a.detectedAt));
        Collections.sort(r.resolvedList, (a, b) -> Long.compare(b.resolvedAt, a.resolvedAt));
        group(r.byCategory, r.newList, 0);
        group(r.byEquipment, r.newList, 1);
        group(r.bySystem, r.newList, 2);
        return r;
    }

    private static int severityIndex(String s) { for (int i = 0; i < StatsReport.SEVERITIES.length; i++) if (StatsReport.SEVERITIES[i].equals(s)) return i; return -1; }

    /** by: 0 = loại, 1 = thiết bị (so không dấu/không phân biệt hoa thường), 2 = hệ thống/khu vực. */
    private static void group(List<StatsReport.Group> out, List<StatsReport.Item> items, int by) {
        Map<String, StatsReport.Group> m = new LinkedHashMap<>();
        for (StatsReport.Item it : items) {
            String name = by == 0 ? it.category : by == 1 ? clean(it.equipment, "(chưa nhập thiết bị)") : clean(it.system, NO_SYSTEM);
            String key = by == 0 ? name : TextNormalizer.normalize(name);
            StatsReport.Group g = m.get(key);
            if (g == null) { g = new StatsReport.Group(); g.name = name; m.put(key, g); }
            g.count++; if (it.isOpen()) g.open++; g.items.add(it);
        }
        out.addAll(m.values());
        Collections.sort(out, new Comparator<StatsReport.Group>() {
            @Override public int compare(StatsReport.Group a, StatsReport.Group b) {
                if (a.count != b.count) return Integer.compare(b.count, a.count);
                return a.name.compareTo(b.name);
            }
        });
    }

    private static String clean(String s, String fallback) { return s == null || s.trim().isEmpty() ? fallback : s.trim().replaceAll("\\s+", " "); }

    public static List<StatsReport.Group> top(List<StatsReport.Group> list, int n) { return new ArrayList<>(list.subList(0, Math.min(n, list.size()))); }
}
