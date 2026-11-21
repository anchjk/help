package vn.evnqt1.helpdesk.core;

import java.text.Normalizer;
import java.util.Locale;

/** Vietnamese-aware normalization for local accent-insensitive search. */
public final class TextNormalizer {
    private TextNormalizer() {}
    public static String normalize(String value) {
        if (value == null) return "";
        String lower = value.trim().toLowerCase(Locale.ROOT).replace('đ', 'd');
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").replaceAll("\\s+", " ").trim();
    }
    public static boolean contains(String text, String query) {
        String q = normalize(query);
        return q.isEmpty() || normalize(text).contains(q);
    }
}
