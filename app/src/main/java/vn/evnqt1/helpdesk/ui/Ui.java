package vn.evnqt1.helpdesk.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import vn.evnqt1.helpdesk.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Bộ thành phần giao diện dùng chung (không thư viện ngoài): thẻ, chip, nút, ô nhập, chọn ngày giờ. Màu lấy từ resource nên tự đổi theo chế độ sáng/tối. */
public final class Ui {
    private Ui() {}

    public static final int BTN_PRIMARY = 0, BTN_TONAL = 1, BTN_OUTLINE = 2, BTN_DANGER = 3;
    public static final TimeZone VN = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");

    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    public static int color(Context c, int res) { return c.getColor(res); }
    public static int alpha(int color, int a) { return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)); }

    public static GradientDrawable shape(Context c, int fillRes, float radiusDp, int strokeRes) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c.getColor(fillRes));
        g.setCornerRadius(dp(c, radiusDp));
        if (strokeRes != 0) g.setStroke(Math.max(1, dp(c, 1)), c.getColor(strokeRes));
        return g;
    }

    public static Drawable ripple(Context c, Drawable content, float radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(c, radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(alpha(c.getColor(R.color.ink), 0x1F)), content, mask);
    }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    public static LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    public static LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w); }
    public static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, Context c, float l, float t, float r, float b) {
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    public static TextView text(Context c, CharSequence s, float sp, boolean bold, int colorRes) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(c.getColor(colorRes));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    public static TextView chip(Context c, String label, int fgRes, int bgRes) {
        TextView t = text(c, label, 11, true, fgRes);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 9), dp(c, 3), dp(c, 9), dp(c, 3));
        t.setLetterSpacing(0.01f);
        t.setBackground(shape(c, bgRes, 20, 0));
        t.setSingleLine(true);
        return t;
    }

    public static ImageView icon(Context c, int drawableRes, int tintRes, float sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageResource(drawableRes);
        if (tintRes != 0) v.setImageTintList(ColorStateList.valueOf(c.getColor(tintRes)));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** Thẻ nền surface, bo góc 16dp, viền mảnh. Thêm vào cha bằng {@link #addCard}. */
    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        l.setBackground(shape(c, R.color.surface, 18, R.color.outline));
        return l;
    }

    public static void addCard(LinearLayout parent, View card) {
        parent.addView(card, margins(matchWrap(), parent.getContext(), 0, 0, 0, 10));
    }

    public static void clickable(View v, float radiusDp, int fillRes, int strokeRes, View.OnClickListener l) {
        Context c = v.getContext();
        v.setBackground(ripple(c, shape(c, fillRes, radiusDp, strokeRes), radiusDp));
        v.setClickable(true);
        v.setFocusable(true);
        v.setOnClickListener(l);
    }

    public static LinearLayout button(Context c, String label, int style, int iconRes, View.OnClickListener l) {
        int fg, bg, stroke = 0;
        switch (style) {
            case BTN_PRIMARY: fg = R.color.on_brand; bg = R.color.brand; break;
            case BTN_DANGER: fg = R.color.danger; bg = R.color.danger_soft; break;
            case BTN_OUTLINE: fg = R.color.primary; bg = R.color.surface; stroke = R.color.outline; break;
            default: fg = R.color.primary; bg = R.color.primary_soft; break;
        }
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(dp(c, 48));
        b.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        if (iconRes != 0) {
            ImageView i = icon(c, iconRes, fg, 18);
            ((LinearLayout.LayoutParams) i.getLayoutParams()).rightMargin = dp(c, 8);
            b.addView(i);
        }
        TextView t = text(c, label, 14, true, fg);
        t.setGravity(Gravity.CENTER);
        b.addView(t);
        clickable(b, 14, bg, stroke, l);
        return b;
    }

    public static TextView section(Context c, String label) {
        TextView t = text(c, label.toUpperCase(Locale.ROOT), 12, true, R.color.muted);
        t.setLetterSpacing(0.06f);
        t.setPadding(dp(c, 4), dp(c, 18), 0, dp(c, 8));
        return t;
    }

    public static TextView note(Context c, String s) {
        TextView t = text(c, s, 12.5f, false, R.color.muted);
        t.setPadding(dp(c, 2), dp(c, 2), dp(c, 2), dp(c, 6));
        return t;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(c.getColor(R.color.outline));
        v.setLayoutParams(margins(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1))), c, 0, 8, 0, 8));
        return v;
    }

    // ---------------- Biểu mẫu ----------------

    private static LinearLayout labeled(Context c, LinearLayout parent, String label, View input) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView l = text(c, label, 12, true, R.color.muted);
        l.setPadding(dp(c, 2), 0, 0, dp(c, 5));
        box.addView(l);
        box.addView(input, matchWrap());
        parent.addView(box, margins(matchWrap(), c, 0, 0, 0, 12));
        return box;
    }

    public static EditText edit(Context c, LinearLayout parent, String label, String value, boolean multiline) {
        EditText e = new EditText(c);
        e.setText(value == null ? "" : value);
        e.setTextSize(15);
        e.setTextColor(c.getColor(R.color.ink));
        e.setHintTextColor(c.getColor(R.color.muted));
        e.setBackground(shape(c, R.color.surface_alt, 12, R.color.outline));
        e.setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13));
        e.setMinHeight(dp(c, 48));
        if (multiline) { e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); e.setMinLines(2); e.setGravity(Gravity.TOP); }
        else { e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); e.setSingleLine(true); }
        labeled(c, parent, label, e);
        return e;
    }

    /** Ô chọn một giá trị trong danh sách (thay Spinner). */
    public static final class Picker {
        public final String[] items;
        public int index;
        public final TextView view;
        public Runnable onChange;
        public Picker(Context c, LinearLayout parent, String label, String[] items, int selected) {
            this.items = items; this.index = Math.max(0, Math.min(selected, items.length - 1));
            view = text(c, items[index], 15, false, R.color.ink);
            view.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
            view.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron, 0);
            view.setCompoundDrawableTintList(ColorStateList.valueOf(c.getColor(R.color.muted)));
            clickable(view, 12, R.color.surface_alt, R.color.outline, v -> new AlertDialog.Builder(c).setTitle(label)
                    .setSingleChoiceItems(items, index, (d, which) -> { index = which; view.setText(items[which]); d.dismiss(); if (onChange != null) onChange.run(); })
                    .setNegativeButton("Hủy", null).show());
            labeled(c, parent, label, view);
        }
        public String value() { return items[index]; }
    }

    /** Ô chọn ngày giờ bằng hộp chọn của hệ thống: người dùng không phải gõ tay định dạng dd/MM/yyyy HH:mm. */
    public static final class DateTimeField {
        public long millis;
        public final TextView view;
        public Runnable onChange;
        private final boolean withTime;
        private final SimpleDateFormat fmt;
        public DateTimeField(Context c, LinearLayout parent, String label, long initial, boolean withTime) {
            this.millis = initial; this.withTime = withTime;
            fmt = new SimpleDateFormat(withTime ? "dd/MM/yyyy HH:mm" : "dd/MM/yyyy", new Locale("vi", "VN"));
            fmt.setTimeZone(VN);
            view = text(c, fmt.format(initial), 15, false, R.color.ink);
            view.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
            view.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_calendar, 0);
            view.setCompoundDrawableTintList(ColorStateList.valueOf(c.getColor(R.color.muted)));
            clickable(view, 12, R.color.surface_alt, R.color.outline, v -> pick(c));
            labeled(c, parent, label, view);
        }
        private void pick(Context c) {
            Calendar cal = Calendar.getInstance(VN);
            cal.setTimeInMillis(millis);
            new DatePickerDialog(c, (dp, y, m, d) -> {
                cal.set(y, m, d);
                if (!withTime) { cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0); set(cal); return; }
                new TimePickerDialog(c, (tp, h, min) -> { cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0); set(cal); },
                        cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        }
        private void set(Calendar cal) { millis = cal.getTimeInMillis(); view.setText(fmt.format(millis)); if (onChange != null) onChange.run(); }
    }

    /** Ô chọn giờ HH:mm bằng hộp chọn giờ của hệ thống. */
    public static final class TimeField {
        public String value;
        public final TextView view;
        public TimeField(Context c, LinearLayout parent, String label, String hhmm) {
            this.value = hhmm;
            view = text(c, hhmm, 16, true, R.color.ink);
            view.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
            view.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_clock, 0);
            view.setCompoundDrawableTintList(ColorStateList.valueOf(c.getColor(R.color.muted)));
            clickable(view, 12, R.color.surface_alt, R.color.outline, v -> {
                int h = 7, m = 0;
                try { String[] p = value.split(":"); h = Integer.parseInt(p[0]); m = Integer.parseInt(p[1]); } catch (RuntimeException ignored) { }
                new TimePickerDialog(c, (tp, hh, mm) -> { value = String.format(Locale.US, "%02d:%02d", hh, mm); view.setText(value); }, h, m, true).show();
            });
            labeled(c, parent, label, view);
        }
    }
}
