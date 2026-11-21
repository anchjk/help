package vn.evnqt1.helpdesk.core;

import java.time.LocalDate;
import java.time.ZoneId;

/** Các khoảng thời gian chọn sẵn của trợ lý phân tích; tính theo giờ Việt Nam, nửa mở [from, to). */
public final class AnalysisRange {
    private AnalysisRange() {}

    public static final String[] LABELS = {"Hôm nay", "7 ngày qua", "30 ngày qua", "Tháng này", "Tháng trước", "Năm nay", "7 ngày tới", "Tháng sau", "Toàn bộ"};
    public static final int TODAY = 0, LAST7 = 1, LAST30 = 2, THIS_MONTH = 3, LAST_MONTH = 4, THIS_YEAR = 5, NEXT7 = 6, NEXT_MONTH = 7, ALL = 8;

    /** Trả về {from, to} tính bằng mili-giây. */
    public static long[] resolve(int id, long nowMillis, ZoneId zone) {
        LocalDate today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        LocalDate from, to;
        switch (id) {
            case TODAY: from = today; to = today.plusDays(1); break;
            case LAST7: from = today.minusDays(6); to = today.plusDays(1); break;
            case LAST30: from = today.minusDays(29); to = today.plusDays(1); break;
            case THIS_MONTH: from = today.withDayOfMonth(1); to = from.plusMonths(1); break;
            case LAST_MONTH: to = today.withDayOfMonth(1); from = to.minusMonths(1); break;
            case THIS_YEAR: from = today.withDayOfYear(1); to = from.plusYears(1); break;
            case NEXT7: from = today; to = today.plusDays(7); break;
            case NEXT_MONTH: from = today.withDayOfMonth(1).plusMonths(1); to = from.plusMonths(1); break;
            default: return new long[]{0, Long.MAX_VALUE};
        }
        return new long[]{from.atStartOfDay(zone).toInstant().toEpochMilli(), to.atStartOfDay(zone).toInstant().toEpochMilli()};
    }

    public static String label(int id) { return id >= 0 && id < LABELS.length ? LABELS[id] : LABELS[ALL]; }
}
