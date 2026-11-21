package vn.evnqt1.helpdesk.core;

import java.time.Instant;
import java.util.Objects;

/** Immutable, fully-expanded occurrence of one rostered shift. */
public final class ShiftOccurrence {
    public final String id;
    public final String shiftDate;
    public final String code;
    public final long startMillis;
    public final long endMillis;
    public final String roster;
    /** MORNING / AFTERNOON / NIGHT khi lịch nhập từ ảnh; null với ca nhập thủ công. */
    public final String slot;

    public ShiftOccurrence(String id, String shiftDate, String code, long startMillis,
                           long endMillis, String roster) {
        this(id, shiftDate, code, startMillis, endMillis, roster, null);
    }

    public ShiftOccurrence(String id, String shiftDate, String code, long startMillis,
                           long endMillis, String roster, String slot) {
        this.id = Objects.requireNonNull(id);
        this.shiftDate = Objects.requireNonNull(shiftDate);
        this.code = Objects.requireNonNull(code);
        if (endMillis <= startMillis) throw new IllegalArgumentException("endMillis must be after startMillis");
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.roster = roster == null ? "" : roster;
        this.slot = slot;
    }

    public boolean contains(long timeMillis) {
        return timeMillis >= startMillis && timeMillis < endMillis;
    }

    public boolean overlaps(ShiftOccurrence other) {
        return startMillis < other.endMillis && other.startMillis < endMillis;
    }

    public Instant startInstant() { return Instant.ofEpochMilli(startMillis); }
    public Instant endInstant() { return Instant.ofEpochMilli(endMillis); }
}
