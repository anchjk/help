package vn.evnqt1.helpdesk.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resolves work time against actual shift occurrence intervals; supports overnight shifts. */
public final class ShiftResolver {
    private ShiftResolver() {}

    public static final class Result {
        public enum Kind { MATCHED, NOT_FOUND, OVERLAP }
        public final Kind kind;
        public final ShiftOccurrence occurrence;
        public final List<ShiftOccurrence> candidates;
        private Result(Kind kind, ShiftOccurrence occurrence, List<ShiftOccurrence> candidates) {
            this.kind = kind; this.occurrence = occurrence;
            this.candidates = Collections.unmodifiableList(candidates);
        }
    }

    public static Result resolve(long workAt, List<ShiftOccurrence> shifts) {
        List<ShiftOccurrence> matches = new ArrayList<>();
        for (ShiftOccurrence shift : shifts) if (shift.contains(workAt)) matches.add(shift);
        if (matches.isEmpty()) return new Result(Result.Kind.NOT_FOUND, null, matches);
        if (matches.size() > 1) return new Result(Result.Kind.OVERLAP, null, matches);
        return new Result(Result.Kind.MATCHED, matches.get(0), matches);
    }
}
