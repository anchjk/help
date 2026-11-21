package vn.evnqt1.helpdesk.core;

import java.util.Arrays;
import java.util.Collections;

/** Executable without Android/JUnit: javac + java ...CoreSmokeTest. */
public final class CoreSmokeTest {
    public static void main(String[] args) {
        check(TextNormalizer.contains("Bơm cấp nước", "bom cap nuoc"), "Vietnamese accent search");
        check(TextNormalizer.contains("Động cơ", "dong co"), "Vietnamese đ search");
        long h = 3_600_000L;
        ShiftOccurrence overnight = new ShiftOccurrence("occ-1", "2026-11-18", "C", 22*h, 30*h, "A;B");
        check(ShiftResolver.resolve(25*h, Collections.singletonList(overnight)).kind == ShiftResolver.Result.Kind.MATCHED,
                "overnight interval");
        ShiftOccurrence sameCodeNextDate = new ShiftOccurrence("occ-2", "2026-11-19", "C", 46*h, 54*h, "C;D");
        check(!SessionRules.sameShiftOccurrence(overnight.id, sameCodeNextDate.id), "same shift code different occurrence");
        check(SessionRules.sameShiftOccurrence("x", "x"), "same occurrence groups");
        check(SessionRules.nextSessionNumber(3) == 4, "session sequence");
        ShiftOccurrence overlapping = new ShiftOccurrence("occ-3", "2026-11-19", "D", 24*h, 28*h, "E;F");
        check(ShiftResolver.resolve(25*h, Arrays.asList(overnight, overlapping)).kind == ShiftResolver.Result.Kind.OVERLAP, "overlap requires manual choice");
        check(!SessionRules.sameShiftOccurrence(null, null), "unassigned shifts must not be assumed same occurrence");
        AlertRuleEngine.AnomalySnapshot a = new AlertRuleEngine.AnomalySnapshot("ABN-1", "pump", "noise", "Đang xử lý", "Cao", 1, 1, false);
        check(!AlertRuleEngine.evaluate(Collections.singletonList(a), 10_000_000_000L, 1, 1, 3, 100).isEmpty(), "alert rules");
        AlertRuleEngine.AnomalySnapshot reopened = new AlertRuleEngine.AnomalySnapshot("ABN-2", "pump", "noise", "Đang xử lý", "Trung bình", 1, 2, true);
        check(AlertRuleEngine.evaluate(Collections.singletonList(reopened), 10_000_000_000L, 0, 0, 99, 1).stream().anyMatch(x -> "REOPENED".equals(x.type)), "reopened alert");
        System.out.println("PASS: 10 core assertions");
    }
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
}
