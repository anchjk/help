package vn.evnqt1.helpdesk.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import vn.evnqt1.helpdesk.R;

/** Biểu đồ cột nhóm (hai chuỗi: phát sinh mới / đã xử lý) vẽ bằng Canvas, không thư viện ngoài. Chạm vào một cột để xem số liệu. */
public final class BarChartView extends View {
    public interface OnSelect { void onSelect(int index); }

    private String[] labels = new String[0];
    private int[] a = new int[0], b = new int[0];
    private int colorA, colorB;
    private int selected = -1;
    private OnSelect listener;
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG), grid = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG), hi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public BarChartView(Context c) {
        super(c);
        grid.setStrokeWidth(Math.max(1, Ui.dp(c, 1)));
        text.setTextSize(Ui.dp(c, 10.5f) );
        text.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public void setData(String[] labels, int[] a, int[] b, int colorA, int colorB) {
        this.labels = labels; this.a = a; this.b = b; this.colorA = colorA; this.colorB = colorB; this.selected = -1;
        invalidate();
    }
    public void setOnSelect(OnSelect l) { this.listener = l; }
    public int selectedIndex() { return selected; }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Ui.dp(getContext(), 220), w), Ui.dp(getContext(), 190));
    }

    private static int niceMax(int v) {
        if (v <= 4) return Math.max(2, v);
        int step = v <= 10 ? 2 : v <= 20 ? 5 : v <= 50 ? 10 : v <= 100 ? 20 : v <= 500 ? 100 : 500;
        return ((v + step - 1) / step) * step;
    }

    @Override protected void onDraw(Canvas cv) {
        Context c = getContext();
        int n = labels.length;
        int muted = c.getColor(R.color.muted), outline = c.getColor(R.color.outline), ink = c.getColor(R.color.ink);
        float left = Ui.dp(c, 28), top = Ui.dp(c, 16), right = Ui.dp(c, 4), bottom = Ui.dp(c, 22);
        float w = getWidth() - left - right, h = getHeight() - top - bottom;
        if (n == 0 || w <= 0) return;
        int max = 1;
        for (int i = 0; i < n; i++) max = Math.max(max, Math.max(a[i], b[i]));
        int top0 = niceMax(max);
        // lưới ngang: 0, giữa, trên cùng
        grid.setColor(outline);
        text.setColor(muted);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTypeface(Typeface.DEFAULT);
        int lines = top0 <= 4 ? top0 : 4;
        for (int i = 0; i <= lines; i++) {
            float y = top + h - h * i / lines;
            cv.drawLine(left, y, left + w, y, grid);
            cv.drawText(String.valueOf(Math.round((float) top0 * i / lines)), left - Ui.dp(c, 5), y + Ui.dp(c, 3.5f), text);
        }
        float group = w / n;
        float bw = Math.min(Ui.dp(c, 14), group * 0.36f);
        float gap = Math.max(1, bw * 0.12f);
        int labelStep = Math.max(1, (int) Math.ceil(n / Math.max(1f, w / Ui.dp(c, 36))));
        for (int i = 0; i < n; i++) {
            float cx = left + group * (i + 0.5f);
            if (i == selected) { hi.setColor(Ui.alpha(colorA, 0x22)); rect.set(left + group * i, top - Ui.dp(c, 4), left + group * (i + 1), top + h + Ui.dp(c, 2)); cv.drawRoundRect(rect, Ui.dp(c, 6), Ui.dp(c, 6), hi); }
            drawBar(cv, cx - gap / 2 - bw, cx - gap / 2, top, h, a[i], top0, colorA, ink, n <= 12);
            drawBar(cv, cx + gap / 2, cx + gap / 2 + bw, top, h, b[i], top0, colorB, ink, n <= 12);
            if (i % labelStep == 0 || i == selected) {
                text.setTextAlign(Paint.Align.CENTER);
                text.setColor(i == selected ? ink : muted);
                text.setTypeface(i == selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                cv.drawText(labels[i], cx, top + h + Ui.dp(c, 15), text);
            }
        }
    }

    private void drawBar(Canvas cv, float x0, float x1, float top, float h, int v, int max, int color, int ink, boolean value) {
        if (v <= 0) return;
        float bh = Math.max(Ui.dp(getContext(), 3), h * v / max);
        bar.setColor(color);
        rect.set(x0, top + h - bh, x1, top + h);
        float r = Math.min(Ui.dp(getContext(), 4), (x1 - x0) / 2);
        cv.drawRoundRect(rect, r, r, bar);
        if (value) {
            text.setColor(ink); text.setTextAlign(Paint.Align.CENTER); text.setTypeface(Typeface.DEFAULT_BOLD);
            cv.drawText(String.valueOf(v), (x0 + x1) / 2, top + h - bh - Ui.dp(getContext(), 3), text);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (labels.length == 0) return false;
        if (e.getAction() == MotionEvent.ACTION_DOWN) { getParent().requestDisallowInterceptTouchEvent(true); return true; }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            getParent().requestDisallowInterceptTouchEvent(false);
            float left = Ui.dp(getContext(), 28), w = getWidth() - left - Ui.dp(getContext(), 4);
            int i = (int) ((e.getX() - left) / (w / labels.length));
            if (i >= 0 && i < labels.length) { selected = i == selected ? -1 : i; invalidate(); if (listener != null) listener.onSelect(selected); performClick(); }
            return true;
        }
        return e.getAction() == MotionEvent.ACTION_MOVE || e.getAction() == MotionEvent.ACTION_CANCEL;
    }

    @Override public boolean performClick() { return super.performClick(); }
}
