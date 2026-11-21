package vn.evnqt1.helpdesk.core;

import java.util.ArrayList;
import java.util.List;

/** Kết quả một phép phân tích của trợ lý offline: tiêu đề, các dòng tóm tắt, bảng (tùy chọn) và liên kết tới hồ sơ. */
public final class AnalysisResult {
    public final String title;
    public final List<String> lines = new ArrayList<>();
    public String[] header = new String[0];
    public final List<String[]> rows = new ArrayList<>();
    /** Mỗi phần tử {loại "ANOMALY"|"DRAWING", id, nhãn hiển thị}. */
    public final List<String[]> links = new ArrayList<>();
    public String linksTitle = "Hồ sơ liên quan";

    public AnalysisResult(String title) { this.title = title; }
    public AnalysisResult line(String s) { lines.add(s); return this; }
    public AnalysisResult table(String... h) { header = h; return this; }
    public AnalysisResult row(String... r) { rows.add(r); return this; }
    public AnalysisResult link(String type, String id, String label) { links.add(new String[]{type, id, label}); return this; }
    public boolean isEmpty() { return lines.isEmpty() && rows.isEmpty() && links.isEmpty(); }
}
