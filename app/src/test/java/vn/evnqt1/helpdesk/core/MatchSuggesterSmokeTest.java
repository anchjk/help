package vn.evnqt1.helpdesk.core;

import java.util.ArrayList;
import java.util.List;

import vn.evnqt1.helpdesk.core.MatchSuggester.Candidate;
import vn.evnqt1.helpdesk.core.MatchSuggester.Match;

/** Kiểm thử gợi ý gán phiếu công việc vào bất thường / bản vẽ (thuần Java). */
public final class MatchSuggesterSmokeTest {
    private static int passed = 0;

    public static void main(String[] args) {
        List<Candidate> an = new ArrayList<>();
        an.add(new Candidate("A1", "Bơm cấp nước số 1", "10LAB10AP001", "Rò rỉ nước ở phớt bơm", true, 100));
        an.add(new Candidate("A2", "Bơm cấp nước số 2", "10LAB20AP001", "Rung cao ổ trục", true, 200));
        an.add(new Candidate("A3", "Quạt gió cấp 1A", "10HLA10AN001", "Quá nhiệt động cơ", true, 300));
        an.add(new Candidate("A4", "Bơm cấp nước số 1", "10LAB10AP001", "Đã xử lý lần trước", false, 50));
        an.add(new Candidate("A5", "Van điều khiển lưu lượng", "", "Kẹt van", true, 400));

        // 1. KKS trùng là tín hiệu mạnh nhất, kể cả khi tên thiết bị gõ khác.
        List<Match> m = MatchSuggester.rankAnomalies("bơm nước", "10LAB20AP001", "rung mạnh", an, 3);
        check(!m.isEmpty() && m.get(0).candidate.id.equals("A2") && m.get(0).reason.contains("Trùng mã KKS"), "KKS trùng thắng tên gần giống");
        // 2. Không dấu + KKS có khoảng trắng/gạch.
        m = MatchSuggester.rankAnomalies("bom cap nuoc so 1", "10 LAB-10 AP001", "ro ri phot", an, 3);
        check(m.get(0).candidate.id.equals("A1"), "không dấu + KKS có ký tự phân cách → hồ sơ đang mở được ưu tiên hơn hồ sơ đã xử lý");
        // 3. Chỉ có tên (không KKS): số hiệu phân biệt bơm 1 / bơm 2.
        m = MatchSuggester.rankAnomalies("Bơm cấp nước số 2", "", "kiểm tra", an, 3);
        check(m.get(0).candidate.id.equals("A2"), "số hiệu thiết bị phân biệt được bơm 1 và 2");
        // 4. Lỗi gõ.
        m = MatchSuggester.rankAnomalies("quat gio cap 1A", "", "nhiet do cao", an, 3);
        check(m.get(0).candidate.id.equals("A3"), "tên thiết bị gần giống");
        m = MatchSuggester.rankAnomalies("Van dieu khien luu luong", "", "kẹt", an, 3);
        check(m.get(0).candidate.id.equals("A5"), "hồ sơ không có KKS vẫn khớp theo tên");
        // 5. Không liên quan → không gợi ý.
        m = MatchSuggester.rankAnomalies("Máy biến áp tự dùng", "99XXX99", "thay dầu", an, 3);
        check(m.isEmpty(), "không có gợi ý khi không liên quan");
        // 6. Giới hạn số gợi ý.
        m = MatchSuggester.rankAnomalies("Bơm cấp nước", "", "", an, 2);
        check(m.size() <= 2, "giới hạn số gợi ý");

        List<Candidate> dr = new ArrayList<>();
        dr.add(new Candidate("D1", "EVN-QT1-ME-0123", "Sơ đồ nguyên lý hệ thống nước cấp", "Rev B", true, 10));
        dr.add(new Candidate("D2", "EVN-QT1-ME-0456", "Bản vẽ bố trí quạt gió", "", true, 20));
        dr.add(new Candidate("D3", "QT1/EL/0789", "Sơ đồ một sợi 6.6kV", "", true, 30));

        List<Match> d = MatchSuggester.rankDrawings("EVN QT1 ME 0123", "", "cập nhật van", dr, 3);
        check(d.get(0).candidate.id.equals("D1") && d.get(0).reason.contains("Trùng số bản vẽ"), "số bản vẽ trùng (bỏ khoảng trắng/gạch)");
        d = MatchSuggester.rankDrawings("EVN-QT1-ME-0124", "", "", dr, 3);
        check(d.get(0).candidate.id.equals("D1") || d.get(0).candidate.id.equals("D2"), "số bản vẽ lệch 1 ký tự vẫn được gợi ý");
        d = MatchSuggester.rankDrawings("EVN-QT1-ME-0123", "", "", dr, 3);
        check(d.get(0).candidate.id.equals("D1"), "số bản vẽ trùng không bị nhiễu");
        d = MatchSuggester.rankDrawings("", "so do mot soi 6.6kV", "đổi ký hiệu", dr, 3);
        check(d.get(0).candidate.id.equals("D3"), "chỉ có tên/tiêu đề, không dấu");
        d = MatchSuggester.rankDrawings("", "Bản vẽ bố trí quạt", "", dr, 3);
        check(d.get(0).candidate.id.equals("D2"), "tiêu đề gõ thiếu vẫn khớp");
        d = MatchSuggester.rankDrawings("ZZZ-000", "Mặt bằng nhà xưởng", "", dr, 3);
        check(d.isEmpty(), "bản vẽ không liên quan → không gợi ý");
        check(MatchSuggester.compact("10 LAB-10 AP.001").equals("10lab10ap001"), "compact");
        System.out.println("PASS: " + passed + " match-suggester assertions");
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError("FAIL: " + name);
        passed++;
    }
}
