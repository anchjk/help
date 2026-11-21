package vn.evnqt1.helpdesk.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import vn.evnqt1.helpdesk.R;

/** Biểu đồ vòng (donut) vẽ bằng Canvas: các phần theo màu, số lớn và nhãn nhỏ ở giữa. */
public final class DonutView extends View {
    private int[] values = new int[0], colors = new int[0];
    private String big = "", small = "";
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    public DonutView(Context c) {
        super(c);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.BUTT);
        txt.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(int[] values, int[] colors, String big, String small) {
        this.values = values; this.colors = colors; this.big = big; this.small = small;
        invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        int s = Ui.dp(getContext(), 128);
        setMeasuredDimension(s, s);
    }

    @Override protected void onDraw(Canvas cv) {
        Context c = getContext();
        float stroke = Ui.dp(c, 16), pad = stroke / 2 + Ui.dp(c, 2);
        oval.set(pad, pad, getWidth() - pad, getHeight() - pad);
        arc.setStrokeWidth(stroke);
        int total = 0;
        for (int v : values) total += v;
        if (total <= 0) {
            arc.setColor(c.getColor(R.color.surface_alt));
            cv.drawArc(oval, 0, 360, false, arc);
        } else {
            int parts = 0;
            for (int v : values) if (v > 0) parts++;
            float gap = parts > 1 ? 3f : 0f, start = -90f;
            for (int i = 0; i < values.length; i++) {
                if (values[i] <= 0) continue;
                float sweep = 360f * values[i] / total;
                arc.setColor(colors[i]);
                cv.drawArc(oval, start + gap / 2, Math.max(0.5f, sweep - gap), false, arc);
                start += sweep;
            }
        }
        txt.setColor(c.getColor(R.color.ink));
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        txt.setTextSize(Ui.dp(c, 28));
        cv.drawText(big, getWidth() / 2f, getHeight() / 2f + Ui.dp(c, 6), txt);
        txt.setColor(c.getColor(R.color.muted));
        txt.setTypeface(Typeface.DEFAULT);
        txt.setTextSize(Ui.dp(c, 11));
        cv.drawText(small, getWidth() / 2f, getHeight() / 2f + Ui.dp(c, 22), txt);
    }
}
