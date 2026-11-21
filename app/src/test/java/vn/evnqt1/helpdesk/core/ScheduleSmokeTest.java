package vn.evnqt1.helpdesk.core;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Kiểm thử đọc lịch trực từ Excel/CSV hoàn toàn offline (không AI): dùng chính tệp mẫu docs/mau/Lich_Truc_Ca_Thang_11_2026.xlsx.
 * Chạy: ./test-core.sh
 */
public final class ScheduleSmokeTest {
    private static final String[] WD = "CN,T2,T3,T4,T5,T6,T7".split(",");
    private static final String MOR = "B,B,C,C,D,D,E,E,A,A,B,B,C,C,D,D,E,E,A,A,B,B,C,C,D,D,E,E,A,A";
    private static final String AFT = "C,D,D,E,E,A,A,B,B,C,C,D,D,E,E,A,A,B,B,C,C,D,D,E,E,A,A,B,B,C";
    private static final String NIG = "A,A,B,B,C,C,D,D,E,E,A,A,B,B,C,C,D,D,E,E,A,A,B,B,C,C,D,D,E,E";

    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "docs/mau/Lich_Truc_Ca_Thang_11_2026.xlsx";
        List<String[][]> sheets;
        try (InputStream in = Files.newInputStream(Paths.get(path))) { sheets = XlsxReader.read(in); }
        check(sheets.size() == 1, "đọc được 1 sheet từ tệp .xlsx");

        String[] defS = {"07:00", "15:00", "23:00"}, defE = {"15:00", "23:00", "07:00"};
        ScheduleDraft d = ScheduleSheetParser.parseSheets(sheets, 2026, 1, defS, defE);
        check(d.year == 2026 && d.month == 11, "tháng/năm lấy từ dòng \"(Từ 01/11/2026 đến 30/11/2026)\"");
        check(d.days.length == 30, "30 ngày");
        String[] m = MOR.split(","), a = AFT.split(","), n = NIG.split(",");
        for (int i = 0; i < 30; i++) {
            check(m[i].equals(d.days[i].crew[0]) && a[i].equals(d.days[i].crew[1]) && n[i].equals(d.days[i].crew[2]), "ngày " + (i + 1) + " đúng Sáng/Chiều/Đêm");
            check(WD[i % 7].equals(d.days[i].weekday), "ngày " + (i + 1) + " đúng thứ");
        }
        check("Nguyễn Anh Quân, Trần Xuân An, Nguyễn Văn Tuấn".equals(d.rosters.get("A")), "nhân sự kíp A: " + d.rosters.get("A"));
        check(d.rosters.get("C").contains("Nguyễn Văn Dũng(DP)"), "giữ chú thích (DP)");
        check("Phạm Thị Thủy".equals(d.rosters.get("E").split(", ")[2]), "nhân sự kíp E");
        for (String c : ScheduleDraft.CREWS) check(d.rosters.get(c).split(", ").length == 3, "mỗi kíp 3 người: " + c);
        check(ScheduleRules.validate(d).isEmpty(), "khớp lịch thật, không cảnh báo: " + ScheduleRules.validate(d));

        // Giờ ca theo cài đặt của người dùng
        ScheduleDraft custom = ScheduleSheetParser.parseSheets(sheets, 2026, 1, new String[]{"06:00", "14:00", "22:00"}, new String[]{"14:00", "22:00", "06:00"});
        check("06:00".equals(custom.slotStart[0]) && "22:00".equals(custom.slotStart[2]) && "06:00".equals(custom.slotEnd[2]), "giờ ca lấy theo cài đặt");
        List<ScheduleRules.Occurrence> occ = ScheduleRules.expand(d);
        check(occ.size() == 90, "30 ngày x 3 ca = 90 lần ca");
        Collections.sort(occ, new Comparator<ScheduleRules.Occurrence>() { public int compare(ScheduleRules.Occurrence x, ScheduleRules.Occurrence y) { return Long.compare(x.startMillis, y.startMillis); } });
        for (int i = 0; i + 1 < occ.size(); i++) check(occ.get(i).endMillis == occ.get(i + 1).startMillis, "ca liền mạch tại #" + i);
        List<ShiftOccurrence> shifts = new ArrayList<>();
        int k = 0;
        for (ScheduleRules.Occurrence o : occ) shifts.add(new ShiftOccurrence("s" + (k++), o.date, o.code, o.startMillis, o.endMillis, o.roster, o.slot));
        check(crewAt(shifts, LocalDateTime.of(2026, 11, 1, 8, 0)).equals("B"), "08:00 1/11 -> B");
        check(crewAt(shifts, LocalDateTime.of(2026, 11, 2, 3, 0)).equals("A"), "03:00 2/11 -> A (ca đêm qua ngày)");
        check(crewAt(shifts, LocalDateTime.of(2026, 11, 19, 16, 0)).equals("B"), "16:00 19/11 -> B");

        // CSV (dấu chấm phẩy, có ngoặc kép) cho cùng bố cục
        StringBuilder csv = new StringBuilder("\uFEFF\"LỊCH TRỰC THÁNG 11/2026\";\n\"(Từ 01/11/2026 đến 30/11/2026)\"\n");
        String[][] g = sheets.get(0);
        for (String[] row : g) { StringBuilder line = new StringBuilder(); for (int c = 0; c < row.length; c++) { if (c > 0) line.append(';'); line.append('"').append(row[c].replace("\"", "\"\"")).append('"'); } csv.append(line).append('\n'); }
        ScheduleDraft fromCsv = ScheduleSheetParser.parse(CsvReader.parse(csv.toString()), 2000, 1, defS, defE);
        check(fromCsv.month == 11 && fromCsv.days.length == 30 && "B".equals(fromCsv.days[0].crew[0]) && fromCsv.rosters.get("B").contains("Trần Xuân Lộc"), "đọc được CSV cùng bố cục");

        // Bố cục xoay dọc: mỗi hàng một ngày
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Tháng 11/2026", "", "", "", ""});
        rows.add(new String[]{"Ngày", "Thứ", "Sáng", "Chiều", "Đêm"});
        for (int i = 0; i < 30; i++) rows.add(new String[]{String.valueOf(i + 1), WD[i % 7], m[i], a[i], n[i]});
        rows.add(new String[]{"Kíp A: Nguyễn A, Trần B", "", "", "", ""});
        rows.add(new String[]{"Thời gian đi ca: Ca sáng từ 6:30 tới 14:30, ca chiều từ 14:30 tới 22:30, ca tối từ 22:30 tới 6:30 ngày hôm sau", "", "", "", ""});
        ScheduleDraft vert = ScheduleSheetParser.parse(rows.toArray(new String[0][]), 2000, 1, defS, defE);
        check(vert.year == 2026 && vert.month == 11 && "E".equals(vert.days[6].crew[0]) && "A".equals(vert.days[0].crew[2]), "đọc được bố cục dọc");
        check("Nguyễn A, Trần B".equals(vert.rosters.get("A")), "nhân sự ghi cùng dòng tiêu đề kíp");
        check("06:30".equals(vert.slotStart[0]) && "14:30".equals(vert.slotEnd[0]) && "22:30".equals(vert.slotStart[2]) && "06:30".equals(vert.slotEnd[2]), "giờ ca ghi trong tệp ghi đè mặc định");

        // Lỗi dữ liệu
        ScheduleDraft wrong = ScheduleSheetParser.parseSheets(sheets, 2026, 1, defS, defE);
        wrong.month = 10; wrong.resizeDays(31);
        check(ScheduleRules.validate(wrong).stream().anyMatch(s -> s.contains("chọn sai tháng")), "phát hiện sai tháng/năm");
        ScheduleDraft bad = ScheduleSheetParser.parseSheets(sheets, 2026, 1, defS, defE);
        bad.days[4].crew[1] = bad.days[4].crew[0]; bad.days[6].crew[2] = null;
        List<String> issues = ScheduleRules.validate(bad);
        check(issues.stream().anyMatch(s -> s.startsWith("Ngày 5") && s.contains("bị gán cho cả")) && issues.stream().anyMatch(s -> s.startsWith("Ngày 7") && s.contains("thiếu mã kíp")), "báo trùng/thiếu kíp");
        boolean rejected = false;
        try { ScheduleSheetParser.parse(new String[][]{{"Họ tên", "Số điện thoại"}, {"A", "1"}}, 2026, 11, defS, defE); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "tệp không phải lịch ca bị từ chối");
        System.out.println("PASS: schedule import (Excel/CSV, offline) assertions");
    }

    private static String crewAt(List<ShiftOccurrence> shifts, LocalDateTime t) {
        ShiftResolver.Result r = ShiftResolver.resolve(t.atZone(ScheduleRules.ZONE).toInstant().toEpochMilli(), shifts);
        return r.kind == ShiftResolver.Result.Kind.MATCHED ? r.occurrence.code : "?" + r.kind;
    }
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
}
