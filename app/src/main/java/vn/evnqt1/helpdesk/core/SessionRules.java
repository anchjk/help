package vn.evnqt1.helpdesk.core;

/** Pure business rules for grouping work entries into a single session per entity/shift occurrence. */
public final class SessionRules {
    private SessionRules() {}

    public static boolean sameShiftOccurrence(String existingOccurrenceId, String requestedOccurrenceId) {
        return existingOccurrenceId != null && requestedOccurrenceId != null
                && existingOccurrenceId.equals(requestedOccurrenceId);
    }

    public static int nextSessionNumber(int maxExistingNumber) {
        return Math.max(0, maxExistingNumber) + 1;
    }
}
