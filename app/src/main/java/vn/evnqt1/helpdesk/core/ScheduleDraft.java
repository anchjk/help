package vn.evnqt1.helpdesk.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bản nháp lịch ca của MỘT tháng, được đọc từ tệp Excel/CSV và cho phép người dùng
 * sửa trước khi lưu. Lớp thuần Java, không phụ thuộc Android để kiểm thử được bằng javac.
 */
public final class ScheduleDraft {
    public static final String[] CREWS = {"A", "B", "C", "D", "E"};
    public static final String[] SLOT_KEYS = {"MORNING", "AFTERNOON", "NIGHT"};
    public static final String[] SLOT_LABELS = {"Sáng", "Chiều", "Đêm"};
    public static final int SLOT_MORNING = 0, SLOT_AFTERNOON = 1, SLOT_NIGHT = 2;

    /** Một ngày: mã kíp cho 3 ca (null nếu không đọc được). */
    public static final class Day {
        public final int day;
        /** Thứ đọc được trên ảnh (CN, T2…T7) hoặc null. */
        public String weekday;
        public final String[] crew = new String[3];
        public Day(int day) { this.day = day; }
    }

    public int year, month;
    public Day[] days = new Day[0];
    /** Giờ ca dạng HH:mm; ca đêm kết thúc vào ngày hôm sau. */
    public final String[] slotStart = {"07:00", "15:00", "23:00"};
    public final String[] slotEnd = {"15:00", "23:00", "07:00"};
    /** Nhân sự từng kíp: "A" -> "Nguyễn A, Trần B". */
    public final Map<String, String> rosters = new LinkedHashMap<>();
    /** Ghi chú trong lúc đọc file (ví dụ giờ ca lấy từ file) để hiển thị cho người dùng. */
    public final List<String> notes = new ArrayList<>();

    public ScheduleDraft() {
        for (String c : CREWS) rosters.put(c, "");
    }

    /** Dựng lại mảng ngày theo số ngày của tháng, giữ dữ liệu cũ nếu trùng ngày. */
    public void resizeDays(int length) {
        Day[] next = new Day[length];
        for (int i = 0; i < length; i++) {
            next[i] = i < days.length && days[i] != null ? days[i] : new Day(i + 1);
        }
        days = next;
    }
}
