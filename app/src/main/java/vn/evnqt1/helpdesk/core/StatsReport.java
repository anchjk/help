package vn.evnqt1.helpdesk.core;

import java.util.ArrayList;
import java.util.List;

/** Kết quả thống kê bất thường trong khoảng [from, to) cùng tình trạng tồn đọng tại thời điểm {@code now}. */
public final class StatsReport {
    public static final String[] SEVERITIES = {"Nghiêm trọng", "Cao", "Trung bình", "Thấp"};
    public static final String DONE = "Đã xử lý";

    /** Một bất thường (dữ liệu tối thiểu để thống kê). */
    public static final class Item {
        public String id, code, title, description, equipment, kks, system, severity, status, category;
        public long detectedAt, resolvedAt; // resolvedAt = 0 nếu chưa đóng
        public boolean reopened;
        public boolean isOpen() { return !DONE.equals(status); }
    }

    /** Nhóm đếm: theo loại, thiết bị hoặc hệ thống. */
    public static final class Group {
        public String name;
        public int count, open;
        public final List<Item> items = new ArrayList<>();
    }

    public long from, to, now;
    public StatsBuckets buckets;

    // Đang tồn tại tại thời điểm hiện tại (không phụ thuộc khoảng chọn)
    public int openTotal, openUnprocessed, openInProgress;
    public final int[] openBySeverity = new int[4];
    public long avgOpenAgeMs;
    public final List<Item> openList = new ArrayList<>();

    // Trong khoảng chọn
    public final List<Item> newList = new ArrayList<>();
    public final List<Item> resolvedList = new ArrayList<>();
    public int newStillOpen, reopenedInRange;
    public long avgResolveMs = -1;
    public int[] newPerBucket, resolvedPerBucket;
    public int prevNew, prevResolved; // khoảng liền trước, cùng độ dài
    public int totalAnomalies, totalResolvedAllTime;

    public final List<Group> byCategory = new ArrayList<>(), byEquipment = new ArrayList<>(), bySystem = new ArrayList<>();

    public int net() { return newList.size() - resolvedList.size(); }
}
