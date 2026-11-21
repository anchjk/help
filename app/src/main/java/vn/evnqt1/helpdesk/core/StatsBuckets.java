package vn.evnqt1.helpdesk.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Chia khoảng thời gian [from, to) thành các cột biểu đồ: theo ngày (≤ 31 ngày), theo tuần (≤ 140 ngày), còn lại theo tháng/quý. */
public final class StatsBuckets {
    public static final int DAY = 0, WEEK = 1, MONTH = 2, QUARTER = 3;
    public final int unit;
    /** Mốc bắt đầu của từng cột (mili-giây); cột i kết thúc ở starts[i+1], cột cuối kết thúc ở {@link #end}. */
    public final long[] starts;
    public final String[] labels;
    public final long end;

    private StatsBuckets(int unit, long[] starts, String[] labels, long end) { this.unit = unit; this.starts = starts; this.labels = labels; this.end = end; }

    public int size() { return starts.length; }

    /** Chỉ số cột chứa thời điểm t; -1 nếu ngoài khoảng. */
    public int indexOf(long t) {
        if (starts.length == 0 || t < starts[0] || t >= end) return -1;
        for (int i = starts.length - 1; i >= 0; i--) if (t >= starts[i]) return i;
        return -1;
    }

    public String unitLabel() { return unit == DAY ? "ngày" : unit == WEEK ? "tuần" : unit == MONTH ? "tháng" : "quý"; }

    public static StatsBuckets of(long from, long to, ZoneId zone) {
        LocalDate first = Instant.ofEpochMilli(from).atZone(zone).toLocalDate();
        LocalDate last = Instant.ofEpochMilli(Math.max(from, to - 1)).atZone(zone).toLocalDate();
        long days = ChronoUnit.DAYS.between(first, last) + 1;
        List<LocalDate> st = new ArrayList<>();
        int unit;
        if (days <= 31) { unit = DAY; for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) st.add(d); }
        else if (days <= 140) { unit = WEEK; for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(7)) st.add(d); }
        else {
            long months = ChronoUnit.MONTHS.between(first.withDayOfMonth(1), last.withDayOfMonth(1)) + 1;
            int step = months > 24 ? 3 : 1;
            unit = step == 3 ? QUARTER : MONTH;
            st.add(first);
            LocalDate d = first.withDayOfMonth(1).plusMonths(step);
            while (!d.isAfter(last)) { st.add(d); d = d.plusMonths(step); }
        }
        long[] starts = new long[st.size()];
        String[] labels = new String[st.size()];
        for (int i = 0; i < starts.length; i++) {
            LocalDate d = st.get(i);
            starts[i] = d.atStartOfDay(zone).toInstant().toEpochMilli();
            labels[i] = unit == MONTH || unit == QUARTER ? String.format("%02d/%02d", d.getMonthValue(), d.getYear() % 100) : String.format("%02d/%02d", d.getDayOfMonth(), d.getMonthValue());
        }
        return new StatsBuckets(unit, starts, labels, to);
    }
}
