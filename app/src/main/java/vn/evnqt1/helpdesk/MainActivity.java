package vn.evnqt1.helpdesk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import vn.evnqt1.helpdesk.core.AnalysisRange;
import vn.evnqt1.helpdesk.core.AnalysisResult;
import vn.evnqt1.helpdesk.core.AssistantIntent;
import vn.evnqt1.helpdesk.core.CsvReader;
import vn.evnqt1.helpdesk.core.ScheduleDraft;
import vn.evnqt1.helpdesk.core.ScheduleSheetParser;
import vn.evnqt1.helpdesk.core.XlsxReader;
import vn.evnqt1.helpdesk.core.ScheduleRules;
import vn.evnqt1.helpdesk.core.ShiftOccurrence;
import vn.evnqt1.helpdesk.data.AttachmentStore;
import vn.evnqt1.helpdesk.data.AnalysisEngine;
import vn.evnqt1.helpdesk.data.BackupManager;
import vn.evnqt1.helpdesk.data.LegacyCleanup;
import vn.evnqt1.helpdesk.data.HelpDeskRepository;
import vn.evnqt1.helpdesk.data.LocalDatabase;
import vn.evnqt1.helpdesk.data.ReportExporter;
import vn.evnqt1.helpdesk.ui.Ui;
import vn.evnqt1.helpdesk.ui.Ui.DateTimeField;
import vn.evnqt1.helpdesk.ui.Ui.Picker;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Giao diện native (Android SDK thuần). Toàn bộ dữ liệu nghiệp vụ nằm trong SQLite/tệp riêng của ứng dụng.
 * Cấu trúc: vỏ ứng dụng (header + điều hướng) → các trang (tab và trang con) → hộp thoại biểu mẫu.
 */
public class MainActivity extends Activity {
    private static final int REQ_EXPORT = 101, REQ_IMPORT = 102, REQ_ATTACHMENT = 103, REQ_BACKUP = 104, REQ_SCHEDULE_FILE = 105, REQ_SAMPLE = 106, REQ_FORM_ATTACH = 107;
    private static final String[] TABS = {"Tổng quan", "Bất thường", "Bản vẽ", "Thống kê", "Khác"};
    private static final int[] TAB_ICONS = {R.drawable.ic_home, R.drawable.ic_warning, R.drawable.ic_drawing, R.drawable.ic_stats, R.drawable.ic_grid};
    private static final int[] CREW_COLORS = {R.color.crew_a, R.color.crew_b, R.color.crew_c, R.color.crew_d, R.color.crew_e};
    private static final int PAGE_SIZE = 40;

    private interface Action { void run() throws Exception; }

    private static final class Route {
        final String name, arg;
        Route(String name, String arg) { this.name = name; this.arg = arg; }
    }

    // ---- dịch vụ ----
    private HelpDeskRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateTime = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"));
    private final SimpleDateFormat timeOnly = new SimpleDateFormat("HH:mm", new Locale("vi", "VN"));

    // ---- vỏ giao diện ----
    private LinearLayout root, header, content, bottomNav;
    private ScrollView scroll;
    private TextView titleView, subtitleView;
    private ImageView backBtn, logoView, searchBtn, bellBtn, fab;
    private android.widget.FrameLayout bellHolder;
    private boolean fabWanted, imeVisible;
    private TextView[] navLabels = new TextView[TABS.length];
    private ImageView[] navIcons = new ImageView[TABS.length];
    private LinearLayout[] navPills = new LinearLayout[TABS.length];
    private TextView alertBadge;
    private int headerBasePad, navBasePad;

    // ---- trạng thái điều hướng ----
    private String tab = TABS[0];
    private final ArrayDeque<Route> stack = new ArrayDeque<>();
    private OnBackInvokedCallback backCallback;
    private boolean backRegistered;

    // ---- trạng thái trang ----
    private String pendingReportScope = "ALL", pendingExportType = "", pendingOwnerType, pendingOwnerId, pendingBackupPassword = "";
    private long pendingReportFrom = 0, pendingReportTo = Long.MAX_VALUE;
    private String anomalyFilter = "Tất cả", anomalyQuery = "", drawingQuery = "";
    private int shiftYear, shiftMonth;
    private ScheduleDraft draft;
    private AnalysisResult analysis;
    private int scheduleJob = 0;
    private AlertDialog busyDialog;
    private AttachBox activeAttach;

    // ======================================================================
    // Vòng đời
    // ======================================================================
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dateTime.setTimeZone(Ui.VN); dateTime.setLenient(false); timeOnly.setTimeZone(Ui.VN);
        repo = new HelpDeskRepository(this);
        Calendar now = Calendar.getInstance(Ui.VN);
        shiftYear = now.get(Calendar.YEAR); shiftMonth = now.get(Calendar.MONTH) + 1;
        if (state != null && state.getString("tab") != null) tab = state.getString("tab");
        // Việc dọn dẹp/làm mới cảnh báo chạy nền để màn hình mở ngay.
        io.execute(() -> { LegacyCleanup.run(this); long t = System.currentTimeMillis(); repo.cleanupTrash(t); repo.refreshAlerts(t); main.post(() -> { if (!isDestroyed()) { updateBadge(); if ((stack.isEmpty() && (tab.equals(TABS[0]) || tab.equals(TABS[3]))) || (!stack.isEmpty() && "alerts".equals(current().name))) render(false); } }); });
        buildShell();
        render(true);
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("tab", tab); }

    @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); buildShell(); render(false); }

    @Override protected void onDestroy() {
        super.onDestroy();
        scheduleJob++;
        if (backRegistered && Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        io.shutdownNow();
    }

    @Override @SuppressWarnings("deprecation") public void onBackPressed() { if (!handleBack()) super.onBackPressed(); }

    private boolean handleBack() {
        if (!stack.isEmpty()) { pop(); return true; }
        if (!tab.equals(TABS[0])) { openTab(TABS[0]); return true; }
        return false;
    }

    /** Android 13+: chỉ đăng ký callback khi còn trang để quay lại, để hệ thống giữ hiệu ứng thoát ứng dụng. */
    private void updateBackCallback() {
        if (Build.VERSION.SDK_INT < 33) return;
        boolean need = !stack.isEmpty() || !tab.equals(TABS[0]);
        if (need && !backRegistered) {
            if (backCallback == null) backCallback = () -> handleBack();
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
            backRegistered = true;
        } else if (!need && backRegistered) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            backRegistered = false;
        }
    }

    // ======================================================================
    // Vỏ ứng dụng: header + nội dung cuộn + thanh điều hướng dưới
    // ======================================================================
    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.color(this, R.color.bg));

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        headerBasePad = Ui.dp(this, 12);
        header.setPadding(Ui.dp(this, 16), headerBasePad, Ui.dp(this, 8), headerBasePad);
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Ui.color(this, R.color.brand_dark), Ui.color(this, R.color.brand)});
        header.setBackground(hg);

        backBtn = Ui.icon(this, R.drawable.ic_back, R.color.on_brand, 24);
        backBtn.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        backBtn.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        backBtn.setBackground(Ui.ripple(this, new GradientDrawable(), 20));
        backBtn.setOnClickListener(v -> handleBack());
        header.addView(backBtn);

        logoView = new ImageView(this);
        logoView.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams lpLogo = new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38));
        lpLogo.rightMargin = Ui.dp(this, 12);
        logoView.setLayoutParams(lpLogo);
        header.addView(logoView);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titleView = Ui.text(this, "", 18, true, R.color.on_brand);
        titleView.setSingleLine(true);
        subtitleView = Ui.text(this, "", 12, false, R.color.on_brand);
        subtitleView.setAlpha(0.75f);
        subtitleView.setSingleLine(true);
        titles.addView(titleView);
        titles.addView(subtitleView);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        bellHolder = new android.widget.FrameLayout(this);
        bellBtn = Ui.icon(this, R.drawable.ic_bell, R.color.on_brand, 24);
        bellBtn.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        bellBtn.setBackground(Ui.ripple(this, new GradientDrawable(), 22));
        bellBtn.setContentDescription("Cảnh báo");
        bellBtn.setOnClickListener(v -> push("alerts", null));
        bellHolder.addView(bellBtn, new android.widget.FrameLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        alertBadge = Ui.text(this, "0", 9, true, R.color.on_brand);
        alertBadge.setGravity(Gravity.CENTER);
        alertBadge.setBackground(Ui.shape(this, R.color.danger, 10, 0));
        alertBadge.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        alertBadge.setMinWidth(Ui.dp(this, 16));
        android.widget.FrameLayout.LayoutParams bp0 = new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 16), Gravity.TOP | Gravity.END);
        bp0.topMargin = Ui.dp(this, 4);
        bp0.rightMargin = Ui.dp(this, 2);
        bellHolder.addView(alertBadge, bp0);
        alertBadge.setVisibility(View.GONE);
        header.addView(bellHolder, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));

        searchBtn = Ui.icon(this, R.drawable.ic_search, R.color.on_brand, 24);
        searchBtn.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        searchBtn.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        searchBtn.setBackground(Ui.ripple(this, new GradientDrawable(), 22));
        searchBtn.setOnClickListener(v -> searchDialog());
        header.addView(searchBtn);
        root.addView(header);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 28));
        int screenW = getResources().getDisplayMetrics().widthPixels, maxW = Ui.dp(this, 720);
        // Máy tính bảng/xoay ngang: giữ cột nội dung vừa tầm đọc, căn giữa.
        scroll.addView(content, new android.widget.FrameLayout.LayoutParams(screenW > maxW ? maxW : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        android.widget.FrameLayout body = new android.widget.FrameLayout(this);
        body.addView(scroll, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fab = new ImageView(this);
        fab.setImageResource(R.drawable.ic_add);
        fab.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.on_brand)));
        fab.setScaleType(ImageView.ScaleType.CENTER);
        GradientDrawable fg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Ui.color(this, R.color.brand), Ui.color(this, R.color.brand_deep)});
        fg.setShape(GradientDrawable.OVAL);
        fab.setBackground(Ui.ripple(this, fg, 28));
        fab.setElevation(Ui.dp(this, 6));
        fab.setContentDescription("Thêm nhanh: ghi công việc, tạo bất thường, tạo bản vẽ");
        fab.setOnClickListener(v -> fabMenu());
        android.widget.FrameLayout.LayoutParams fp = new android.widget.FrameLayout.LayoutParams(Ui.dp(this, 58), Ui.dp(this, 58), Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, Ui.dp(this, 16), Ui.dp(this, 16));
        body.addView(fab, fp);
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        navBasePad = Ui.dp(this, 6);
        bottomNav.setPadding(Ui.dp(this, 6), navBasePad, Ui.dp(this, 6), navBasePad);
        bottomNav.setBackgroundColor(Ui.color(this, R.color.surface));
        bottomNav.setElevation(Ui.dp(this, 8));
        for (int i = 0; i < TABS.length; i++) {
            final String name = TABS[i];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setOnClickListener(v -> openTab(name));
            LinearLayout pill = new LinearLayout(this);
            pill.setGravity(Gravity.CENTER);
            navIcons[i] = Ui.icon(this, TAB_ICONS[i], R.color.muted, 22);
            pill.addView(navIcons[i]);
            android.widget.FrameLayout holder = new android.widget.FrameLayout(this);
            pill.setLayoutParams(new android.widget.FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 30), Gravity.CENTER));
            holder.addView(pill);
            item.addView(holder, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 30)));
            navPills[i] = pill;
            navLabels[i] = Ui.text(this, name, 11, true, R.color.muted);
            navLabels[i].setSingleLine(true);
            navLabels[i].setPadding(0, Ui.dp(this, 2), 0, 0);
            item.addView(navLabels[i]);
            bottomNav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        root.addView(bottomNav);

        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int top, bottom, left, right; boolean imeShown = false;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = ins.getInsets(WindowInsets.Type.ime());
                top = bars.top; left = bars.left; right = bars.right; bottom = Math.max(bars.bottom, ime.bottom);
                imeShown = ins.isVisible(WindowInsets.Type.ime());
            } else {
                top = ins.getSystemWindowInsetTop(); left = ins.getSystemWindowInsetLeft(); right = ins.getSystemWindowInsetRight(); bottom = ins.getSystemWindowInsetBottom();
            }
            header.setPadding(Ui.dp(this, 16) + left, headerBasePad + top, Ui.dp(this, 8) + right, headerBasePad);
            bottomNav.setPadding(Ui.dp(this, 6) + left, navBasePad, Ui.dp(this, 6) + right, navBasePad + (imeShown ? 0 : bottom));
            bottomNav.setVisibility(imeShown ? View.GONE : View.VISIBLE);
            imeVisible = imeShown;
            applyFab();
            ((android.widget.FrameLayout.LayoutParams) fab.getLayoutParams()).rightMargin = Ui.dp(this, 16) + right;
            fab.requestLayout();
            root.setPadding(0, 0, 0, imeShown ? bottom : 0);
            return Build.VERSION.SDK_INT >= 30 ? WindowInsets.CONSUMED : ins.consumeSystemWindowInsets();
        });
        setContentView(root);
        updateBadge();
    }

    private void updateBadge() {
        if (alertBadge == null) return;
        long unread = repo.count("alerts", "condition_status='active' AND is_read=0");
        alertBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
        alertBadge.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
    }

    // ======================================================================
    // Điều hướng & dựng trang
    // ======================================================================
    private Route current() { return stack.isEmpty() ? new Route(tab, null) : stack.peek(); }
    private void openTab(String t) { tab = t; stack.clear(); render(true); }
    private void push(String name, String arg) { stack.push(new Route(name, arg)); render(true); }
    private void pop() { if (!stack.isEmpty()) { stack.pop(); render(true); } }
    private void rerender() { render(false); }

    private String titleFor(Route r) {
        switch (r.name) {
            case "alerts": return "Cảnh báo";
            case "shifts": return "Lịch ca trực";
            case "schedule_preview": return "Xem lại lịch ca";
            case "anomaly": return "Hồ sơ bất thường";
            case "drawing": return "Hồ sơ bản vẽ";
            case "trash": return "Thùng rác";
            case "equipment": return "Danh mục thiết bị";
            case "assistant": return "Trợ lý phân tích";
            case "analysis": return analysis == null ? "Kết quả phân tích" : analysis.title;
            case "about": return "Giới thiệu";
            case "search": return "Kết quả tìm kiếm";
            default: return r.name.equals(TABS[0]) ? getString(R.string.app_name) : r.name;
        }
    }

    private void render(boolean toTop) {
        int keepY = scroll.getScrollY();
        content.removeAllViews();
        Route r = current();
        boolean sub = !stack.isEmpty();
        backBtn.setVisibility(sub ? View.VISIBLE : View.GONE);
        logoView.setVisibility(sub ? View.GONE : View.VISIBLE);
        searchBtn.setVisibility(sub ? View.GONE : View.VISIBLE);
        bellHolder.setVisibility(sub ? View.GONE : View.VISIBLE);
        fabWanted = !sub && (tab.equals(TABS[0]) || tab.equals(TABS[1]) || tab.equals(TABS[2]));
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, fabWanted ? 92 : 28));
        applyFab();
        titleView.setText(titleFor(r));
        subtitleView.setVisibility(sub ? View.GONE : View.VISIBLE);
        if (!sub) subtitleView.setText(r.name.equals(TABS[0]) ? "Bất thường thiết bị & hồ sơ bản vẽ" : r.name.equals(TABS[3]) ? "Tồn đọng · phát sinh · đã xử lý" : "EVNQT1 HELP DESK");
        for (int i = 0; i < TABS.length; i++) {
            boolean on = TABS[i].equals(tab);
            navLabels[i].setTextColor(Ui.color(this, on ? R.color.primary : R.color.muted));
            navIcons[i].setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, on ? R.color.primary : R.color.muted)));
            navPills[i].setBackground(on ? Ui.shape(this, R.color.primary_soft, 16, 0) : null);
        }
        try {
            switch (r.name) {
                case "Tổng quan": showDashboard(); break;
                case "Bất thường": showAnomalies(); break;
                case "Bản vẽ": showDrawings(); break;
                case "Thống kê": showStats(); break;
                case "alerts": showAlerts(); break;
                case "Khác": showMore(); break;
                case "shifts": showShifts(); break;
                case "schedule_preview": showSchedulePreview(); break;
                case "anomaly": showAnomalyDetail(r.arg); break;
                case "drawing": showDrawingDetail(r.arg); break;
                case "trash": showTrash(); break;
                case "equipment": showEquipment(); break;
                case "assistant": showAssistant(); break;
                case "analysis": showAnalysis(); break;
                case "about": showAbout(); break;
                case "search": showSearch(r.arg); break;
                default: showDashboard();
            }
        } catch (RuntimeException e) {
            content.removeAllViews();
            Ui.addCard(content, errorCard("Không hiển thị được trang: " + msg(e)));
        }
        updateBadge();
        updateBackCallback();
        final int y = toTop ? 0 : keepY;
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private View errorCard(String m) {
        LinearLayout c = Ui.card(this);
        c.setBackground(Ui.shape(this, R.color.danger_soft, 16, 0));
        c.addView(Ui.text(this, m, 14, false, R.color.danger));
        return c;
    }

    // ======================================================================
    // Thành phần dùng lại
    // ======================================================================
    private String msg(Exception e) { return e.getMessage() == null ? e.toString() : e.getMessage(); }
    private String n(String s) { return s == null ? "" : s; }
    private String dash(String s) { return s == null || s.trim().isEmpty() ? "—" : s; }
    private String limit(String s, int max) { if (s == null) return ""; return s.length() > max ? s.substring(0, max) + "…" : s; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private String fmt(long t) { return dateTime.format(new Date(t)); }

    /** {màu chữ, màu nền} theo trạng thái xử lý. */
    private int[] statusColors(String status) {
        if ("Đã xử lý".equals(status)) return new int[]{R.color.success, R.color.success_soft};
        if ("Đang xử lý".equals(status)) return new int[]{R.color.info, R.color.info_soft};
        return new int[]{R.color.warning, R.color.warning_soft};
    }
    private int[] severityColors(String sev) {
        if ("Nghiêm trọng".equals(sev)) return new int[]{R.color.danger, R.color.danger_soft};
        if ("Cao".equals(sev)) return new int[]{R.color.warning, R.color.warning_soft};
        if ("Trung bình".equals(sev)) return new int[]{R.color.info, R.color.info_soft};
        return new int[]{R.color.muted, R.color.surface_alt};
    }
    private TextView statusChip(String status) { int[] c = statusColors(status); return Ui.chip(this, status, c[0], c[1]); }
    private TextView severityChip(String sev) { int[] c = severityColors(sev); return Ui.chip(this, sev, c[0], c[1]); }

    private LinearLayout row(View... views) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        for (View v : views) { LinearLayout.LayoutParams p = Ui.matchWrap(); p.width = ViewGroup.LayoutParams.WRAP_CONTENT; p.rightMargin = Ui.dp(this, 6); l.addView(v, p); }
        return l;
    }

    private void buttonRow(View... buttons) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < buttons.length; i++) l.addView(buttons[i], Ui.margins(Ui.weight(1), this, 0, 0, i == buttons.length - 1 ? 0 : 8, 0));
        content.addView(l, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 10));
    }

    private void section(String label) { content.addView(Ui.section(this, label)); }
    private void empty(String text) {
        LinearLayout c = Ui.card(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView i = Ui.icon(this, R.drawable.ic_info, R.color.muted, 28);
        c.addView(i);
        TextView t = Ui.text(this, text, 13.5f, false, R.color.muted);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 8), 0, 0);
        c.addView(t);
        Ui.addCard(content, c);
    }

    private LinearLayout infoRows(String[][] rows) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        for (String[] r : rows) {
            LinearLayout line = new LinearLayout(this);
            line.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
            TextView k = Ui.text(this, r[0], 13, false, R.color.muted);
            TextView v = Ui.text(this, dash(r[1]), 14, false, R.color.ink);
            line.addView(k, new LinearLayout.LayoutParams(Ui.dp(this, 112), ViewGroup.LayoutParams.WRAP_CONTENT));
            line.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            l.addView(line);
        }
        return l;
    }

    private View searchBar(String hint, String initial, java.util.function.Consumer<String> onText) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 10), 0);
        bar.setBackground(Ui.shape(this, R.color.surface, 14, R.color.outline));
        bar.addView(Ui.icon(this, R.drawable.ic_search, R.color.muted, 20));
        EditText e = new EditText(this);
        e.setHint(hint); e.setText(initial); e.setSingleLine(true); e.setTextSize(14.5f);
        e.setTextColor(Ui.color(this, R.color.ink)); e.setHintTextColor(Ui.color(this, R.color.muted));
        e.setBackground(null); e.setPadding(Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12));
        e.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence a, int b, int c, int d) {}
            public void onTextChanged(CharSequence a, int b, int c, int d) {}
            public void afterTextChanged(Editable ed) { onText.accept(ed.toString()); }
        });
        bar.addView(e, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return bar;
    }

    /** Thẻ hồ sơ trong danh sách: dải màu bên trái theo mức độ, chip trạng thái, tuổi của bất thường còn tồn tại. */
    private void recordCard(LinearLayout parent, HelpDeskRepository.Record r, boolean drawing) {
        LinearLayout c = new LinearLayout(this);
        c.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        View stripe = new View(this);
        stripe.setBackground(Ui.shape(this, drawing ? R.color.primary : severityColors(r.severity)[0], 3, 0));
        c.addView(stripe, Ui.margins(new LinearLayout.LayoutParams(Ui.dp(this, 4), ViewGroup.LayoutParams.MATCH_PARENT), this, 0, 0, 12, 0));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        String mainText = drawing ? r.drawingNumber : r.title;
        LinearLayout chips = row(Ui.chip(this, (drawing ? "BẢN VẼ · " : "") + r.code, R.color.primary, R.color.primary_soft));
        if (!drawing) { chips.addView(statusChip(r.status)); chips.addView(severityChip(r.severity)); }
        col.addView(chips);
        TextView t = Ui.text(this, mainText == null ? "(Chưa có tiêu đề)" : mainText, 16, true, R.color.ink);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(0, Ui.dp(this, 8), 0, 0);
        col.addView(t);
        String sub = drawing ? n(r.title) + (r.title == null || r.title.isEmpty() ? "" : " · ") + "Rev " + (r.currentRevision == null ? "—" : r.currentRevision)
                : n(r.equipmentName) + " · KKS: " + (r.kks == null ? "—" : r.kks);
        col.addView(Ui.text(this, sub, 13, false, R.color.muted));
        String foot = drawing ? r.sessionCount + " phiên công việc · " + r.revisionCount + " revision · cập nhật " + ago(r.updatedAt)
                : (!"Đã xử lý".equals(r.status) && r.detectedAt > 0 ? "Tồn tại " + durationText(System.currentTimeMillis() - r.detectedAt) + " · " : "") + r.sessionCount + " phiên xử lý · cập nhật " + ago(r.updatedAt);
        TextView f = Ui.text(this, foot, 11.5f, false, R.color.muted);
        f.setPadding(0, Ui.dp(this, 6), 0, 0);
        col.addView(f);
        c.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Ui.clickable(c, 18, R.color.surface, R.color.outline, v -> push(drawing ? "drawing" : "anomaly", r.id));
        Ui.addCard(parent, c);
    }

    /** Hiển thị theo trang để danh sách dài không làm chậm máy. */
    private void addPaged(LinearLayout parent, List<HelpDeskRepository.Record> list, boolean drawing, int from) {
        int to = Math.min(list.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) recordCard(parent, list.get(i), drawing);
        if (to < list.size()) {
            final int next = to;
            LinearLayout more = Ui.button(this, "Xem thêm (" + (list.size() - to) + ")", Ui.BTN_OUTLINE, 0, null);
            more.setOnClickListener(v -> { parent.removeView(more); addPaged(parent, list, drawing, next); });
            parent.addView(more, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 10));
        }
    }

    private String currentSlotLabel(ShiftOccurrence s) {
        if ("MORNING".equals(s.slot)) return "Ca sáng";
        if ("AFTERNOON".equals(s.slot)) return "Ca chiều";
        if ("NIGHT".equals(s.slot)) return "Ca đêm";
        return "Ca " + s.code;
    }

    private int crewColor(String code) {
        for (int i = 0; i < ScheduleDraft.CREWS.length; i++) if (ScheduleDraft.CREWS[i].equals(code)) return CREW_COLORS[i];
        return R.color.muted;
    }

    // ======================================================================
    // Tổng quan
    // ======================================================================
    private void showDashboard() {
        long now = System.currentTimeMillis();
        ShiftOccurrence cur = repo.currentShift(now);
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Ui.color(this, R.color.brand), Ui.color(this, R.color.brand_deep)});
        hg.setCornerRadius(Ui.dp(this, 20));
        hero.setBackground(hg);
        TextView lab = Ui.text(this, cur == null ? "LỊCH CA TRỰC" : "ĐANG TRỰC", 11, true, R.color.on_brand);
        lab.setAlpha(0.7f); lab.setLetterSpacing(0.08f);
        hero.addView(lab);
        if (cur != null) {
            TextView big = Ui.text(this, "Kíp " + cur.code + " · " + currentSlotLabel(cur), 24, true, R.color.on_brand);
            big.setPadding(0, Ui.dp(this, 4), 0, 0);
            hero.addView(big);
            long left = Math.max(0, cur.endMillis - now) / 60_000L;
            TextView time = Ui.text(this, timeOnly.format(new Date(cur.startMillis)) + " – " + timeOnly.format(new Date(cur.endMillis)) + " · còn " + (left / 60) + " giờ " + (left % 60) + " phút", 13, false, R.color.on_brand);
            time.setAlpha(0.85f);
            hero.addView(time);
            if (!cur.roster.isEmpty()) {
                TextView ros = Ui.text(this, cur.roster, 13, false, R.color.on_brand);
                ros.setPadding(0, Ui.dp(this, 8), 0, 0);
                hero.addView(ros);
            }
            hero.setOnClickListener(v -> push("shifts", null));
        } else {
            TextView big = Ui.text(this, "Chưa có lịch ca cho thời điểm này", 18, true, R.color.on_brand);
            big.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
            hero.addView(big);
            LinearLayout b = Ui.button(this, "Nhập lịch ca từ Excel", Ui.BTN_TONAL, R.drawable.ic_calendar, v -> startScheduleImport());
            hero.addView(b);
        }
        Ui.addCard(content, hero);

        section("Bất thường thiết bị");
        long processing = repo.count("anomalies", "deleted_at IS NULL AND status='Đang xử lý'");
        long open = repo.count("anomalies", "deleted_at IS NULL AND status='Chưa xử lý'");
        long closed = repo.count("anomalies", "deleted_at IS NULL AND status='Đã xử lý'");
        long activeAlerts = repo.count("alerts", "condition_status='active'");
        tileRow(
                kpiTile("Chưa xử lý", String.valueOf(open), "Cần tiếp nhận", R.color.muted, R.drawable.ic_warning, R.color.warning, R.color.warning_soft, () -> { anomalyFilter = "Chưa xử lý"; openTab("Bất thường"); }),
                kpiTile("Đang xử lý", String.valueOf(processing), "Đang thực hiện", R.color.muted, R.drawable.ic_tools, R.color.info, R.color.info_soft, () -> { anomalyFilter = "Đang xử lý"; openTab("Bất thường"); }));
        tileRow(
                kpiTile("Đã xử lý", String.valueOf(closed), "Đã đóng hồ sơ", R.color.muted, R.drawable.ic_check, R.color.success, R.color.success_soft, () -> { anomalyFilter = "Đã xử lý"; openTab("Bất thường"); }),
                kpiTile("Cảnh báo", String.valueOf(activeAlerts), "Đang có hiệu lực", R.color.muted, R.drawable.ic_bell, R.color.danger, R.color.danger_soft, () -> push("alerts", null)));
        long[] r7 = AnalysisRange.resolve(AnalysisRange.LAST7, now, ScheduleRules.ZONE);
        vn.evnqt1.helpdesk.core.StatsReport w = repo.stats(r7[0], r7[1], now);
        LinearLayout wk = Ui.card(this);
        wk.setOrientation(LinearLayout.HORIZONTAL);
        wk.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout wkText = new LinearLayout(this);
        wkText.setOrientation(LinearLayout.VERTICAL);
        wkText.addView(Ui.text(this, "7 NGÀY QUA", 11, true, R.color.muted));
        TextView wkLine = Ui.text(this, w.newList.size() + " phát sinh mới · " + w.resolvedList.size() + " đã xử lý", 15.5f, true, R.color.ink);
        wkLine.setPadding(0, Ui.dp(this, 3), 0, 0);
        wkText.addView(wkLine);
        wkText.addView(Ui.text(this, "Đang tồn tại: " + w.openTotal + " · xem thống kê chi tiết theo ngày", 12.5f, false, R.color.muted));
        wk.addView(wkText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        wk.addView(Ui.icon(this, R.drawable.ic_stats, R.color.primary, 24));
        Ui.clickable(wk, 18, R.color.surface, R.color.outline, v -> { statsPreset = 0; openTab("Thống kê"); });
        Ui.addCard(content, wk);

        section("Hồ sơ bản vẽ");
        long drawings = repo.count("drawings", "deleted_at IS NULL"), revisions = repo.count("drawing_revisions", null), sessions = repo.count("drawing_work_sessions", null);
        statRow(new String[]{"Bản vẽ", "Revision", "Phiên"}, new long[]{drawings, revisions, sessions}, new int[]{R.color.primary, R.color.crew_d, R.color.crew_b}, new Runnable[]{() -> openTab("Bản vẽ"), () -> openTab("Bản vẽ"), () -> openTab("Bản vẽ")});

        section("Truy cập nhanh");
        quickRow(new String[]{"Bất thường mới", "Bản vẽ mới", "Ghi công việc"}, new int[]{R.drawable.ic_add, R.drawable.ic_drawing, R.drawable.ic_edit}, new Runnable[]{this::newAnomaly, this::newDrawing, this::chooseWorkTarget});
        quickRow(new String[]{"Lịch ca", "Nhập lịch Excel", "Trợ lý phân tích"}, new int[]{R.drawable.ic_calendar, R.drawable.ic_backup, R.drawable.ic_sparkle}, new Runnable[]{() -> push("shifts", null), this::startScheduleImport, () -> push("assistant", null)});

        List<HelpDeskRepository.Record> aa = repo.listAnomalies(false, 4);
        section("Bất thường cập nhật gần đây");
        if (aa.isEmpty()) empty("Chưa có hồ sơ bất thường."); else for (HelpDeskRepository.Record r : aa) recordCard(content, r, false);
        List<HelpDeskRepository.Record> dd = repo.listDrawings(false, 4);
        section("Bản vẽ cập nhật gần đây");
        if (dd.isEmpty()) empty("Chưa có hồ sơ bản vẽ."); else for (HelpDeskRepository.Record r : dd) recordCard(content, r, true);
    }

    private void statRow(String[] labels, long[] values, int[] accents, Runnable[] actions) {
        LinearLayout l = new LinearLayout(this);
        for (int i = 0; i < labels.length; i++) {
            LinearLayout c = Ui.card(this);
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            View dot = new View(this);
            dot.setBackground(Ui.shape(this, accents[i], 4, 0));
            top.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8)));
            TextView lb = Ui.text(this, labels[i], 12, false, R.color.muted);
            lb.setPadding(Ui.dp(this, 6), 0, 0, 0);
            lb.setSingleLine(true);
            top.addView(lb);
            c.addView(top);
            TextView v = Ui.text(this, String.valueOf(values[i]), 28, true, R.color.ink);
            c.addView(v);
            final Runnable a = actions[i];
            Ui.clickable(c, 16, R.color.surface, R.color.outline, x -> a.run());
            l.addView(c, Ui.margins(Ui.weight(1), this, 0, 0, i == labels.length - 1 ? 0 : 8, 0));
        }
        content.addView(l, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 8));
    }

    /** Hàng lối tắt: các thẻ cùng chiều cao (cao bằng thẻ cao nhất), nhãn luôn chiếm 2 dòng để biểu tượng và chữ thẳng hàng. */
    private void quickRow(String[] labels, int[] icons, Runnable[] actions) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            LinearLayout c = Ui.card(this);
            c.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            c.setPadding(Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 12));
            LinearLayout disc = new LinearLayout(this);
            disc.setGravity(Gravity.CENTER);
            disc.setBackground(Ui.shape(this, R.color.primary_soft, 14, 0));
            disc.addView(Ui.icon(this, icons[i], R.color.primary, 22));
            c.addView(disc, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
            TextView t = Ui.text(this, labels[i], 12.5f, true, R.color.ink);
            t.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            t.setMinLines(2);
            t.setMaxLines(2);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setPadding(0, Ui.dp(this, 8), 0, 0);
            c.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            final Runnable a = actions[i];
            Ui.clickable(c, 16, R.color.surface, R.color.outline, x -> a.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            lp.setMargins(0, 0, i == labels.length - 1 ? 0 : Ui.dp(this, 8), 0);
            l.addView(c, lp);
        }
        content.addView(l, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 8));
    }

    // ======================================================================
    // Danh sách bất thường / bản vẽ / cảnh báo / menu
    // ======================================================================
    private void showAnomalies() {
        buttonRow(Ui.button(this, "Tạo bất thường", Ui.BTN_PRIMARY, R.drawable.ic_add, v -> newAnomaly()), Ui.button(this, "Ghi công việc", Ui.BTN_TONAL, R.drawable.ic_edit, v -> chooseAnomalyWork()));
        final List<HelpDeskRepository.Record> all = repo.listAnomalies(false);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        final Runnable refill = () -> {
            list.removeAllViews();
            List<HelpDeskRepository.Record> shown = new ArrayList<>();
            for (HelpDeskRepository.Record r : all) {
                if (!"Tất cả".equals(anomalyFilter) && !("Đang tồn tại".equals(anomalyFilter) ? !"Đã xử lý".equals(r.status) : anomalyFilter.equals(r.status))) continue;
                if (!anomalyQuery.trim().isEmpty() && !vn.evnqt1.helpdesk.core.TextNormalizer.contains(r.code + " " + r.title + " " + r.description + " " + r.equipmentName + " " + r.kks + " " + r.location + " " + r.systemArea, anomalyQuery)) continue;
                shown.add(r);
            }
            if (all.isEmpty()) emptyIn(list, "Chưa có hồ sơ bất thường. Bấm “Tạo bất thường” để bắt đầu.");
            else if (shown.isEmpty()) emptyIn(list, "Không có hồ sơ phù hợp bộ lọc.");
            else addPaged(list, shown, false, 0);
        };
        content.addView(searchBar("Tìm theo thiết bị, KKS, mô tả, hệ thống…", anomalyQuery, q -> { anomalyQuery = q; refill.run(); }), Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 10));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        for (String f : new String[]{"Tất cả", "Đang tồn tại", "Chưa xử lý", "Đang xử lý", "Đã xử lý"}) {
            boolean on = f.equals(anomalyFilter);
            TextView c = Ui.text(this, f, 13, true, on ? R.color.on_brand : R.color.ink);
            c.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
            Ui.clickable(c, 20, on ? R.color.brand : R.color.surface, on ? 0 : R.color.outline, v -> { anomalyFilter = f; rerender(); });
            chips.addView(c, Ui.margins(Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), this, 0, 0, 8, 0));
        }
        hs.addView(chips);
        content.addView(hs, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12));
        content.addView(list);
        refill.run();
    }

    private void emptyIn(LinearLayout parent, String text) {
        LinearLayout hold = content;
        content = parent;
        empty(text);
        content = hold;
    }

    private void showDrawings() {
        buttonRow(Ui.button(this, "Tạo hồ sơ bản vẽ", Ui.BTN_PRIMARY, R.drawable.ic_add, v -> newDrawing()), Ui.button(this, "Ghi công việc", Ui.BTN_TONAL, R.drawable.ic_edit, v -> chooseDrawingWork()));
        final List<HelpDeskRepository.Record> all = repo.listDrawings(false);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        final Runnable refill = () -> {
            list.removeAllViews();
            String term = drawingQuery.trim();
            List<HelpDeskRepository.Record> shown = new ArrayList<>();
            for (HelpDeskRepository.Record r : all)
                if (term.isEmpty() || (r.drawingNumber != null && r.drawingNumber.equalsIgnoreCase(term)) || vn.evnqt1.helpdesk.core.TextNormalizer.contains(r.code + " " + r.drawingNumber + " " + r.title + " " + r.description + " " + r.discipline + " " + r.systemArea, term)) shown.add(r);
            if (all.isEmpty()) emptyIn(list, "Chưa có hồ sơ bản vẽ. Bấm “Tạo hồ sơ bản vẽ” để bắt đầu.");
            else if (shown.isEmpty()) emptyIn(list, "Không tìm thấy bản vẽ phù hợp.");
            else addPaged(list, shown, true, 0);
        };
        content.addView(searchBar("Tìm số bản vẽ hoặc tên/mô tả…", drawingQuery, q -> { drawingQuery = q; refill.run(); }), Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12));
        content.addView(list);
        refill.run();
    }

    private void showAlerts() {
        repo.refreshAlerts(System.currentTimeMillis());
        List<HelpDeskRepository.AlertItem> alerts = repo.alerts();
        boolean anyUnread = false;
        for (HelpDeskRepository.AlertItem a : alerts) if (a.isRead == 0 && "active".equals(a.condition)) anyUnread = true;
        content.addView(Ui.note(this, "Cảnh báo chỉ hiển thị trong ứng dụng, không có thông báo đẩy, rung hoặc chuông. Xem cảnh báo không đồng nghĩa đã xử lý bất thường."));
        if (anyUnread) {
            final List<HelpDeskRepository.AlertItem> snapshot = alerts;
            content.addView(Ui.button(this, "Đánh dấu tất cả đã xem", Ui.BTN_TONAL, R.drawable.ic_check, v -> { for (HelpDeskRepository.AlertItem a : snapshot) if (a.isRead == 0) repo.markAlertRead(a.id); rerender(); }), Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 12));
        }
        if (alerts.isEmpty()) { empty("Hiện không có cảnh báo theo các ngưỡng đã cấu hình."); return; }
        for (HelpDeskRepository.AlertItem a : alerts) {
            boolean active = "active".equals(a.condition);
            LinearLayout card = Ui.card(this);
            card.addView(row(Ui.chip(this, a.type, active ? R.color.danger : R.color.muted, active ? R.color.danger_soft : R.color.surface_alt), Ui.chip(this, a.isRead == 1 ? "Đã xem" : "Chưa xem", a.isRead == 1 ? R.color.muted : R.color.primary, a.isRead == 1 ? R.color.surface_alt : R.color.primary_soft), Ui.chip(this, active ? "Còn hiệu lực" : "Hết hiệu lực", R.color.muted, R.color.surface_alt)));
            TextView t = Ui.text(this, a.anomalyCode + " · " + a.title, 15, true, R.color.ink);
            t.setPadding(0, Ui.dp(this, 8), 0, 0);
            card.addView(t);
            card.addView(Ui.text(this, a.reason, 13, false, R.color.muted));
            Ui.clickable(card, 16, R.color.surface, R.color.outline, v -> { repo.markAlertRead(a.id); push("anomaly", a.anomalyId); });
            Ui.addCard(content, card);
        }
    }

    private void menuItem(String title, String detail, int icon, Runnable click) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        LinearLayout disc = new LinearLayout(this);
        disc.setGravity(Gravity.CENTER);
        disc.setBackground(Ui.shape(this, R.color.primary_soft, 12, 0));
        disc.addView(Ui.icon(this, icon, R.color.primary, 20));
        c.addView(disc, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        tx.addView(Ui.text(this, title, 15, true, R.color.ink));
        tx.addView(Ui.text(this, detail, 12, false, R.color.muted));
        c.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        c.addView(Ui.icon(this, R.drawable.ic_chevron, R.color.muted, 22));
        Ui.clickable(c, 16, R.color.surface, R.color.outline, v -> click.run());
        Ui.addCard(content, c);
    }

    private void showMore() {
        section("Lịch ca & dữ liệu");
        menuItem("Lịch ca A/B/C/D/E", "Xem lịch theo tháng, nhập từ file Excel/CSV, cài giờ ca", R.drawable.ic_calendar, () -> push("shifts", null));
        menuItem("Danh mục thiết bị", "Thiết bị, alias và KKS tùy chọn", R.drawable.ic_tools, () -> push("equipment", null));
        menuItem("Báo cáo", "Xuất PDF, Excel, CSV", R.drawable.ic_report, this::reportDialog);
        menuItem("Sao lưu / Khôi phục", "Tệp .elbackup mã hóa bằng mật khẩu", R.drawable.ic_backup, this::backupMenu);
        menuItem("Thùng rác", "Khôi phục hồ sơ trong 30 ngày", R.drawable.ic_delete, () -> push("trash", null));
        section("Trợ lý & cài đặt");
        menuItem("Cảnh báo", "Cảnh báo nội bộ theo ngưỡng đã cài", R.drawable.ic_bell, () -> push("alerts", null));
        menuItem("Trợ lý phân tích", "Phân tích nhanh dữ liệu, hoàn toàn offline", R.drawable.ic_sparkle, () -> push("assistant", null));
        menuItem("Cài đặt cảnh báo", "Ngưỡng luật cảnh báo chạy cục bộ", R.drawable.ic_bell, this::alertSettings);
        section("Thông tin");
        menuItem("Giới thiệu", "Phiên bản, bản quyền, quyền riêng tư", R.drawable.ic_info, () -> push("about", null));
        TextView foot = Ui.text(this, "© 2026 ANQT1 · Tất cả bản quyền được bảo lưu", 11.5f, false, R.color.muted);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Ui.dp(this, 16), 0, 0);
        content.addView(foot, Ui.matchWrap());
    }

    // ======================================================================
    // Biểu mẫu & hộp thoại
    // ======================================================================
    private String value(EditText e) { return e.getText() == null ? "" : e.getText().toString().trim(); }

    private LinearLayout form() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 4));
        return l;
    }
    private ScrollView scrolled(View v) { ScrollView s = new ScrollView(this); s.addView(v); return s; }

    /** Hộp thoại biểu mẫu: lỗi nhập liệu hiện ngay trên hộp thoại và KHÔNG làm mất dữ liệu đã nhập. */
    private void showForm(String title, LinearLayout form, String okLabel, Action save) {
        AlertDialog d = new AlertDialog.Builder(this).setTitle(title).setView(scrolled(form)).setNegativeButton("Hủy", null).setPositiveButton(okLabel, null).create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { save.run(); d.dismiss(); } catch (Exception e) { toast(msg(e)); }
        });
    }

    private void error(Exception e) { new AlertDialog.Builder(this).setTitle("Không thực hiện được").setMessage(msg(e)).setPositiveButton("Đóng", null).show(); }

    // ---------------------------------------------------------------- tệp đính kèm trong biểu mẫu
    /** Khối "Tệp đính kèm" trong biểu mẫu: nút chọn tệp + danh sách tệp đã chọn (chưa lưu, có nút bỏ chọn). */
    private final class AttachBox {
        final List<AttachmentStore.Source> files = new ArrayList<>();
        final LinearLayout listView;

        AttachBox(LinearLayout form) {
            LinearLayout box = new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            TextView l = Ui.text(MainActivity.this, "Tệp đính kèm (ảnh, PDF, Word, Excel)", 12, true, R.color.muted);
            l.setPadding(Ui.dp(MainActivity.this, 2), 0, 0, Ui.dp(MainActivity.this, 5));
            box.addView(l);
            listView = new LinearLayout(MainActivity.this);
            listView.setOrientation(LinearLayout.VERTICAL);
            box.addView(listView);
            box.addView(Ui.button(MainActivity.this, "Đính kèm tệp", Ui.BTN_OUTLINE, R.drawable.ic_attach, v -> { activeAttach = AttachBox.this; launchFilePicker(REQ_FORM_ATTACH, true); }));
            form.addView(box, Ui.margins(Ui.matchWrap(), MainActivity.this, 0, 0, 0, 12));
            refresh();
        }

        void add(AttachmentStore.Source s) {
            for (AttachmentStore.Source x : files) if (x.uri.equals(s.uri)) return;
            files.add(s);
            refresh();
        }

        void refresh() {
            listView.removeAllViews();
            for (final AttachmentStore.Source s : new ArrayList<>(files)) {
                LinearLayout rowL = new LinearLayout(MainActivity.this);
                rowL.setGravity(Gravity.CENTER_VERTICAL);
                rowL.setPadding(Ui.dp(MainActivity.this, 10), Ui.dp(MainActivity.this, 4), Ui.dp(MainActivity.this, 2), Ui.dp(MainActivity.this, 4));
                rowL.setBackground(Ui.shape(MainActivity.this, R.color.surface_alt, 10, 0));
                rowL.addView(Ui.icon(MainActivity.this, s.mime != null && s.mime.startsWith("image/") ? R.drawable.ic_image : R.drawable.ic_attach, R.color.primary, 18));
                String size = AttachmentStore.sizeLabel(s.size);
                TextView t = Ui.text(MainActivity.this, s.name + (size.isEmpty() ? "" : " · " + size), 13, false, R.color.ink);
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                t.setPadding(Ui.dp(MainActivity.this, 8), 0, Ui.dp(MainActivity.this, 4), 0);
                rowL.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                ImageView x = Ui.icon(MainActivity.this, R.drawable.ic_close, R.color.muted, 18);
                x.setPadding(Ui.dp(MainActivity.this, 9), Ui.dp(MainActivity.this, 9), Ui.dp(MainActivity.this, 9), Ui.dp(MainActivity.this, 9));
                x.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(MainActivity.this, 36), Ui.dp(MainActivity.this, 36)));
                x.setBackground(Ui.ripple(MainActivity.this, new GradientDrawable(), 18));
                x.setOnClickListener(v -> { files.remove(s); refresh(); });
                rowL.addView(x);
                listView.addView(rowL, Ui.margins(Ui.matchWrap(), MainActivity.this, 0, 0, 0, 6));
            }
        }
    }

    private void launchFilePicker(int request, boolean multiple) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, AttachmentStore.PICKER_MIME_TYPES);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, request);
    }

    /** Lấy danh sách URI người dùng chọn (một hoặc nhiều tệp) và giữ quyền đọc đến khi lưu. */
    private List<Uri> pickedUris(Intent data) {
        List<Uri> out = new ArrayList<>();
        ClipData cd = data.getClipData();
        if (cd != null) { for (int i = 0; i < cd.getItemCount(); i++) { Uri u = cd.getItemAt(i).getUri(); if (u != null) out.add(u); } }
        else if (data.getData() != null) out.add(data.getData());
        for (Uri u : out) { try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (RuntimeException ignored) { } }
        return out;
    }

    /** Lưu các tệp đã chọn trong biểu mẫu. Trả về thông báo lỗi (rỗng nếu tất cả đã lưu); không ném lỗi để không tạo trùng hồ sơ khi thử lại. */
    private String saveFiles(AttachBox box, String ownerType, String ownerId, String workId) {
        if (box == null || box.files.isEmpty()) return "";
        AttachmentStore store = new AttachmentStore(this);
        int failed = 0;
        StringBuilder err = new StringBuilder();
        for (AttachmentStore.Source s : box.files) {
            try { store.importUri(s.uri, ownerType, ownerId, workId, workId == null ? "Tài liệu" : "Phiếu công việc", s.name); }
            catch (Exception e) { failed++; err.append("\n• ").append(s.name).append(": ").append(msg(e)); }
        }
        return failed == 0 ? "" : "Không lưu được " + failed + " tệp:" + err;
    }

    // ---------------------------------------------------------------- tạo hồ sơ
    private void newAnomaly() { newAnomaly("", "", "", null); }

    private void newAnomaly(String equipment0, String kks0, String description0, List<AttachmentStore.Source> files0) {
        LinearLayout f = form();
        EditText title = Ui.edit(this, f, "Tên/tiêu đề bất thường *", "", false);
        EditText equipment = Ui.edit(this, f, "Tên thiết bị mô tả *", n(equipment0), false);
        EditText kks = Ui.edit(this, f, "KKS (không bắt buộc)", n(kks0), false);
        EditText description = Ui.edit(this, f, "Mô tả hiện tượng ban đầu", n(description0), true);
        EditText system = Ui.edit(this, f, "Hệ thống / khu vực", "", false);
        EditText location = Ui.edit(this, f, "Vị trí", "", false);
        EditText responsible = Ui.edit(this, f, "Người/bộ phận phụ trách", "", false);
        DateTimeField detected = new DateTimeField(this, f, "Thời điểm phát hiện", System.currentTimeMillis(), true);
        Picker severity = new Picker(this, f, "Mức độ", new String[]{"Thấp", "Trung bình", "Cao", "Nghiêm trọng"}, 1);
        AttachBox attach = new AttachBox(f);
        if (files0 != null) for (AttachmentStore.Source s : files0) attach.add(s);
        showForm("Tạo bất thường", f, "Lưu", () -> {
            String id = repo.createAnomaly(value(title), value(description), value(equipment), value(kks), value(system), value(location), severity.value(), value(responsible), detected.millis);
            repo.refreshAlerts(System.currentTimeMillis());
            String fail = saveFiles(attach, "ANOMALY", id, null);
            toast("Đã tạo hồ sơ " + repo.getAnomaly(id).code + (fail.isEmpty() ? "" : "\n" + fail));
            anomalyFilter = "Tất cả";
            tab = "Bất thường"; stack.clear(); push("anomaly", id);
        });
    }

    private void newDrawing() { newDrawing("", "", "", null); }

    private void newDrawing(String number0, String title0, String description0, List<AttachmentStore.Source> files0) {
        LinearLayout f = form();
        EditText number = Ui.edit(this, f, "Số bản vẽ chính xác *", n(number0), false);
        EditText title = Ui.edit(this, f, "Tên/tiêu đề bản vẽ", n(title0), false);
        EditText description = Ui.edit(this, f, "Mô tả", n(description0), true);
        EditText discipline = Ui.edit(this, f, "Chuyên ngành", "", false);
        EditText system = Ui.edit(this, f, "Hệ thống / khu vực", "", false);
        EditText equip = Ui.edit(this, f, "Thiết bị liên quan (mô tả)", "", false);
        AttachBox attach = new AttachBox(f);
        if (files0 != null) for (AttachmentStore.Source s : files0) attach.add(s);
        showForm("Tạo hồ sơ bản vẽ", f, "Lưu", () -> {
            String id = repo.createDrawing(value(number), value(title), value(description), value(discipline), value(system), value(equip));
            String fail = saveFiles(attach, "DRAWING", id, null);
            toast("Đã tạo hồ sơ bản vẽ" + (fail.isEmpty() ? "" : "\n" + fail));
            tab = "Bản vẽ"; stack.clear(); push("drawing", id);
        });
    }

    // ---------------------------------------------------------------- ghi công việc (3 trường + tệp) và gán vào hồ sơ
    private void chooseWorkTarget() {
        showSheet("Ghi nhận công việc cho", new String[]{"Bất thường thiết bị", "Hồ sơ bản vẽ"}, new int[]{R.drawable.ic_warning, R.drawable.ic_drawing}, new Runnable[]{this::chooseAnomalyWork, this::chooseDrawingWork});
    }

    private void chooseAnomalyWork() { quickAnomalyWork("", "", null); }
    private void chooseDrawingWork() { quickDrawingWork("", "", null); }

    /** Hộp thoại không tự đóng khi bấm nút chính: biểu mẫu còn nguyên để người dùng quay lại từ bước gán hồ sơ. */
    private interface FormAction { void run(AlertDialog dialog) throws Exception; }

    private AlertDialog showFormManual(String title, LinearLayout form, String okLabel, FormAction onOk) {
        AlertDialog d = new AlertDialog.Builder(this).setTitle(title).setView(scrolled(form)).setNegativeButton("Hủy", null).setPositiveButton(okLabel, null).create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { try { onOk.run(d); } catch (Exception e) { toast(msg(e)); } });
        return d;
    }

    /**
     * Ghi công việc bất thường: tên thiết bị, mã KKS (tùy chọn), nội dung, tệp đính kèm.
     * targetId != null (mở từ trang chi tiết) → ghi thẳng vào hồ sơ đó; ngược lại gợi ý bất thường liên quan nhất sau khi xác nhận.
     */
    private void quickAnomalyWork(String equipment0, String kks0, String targetId) {
        LinearLayout f = form();
        if (targetId != null) {
            HelpDeskRepository.Record t = repo.getAnomaly(targetId);
            if (t != null) f.addView(Ui.note(this, "Ghi vào hồ sơ " + t.code + " · " + n(t.title)));
        }
        EditText equipment = Ui.edit(this, f, "Tên thiết bị *", n(equipment0), false);
        EditText kks = Ui.edit(this, f, "Mã KKS (không bắt buộc)", n(kks0), false);
        EditText content = Ui.edit(this, f, "Nội dung công việc *", "", true);
        content.setMinLines(4);
        AttachBox attach = new AttachBox(f);
        showFormManual("Ghi công việc", f, "Xác nhận", d -> {
            String name = value(equipment), code = value(kks), text = value(content);
            if (name.isEmpty()) throw new Exception("Nhập tên thiết bị");
            if (text.isEmpty()) throw new Exception("Nhập nội dung công việc");
            if (targetId != null) { commitWork(false, targetId, name, code, text, attach, d); return; }
            showAssignDialog(false, name, code, text, attach, repo.suggestAnomalies(name, code, text, 3), d);
        });
    }

    /** Ghi công việc bản vẽ: số bản vẽ, tên/tiêu đề (nhập một trong hai), nội dung, tệp đính kèm; gợi ý theo số hoặc tên/tiêu đề. */
    private void quickDrawingWork(String number0, String title0, String targetId) {
        LinearLayout f = form();
        if (targetId != null) {
            HelpDeskRepository.Record t = repo.getDrawing(targetId);
            if (t != null) f.addView(Ui.note(this, "Ghi vào hồ sơ " + t.code + " · " + n(t.drawingNumber)));
        }
        EditText number = Ui.edit(this, f, "Số bản vẽ", n(number0), false);
        EditText title = Ui.edit(this, f, "Tên/tiêu đề bản vẽ", n(title0), false);
        EditText content = Ui.edit(this, f, "Nội dung công việc *", "", true);
        content.setMinLines(4);
        AttachBox attach = new AttachBox(f);
        showFormManual("Ghi công việc bản vẽ", f, "Xác nhận", d -> {
            String num = value(number), ttl = value(title), text = value(content);
            if (num.isEmpty() && ttl.isEmpty()) throw new Exception("Nhập số bản vẽ hoặc tên/tiêu đề bản vẽ");
            if (text.isEmpty()) throw new Exception("Nhập nội dung công việc");
            if (targetId != null) { commitWork(true, targetId, ttl, num, text, attach, d); return; }
            showAssignDialog(true, ttl, num, text, attach, repo.suggestDrawings(num, ttl, text, 3), d);
        });
    }

    /** Bước gán: hiện gợi ý tốt nhất (chọn được nhanh), hoặc chuyển sang chọn thủ công trong toàn bộ danh sách. */
    private void showAssignDialog(boolean drawing, String name, String code, String text, AttachBox attach, List<HelpDeskRepository.Suggestion> sg, AlertDialog workDialog) {
        if (sg.isEmpty()) { showPickAll(drawing, name, code, text, attach, workDialog, true); return; }
        LinearLayout box = form();
        box.addView(Ui.note(this, drawing ? "Bản vẽ liên quan nhất với nội dung bạn nhập. Chọn một bản vẽ rồi bấm “Gán vào hồ sơ này”, hoặc chọn bản vẽ khác."
                : "Bất thường liên quan nhất theo tên thiết bị + KKS. Chọn một hồ sơ rồi bấm “Gán vào hồ sơ này”, hoặc chọn bất thường khác."));
        final int[] sel = {0};
        final List<LinearLayout> cards = new ArrayList<>();
        final Runnable paint = () -> { for (int i = 0; i < cards.size(); i++) cards.get(i).setBackground(Ui.shape(this, sel[0] == i ? R.color.primary_soft : R.color.surface, 14, sel[0] == i ? R.color.primary : R.color.outline)); };
        for (int i = 0; i < sg.size(); i++) {
            final int idx = i;
            HelpDeskRepository.Suggestion s = sg.get(i);
            HelpDeskRepository.Record r = s.record;
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
            LinearLayout chips = row(Ui.chip(this, r.code, R.color.primary, R.color.primary_soft));
            if (i == 0) chips.addView(Ui.chip(this, "Gợi ý tốt nhất", R.color.success, R.color.success_soft));
            if (!drawing) chips.addView(statusChip(r.status));
            c.addView(chips);
            TextView t = Ui.text(this, drawing ? n(r.drawingNumber) : n(r.title), 15, true, R.color.ink);
            t.setPadding(0, Ui.dp(this, 6), 0, 0);
            c.addView(t);
            c.addView(Ui.text(this, drawing ? dash(r.title) : n(r.equipmentName) + " · KKS: " + dash(r.kks), 13, false, R.color.muted));
            if (s.reason != null && !s.reason.isEmpty()) {
                TextView why = Ui.text(this, s.reason, 12, true, R.color.success);
                why.setPadding(0, Ui.dp(this, 4), 0, 0);
                c.addView(why);
            }
            c.setClickable(true);
            c.setOnClickListener(v -> { sel[0] = idx; paint.run(); });
            cards.add(c);
            box.addView(c, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 8));
        }
        paint.run();
        AlertDialog d = new AlertDialog.Builder(this).setTitle("Gán công việc vào…").setView(scrolled(box))
                .setPositiveButton("Gán vào hồ sơ này", null).setNeutralButton(drawing ? "Chọn bản vẽ khác" : "Chọn bất thường khác", null).setNegativeButton("Quay lại", null).create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { if (commitWork(drawing, sg.get(sel[0]).record.id, name, code, text, attach, workDialog)) d.dismiss(); });
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> { d.dismiss(); showPickAll(drawing, name, code, text, attach, workDialog, false); });
    }

    /** Danh sách toàn bộ hồ sơ để tự chọn (có ô tìm kiếm không dấu). */
    private void showPickAll(boolean drawing, String name, String code, String text, AttachBox attach, AlertDialog workDialog, boolean noSuggestion) {
        final List<HelpDeskRepository.Record> all = drawing ? repo.listDrawings(false) : repo.listAnomalies(false);
        LinearLayout box = form();
        String what = drawing ? "bản vẽ" : "bất thường";
        box.addView(Ui.note(this, all.isEmpty() ? "Chưa có hồ sơ " + what + " nào để gán. Bạn có thể tạo hồ sơ mới từ nội dung vừa nhập."
                : (noSuggestion ? "Chưa tìm thấy " + what + " phù hợp với nội dung đã nhập. Hãy chọn thủ công trong danh sách." : "Chọn " + what + " để gán công việc.")));
        final AlertDialog[] holder = new AlertDialog[1];
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        final String[] query = {""};
        final Runnable refill = () -> {
            list.removeAllViews();
            int shown = 0, matched = 0;
            for (HelpDeskRepository.Record r : all) {
                String hay = drawing ? r.code + " " + r.drawingNumber + " " + r.title + " " + r.description : r.code + " " + r.title + " " + r.equipmentName + " " + r.kks + " " + r.description;
                if (!query[0].trim().isEmpty() && !vn.evnqt1.helpdesk.core.TextNormalizer.contains(hay, query[0])) continue;
                matched++;
                if (shown >= 60) continue;
                shown++;
                final HelpDeskRepository.Record rr = r;
                list.addView(pickRow(r, drawing, v -> { if (commitWork(drawing, rr.id, name, code, text, attach, workDialog)) holder[0].dismiss(); }), Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 8));
            }
            if (matched == 0 && !all.isEmpty()) list.addView(Ui.note(this, "Không có hồ sơ phù hợp từ khóa."));
            if (matched > shown) list.addView(Ui.note(this, "Còn " + (matched - shown) + " hồ sơ nữa — nhập từ khóa để thu hẹp danh sách."));
        };
        if (!all.isEmpty()) box.addView(searchBar(drawing ? "Tìm số bản vẽ hoặc tên…" : "Tìm theo mã, thiết bị, KKS…", "", q -> { query[0] = q; refill.run(); }), Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 10));
        box.addView(list);
        refill.run();
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(drawing ? "Chọn bản vẽ" : "Chọn bất thường").setView(scrolled(box)).setNegativeButton("Quay lại", null)
                .setNeutralButton(drawing ? "Tạo bản vẽ mới" : "Tạo bất thường mới", (dd, w) -> {
                    workDialog.dismiss();
                    if (drawing) newDrawing(code, name, text, attach.files); else newAnomaly(name, code, text, attach.files);
                });
        holder[0] = b.create();
        holder[0].show();
    }

    private View pickRow(HelpDeskRepository.Record r, boolean drawing, View.OnClickListener l) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        LinearLayout chips = row(Ui.chip(this, r.code, R.color.primary, R.color.primary_soft));
        if (!drawing) chips.addView(statusChip(r.status));
        c.addView(chips);
        TextView t = Ui.text(this, drawing ? n(r.drawingNumber) : n(r.title), 14.5f, true, R.color.ink);
        t.setPadding(0, Ui.dp(this, 5), 0, 0);
        c.addView(t);
        c.addView(Ui.text(this, drawing ? dash(r.title) : n(r.equipmentName) + " · KKS: " + dash(r.kks), 12.5f, false, R.color.muted));
        Ui.clickable(c, 12, R.color.surface, R.color.outline, l);
        return c;
    }

    /**
     * Lưu phiếu công việc vào hồ sơ đã chọn (+ tệp đính kèm của phiếu). name/code: bất thường = (tên thiết bị, KKS); bản vẽ = (tên/tiêu đề, số bản vẽ).
     * Thời gian là lúc xác nhận; ca trực và nhân sự tự gán theo lịch ca. Trả về true nếu đã lưu.
     */
    private boolean commitWork(boolean drawing, String id, String name, String code, String text, AttachBox attach, AlertDialog workDialog) {
        try {
            long now = System.currentTimeMillis();
            HelpDeskRepository.WorkInput in = new HelpDeskRepository.WorkInput();
            in.workAt = now; in.description = text; in.refName = name; in.refCode = code;
            ShiftOccurrence cur = repo.currentShift(now);
            in.participants = cur == null ? "" : n(cur.roster);
            in.workType = "Ghi nhận công việc";
            in.result = drawing ? "Ghi chú" : "Chưa thay đổi trạng thái";
            String workId;
            if (drawing) workId = repo.addDrawingWork(id, in);
            else { workId = repo.addAnomalyWork(id, in); repo.refreshAlerts(now); }
            String fail = saveFiles(attach, drawing ? "DRAWING" : "ANOMALY", id, workId);
            toast("Đã lưu công việc" + (fail.isEmpty() ? "" : "\n" + fail));
            workDialog.dismiss();
            String route = drawing ? "drawing" : "anomaly";
            if (stack.isEmpty() || !route.equals(current().name) || !id.equals(current().arg)) { tab = drawing ? "Bản vẽ" : "Bất thường"; stack.clear(); push(route, id); } else rerender();
            return true;
        } catch (Exception e) { toast(msg(e)); return false; }
    }

    private void revisionForm(String drawingId) {
        LinearLayout f = form();
        EditText old = Ui.edit(this, f, "Revision trước (nếu có)", "", false);
        EditText next = Ui.edit(this, f, "Revision mới *", "", false);
        DateTimeField date = new DateTimeField(this, f, "Ngày giờ chỉnh sửa", System.currentTimeMillis(), true);
        EditText desc = Ui.edit(this, f, "Nội dung thay đổi", "", true);
        EditText reason = Ui.edit(this, f, "Lý do chỉnh sửa", "", true);
        EditText area = Ui.edit(this, f, "Khu vực/vùng bản vẽ bị ảnh hưởng", "", false);
        EditText author = Ui.edit(this, f, "Người thực hiện", "", false);
        EditText review = Ui.edit(this, f, "Người kiểm tra/phê duyệt", "", false);
        EditText notes = Ui.edit(this, f, "Ghi chú", "", true);
        showForm("Ghi nhận lần chỉnh sửa bản vẽ", f, "Tạo revision", () -> {
            repo.addDrawingRevision(drawingId, value(old), value(next), date.millis, value(desc), value(reason), value(area), value(author), value(review), value(notes));
            toast("Đã tạo revision mới; lịch sử cũ được giữ nguyên");
            rerender();
        });
    }

    private void confirmDelete(String type, String id) {
        new AlertDialog.Builder(this).setTitle("Đưa hồ sơ vào thùng rác?").setMessage("Hồ sơ và lịch sử sẽ được giữ 30 ngày để có thể khôi phục. Không xóa ngay tệp dữ liệu.")
                .setNegativeButton("Hủy", null).setPositiveButton("Đưa vào thùng rác", (d, w) -> { repo.softDelete(type, id); repo.refreshAlerts(System.currentTimeMillis()); toast("Đã chuyển vào thùng rác"); stack.clear(); push("trash", null); }).show();
    }

    private void pickAttachment(String ownerType, String ownerId) {
        pendingOwnerType = ownerType; pendingOwnerId = ownerId;
        launchFilePicker(REQ_ATTACHMENT, true);
    }

    /** Tệp chọn từ nút "Đính kèm tệp" trên trang chi tiết: lưu ngay vào hồ sơ. */
    private void onDetailFilesPicked(Intent data) {
        AttachmentStore store = new AttachmentStore(this);
        int ok = 0;
        StringBuilder err = new StringBuilder();
        for (Uri u : pickedUris(data)) {
            try { store.importUri(u, pendingOwnerType, pendingOwnerId, null, "Tài liệu", "Tệp được đính kèm từ màn hình hồ sơ"); ok++; }
            catch (Exception e) { err.append("\n• ").append(msg(e)); }
        }
        toast((ok > 0 ? "Đã lưu " + ok + " tệp đính kèm" : "Không lưu được tệp") + err);
        rerender();
    }

    /** Tệp chọn trong biểu mẫu đang mở: chỉ hiện trong danh sách chờ, chưa lưu cho tới khi bấm Lưu/Xác nhận. */
    private void onFormFilesPicked(Intent data) {
        AttachBox box = activeAttach;
        if (box == null) return;
        AttachmentStore store = new AttachmentStore(this);
        StringBuilder err = new StringBuilder();
        for (Uri u : pickedUris(data)) {
            try { box.add(store.inspect(u)); } catch (Exception e) { err.append(err.length() == 0 ? "" : "\n").append(msg(e)); }
        }
        if (err.length() > 0) toast(err.toString());
    }

    private void openAttachment(AttachmentStore.Item it) {
        if (it.path == null || !new java.io.File(it.path).exists()) { toast("Tệp không còn trong bộ nhớ ứng dụng"); return; }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(AttachmentStore.uriFor(this, it.id), it.mime == null ? "*/*" : it.mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (android.content.ActivityNotFoundException e) { toast("Máy chưa có ứng dụng mở được tệp “" + it.name + "”"); }
        catch (RuntimeException e) { toast(msg(e)); }
    }

    private void confirmDeleteFile(AttachmentStore.Item it) {
        new AlertDialog.Builder(this).setTitle("Xóa tệp đính kèm?").setMessage(it.name + "\n\nTệp sẽ bị xóa khỏi hồ sơ và khỏi bộ nhớ ứng dụng.")
                .setNegativeButton("Hủy", null).setPositiveButton("Xóa", (d, w) -> { new AttachmentStore(this).delete(it.id); toast("Đã xóa tệp"); rerender(); }).show();
    }

    /** Một dòng tệp đính kèm: chạm để mở bằng ứng dụng xem tệp, biểu tượng thùng rác để xóa. */
    private View fileRow(AttachmentStore.Item it) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6));
        r.addView(Ui.icon(this, it.isImage() ? R.drawable.ic_image : R.drawable.ic_attach, R.color.primary, 20));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 4), 0);
        TextView nm = Ui.text(this, it.name, 13.5f, true, R.color.ink);
        nm.setSingleLine(true);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        tx.addView(nm);
        String size = AttachmentStore.sizeLabel(it.size);
        tx.addView(Ui.text(this, (size.isEmpty() ? "" : size + " · ") + fmt(it.addedAt), 11.5f, false, R.color.muted));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageView del = Ui.icon(this, R.drawable.ic_delete, R.color.muted, 18);
        del.setPadding(Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9));
        del.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        del.setBackground(Ui.ripple(this, new GradientDrawable(), 18));
        del.setOnClickListener(v -> confirmDeleteFile(it));
        r.addView(del);
        Ui.clickable(r, 10, R.color.surface_alt, R.color.outline, v -> openAttachment(it));
        return r;
    }

    /** Mục "Tệp đính kèm" của hồ sơ: các tệp gắn trực tiếp vào hồ sơ (tệp của từng phiếu công việc hiện trong lịch sử công việc). */
    private void attachmentSection(List<AttachmentStore.Item> direct) {
        if (direct.isEmpty()) return;
        section("Tệp đính kèm (" + direct.size() + ")");
        for (AttachmentStore.Item it : direct) content.addView(fileRow(it), Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 6));
    }

    private java.util.Map<String, List<AttachmentStore.Item>> filesByWork(List<AttachmentStore.Item> all) {
        java.util.Map<String, List<AttachmentStore.Item>> m = new java.util.HashMap<>();
        for (AttachmentStore.Item it : all) if (it.workEntryId != null) { List<AttachmentStore.Item> l = m.get(it.workEntryId); if (l == null) { l = new ArrayList<>(); m.put(it.workEntryId, l); } l.add(it); }
        return m;
    }

    private List<AttachmentStore.Item> directFiles(List<AttachmentStore.Item> all) {
        List<AttachmentStore.Item> l = new ArrayList<>();
        for (AttachmentStore.Item it : all) if (it.workEntryId == null) l.add(it);
        return l;
    }

    // ======================================================================
    // Trang chi tiết
    // ======================================================================
    private void timelineItem(String head, String[] chipLabels, String body, boolean last) { timelineItem(head, chipLabels, body, last, null); }

    private void timelineItem(String head, String[] chipLabels, String body, boolean last, List<AttachmentStore.Item> files) {
        LinearLayout rowL = new LinearLayout(this);
        rowL.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        View dot = new View(this);
        dot.setBackground(Ui.shape(this, R.color.brand, 6, 0));
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.dp(this, 12), Ui.dp(this, 12));
        dp.topMargin = Ui.dp(this, 5);
        rail.addView(dot, dp);
        if (!last) {
            View line = new View(this);
            line.setBackgroundColor(Ui.color(this, R.color.outline));
            rail.addView(line, new LinearLayout.LayoutParams(Ui.dp(this, 2), 0, 1));
        }
        rowL.addView(rail, new LinearLayout.LayoutParams(Ui.dp(this, 22), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, last ? 0 : 16));
        box.addView(Ui.text(this, head, 12.5f, true, R.color.muted));
        if (chipLabels.length > 0) {
            LinearLayout chips = new LinearLayout(this);
            chips.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 2));
            for (String s : chipLabels) if (s != null && !s.isEmpty()) chips.addView(Ui.chip(this, s, R.color.primary, R.color.primary_soft), Ui.margins(Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), this, 0, 0, 6, 0));
            box.addView(chips);
        }
        if (!body.trim().isEmpty()) box.addView(Ui.text(this, body.trim(), 14, false, R.color.ink));
        if (files != null) for (AttachmentStore.Item it : files) box.addView(fileRow(it), Ui.margins(Ui.matchWrap(), this, 0, 6, 0, 0));
        rowL.addView(box, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        content.addView(rowL, Ui.matchWrap());
    }

    /** parentName/parentCode: giá trị của hồ sơ chứa phiếu; chỉ hiện dòng "Phiếu ghi" khi người dùng nhập khác với hồ sơ. */
    private String historyBody(HelpDeskRepository.HistoryItem h, boolean withResult, String parentName, String parentCode) {
        StringBuilder b = new StringBuilder(n(h.description));
        boolean diffName = !n(h.refName).isEmpty() && !vn.evnqt1.helpdesk.core.MatchSuggester.compact(h.refName).equals(vn.evnqt1.helpdesk.core.MatchSuggester.compact(parentName));
        boolean diffCode = !n(h.refCode).isEmpty() && !vn.evnqt1.helpdesk.core.MatchSuggester.compact(h.refCode).equals(vn.evnqt1.helpdesk.core.MatchSuggester.compact(parentCode));
        if (diffName || diffCode) b.append("\nPhiếu ghi: ").append(dash(h.refName)).append(" · ").append(dash(h.refCode));
        if (!n(h.actions).isEmpty()) b.append("\nHành động: ").append(h.actions);
        if (!n(h.findings).isEmpty()) b.append("\nPhát hiện: ").append(h.findings);
        if (withResult && !n(h.result).isEmpty()) b.append("\nKết quả: ").append(h.result);
        if (!n(h.author).isEmpty()) b.append("\nNgười thực hiện: ").append(h.author);
        return b.toString();
    }

    private void showAnomalyDetail(String id) {
        HelpDeskRepository.Record r = repo.getAnomaly(id);
        if (r == null) { Ui.addCard(content, errorCard("Không tìm thấy hồ sơ.")); return; }
        LinearLayout c = Ui.card(this);
        c.addView(row(Ui.chip(this, r.code, R.color.primary, R.color.primary_soft), statusChip(r.status), severityChip(r.severity)));
        TextView t = Ui.text(this, r.title, 20, true, R.color.ink);
        t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        c.addView(t);
        c.addView(Ui.divider(this));
        c.addView(infoRows(new String[][]{{"Thiết bị", r.equipmentName}, {"KKS", r.kks}, {"Hệ thống", r.systemArea}, {"Vị trí", r.location}, {"Phát hiện", fmt(r.detectedAt)}, {"Phụ trách", r.responsible}}));
        if (!n(r.description).isEmpty()) {
            c.addView(Ui.divider(this));
            c.addView(Ui.text(this, r.description, 14, false, R.color.ink));
        }
        Ui.addCard(content, c);
        buttonRow(Ui.button(this, "Ghi công việc", Ui.BTN_PRIMARY, R.drawable.ic_edit, v -> quickAnomalyWork(r.equipmentName, r.kks, id)), Ui.button(this, "Đổi trạng thái", Ui.BTN_TONAL, R.drawable.ic_check, v -> statusDialog(id, r.status)));
        buttonRow(Ui.button(this, "Đính kèm tệp", Ui.BTN_OUTLINE, R.drawable.ic_attach, v -> pickAttachment("ANOMALY", id)), Ui.button(this, "Thùng rác", Ui.BTN_DANGER, R.drawable.ic_delete, v -> confirmDelete("ANOMALY", id)));
        List<AttachmentStore.Item> files = new AttachmentStore(this).list("ANOMALY", id);
        attachmentSection(directFiles(files));
        java.util.Map<String, List<AttachmentStore.Item>> byWork = filesByWork(files);
        section("Lịch sử công việc");
        List<HelpDeskRepository.HistoryItem> hist = repo.history("ANOMALY", id);
        if (hist.isEmpty()) empty("Chưa có công việc nào được ghi nhận.");
        for (int i = hist.size() - 1; i >= 0; i--) {
            HelpDeskRepository.HistoryItem h = hist.get(i);
            timelineItem(fmt(h.workAt) + (n(h.shift).isEmpty() ? "" : " · Ca " + h.shift), new String[]{h.type, h.result}, historyBody(h, false, r.equipmentName, r.kks), i == 0, byWork.get(h.id));
        }
    }

    private void statusDialog(String id, String currentStatus) {
        final String[] st = {"Chưa xử lý", "Đang xử lý", "Đã xử lý"};
        int sel = 0;
        for (int i = 0; i < st.length; i++) if (st[i].equals(currentStatus)) sel = i;
        new AlertDialog.Builder(this).setTitle("Chuyển trạng thái").setSingleChoiceItems(st, sel, (d, which) -> {
            d.dismiss();
            try { repo.updateAnomalyStatus(id, st[which], "Người dùng", "Cập nhật thủ công"); repo.refreshAlerts(System.currentTimeMillis()); rerender(); } catch (Exception e) { error(e); }
        }).setNegativeButton("Hủy", null).show();
    }

    private void showDrawingDetail(String id) {
        HelpDeskRepository.Record r = repo.getDrawing(id);
        if (r == null) { Ui.addCard(content, errorCard("Không tìm thấy hồ sơ.")); return; }
        LinearLayout c = Ui.card(this);
        c.addView(row(Ui.chip(this, r.code, R.color.primary, R.color.primary_soft), Ui.chip(this, "Rev " + (r.currentRevision == null ? "—" : r.currentRevision), R.color.success, R.color.success_soft)));
        TextView t = Ui.text(this, r.drawingNumber, 20, true, R.color.ink);
        t.setPadding(0, Ui.dp(this, 10), 0, 0);
        c.addView(t);
        if (!n(r.title).isEmpty()) c.addView(Ui.text(this, r.title, 14, false, R.color.muted));
        c.addView(Ui.divider(this));
        c.addView(infoRows(new String[][]{{"Chuyên ngành", r.discipline}, {"Hệ thống", r.systemArea}, {"Phiên công việc", String.valueOf(r.sessionCount)}, {"Số revision", String.valueOf(r.revisionCount)}}));
        if (!n(r.description).isEmpty()) { c.addView(Ui.divider(this)); c.addView(Ui.text(this, r.description, 14, false, R.color.ink)); }
        Ui.addCard(content, c);
        buttonRow(Ui.button(this, "Ghi công việc", Ui.BTN_PRIMARY, R.drawable.ic_edit, v -> quickDrawingWork(r.drawingNumber, r.title, id)), Ui.button(this, "Ghi Revision", Ui.BTN_TONAL, R.drawable.ic_add, v -> revisionForm(id)));
        buttonRow(Ui.button(this, "Đính kèm tệp", Ui.BTN_OUTLINE, R.drawable.ic_attach, v -> pickAttachment("DRAWING", id)), Ui.button(this, "Thùng rác", Ui.BTN_DANGER, R.drawable.ic_delete, v -> confirmDelete("DRAWING", id)));
        section("Timeline revision");
        List<String[]> revs = repo.revisions(id);
        if (revs.isEmpty()) empty("Chưa có lần chỉnh sửa nào.");
        for (int i = 0; i < revs.size(); i++) {
            String[] rev = revs.get(i);
            long when = 0; try { when = Long.parseLong(rev[2]); } catch (Exception ignored) { }
            String body = n(rev[3]) + (n(rev[4]).isEmpty() ? "" : "\nLý do: " + rev[4]) + (n(rev[5]).isEmpty() ? "" : "\nVùng ảnh hưởng: " + rev[5]) + (n(rev[6]).isEmpty() ? "" : "\nThực hiện: " + rev[6]) + (n(rev[7]).isEmpty() ? "" : " · Kiểm tra: " + rev[7]);
            timelineItem(fmt(when), new String[]{"Rev " + rev[0] + (n(rev[1]).isEmpty() ? "" : " (trước: " + rev[1] + ")")}, body, i == revs.size() - 1);
        }
        List<AttachmentStore.Item> files = new AttachmentStore(this).list("DRAWING", id);
        attachmentSection(directFiles(files));
        java.util.Map<String, List<AttachmentStore.Item>> byWork = filesByWork(files);
        section("Lịch sử công việc");
        List<HelpDeskRepository.HistoryItem> hist = repo.history("DRAWING", id);
        if (hist.isEmpty()) empty("Chưa có công việc nào được ghi nhận.");
        for (int i = hist.size() - 1; i >= 0; i--) {
            HelpDeskRepository.HistoryItem h = hist.get(i);
            timelineItem(fmt(h.workAt) + (n(h.shift).isEmpty() ? "" : " · Ca " + h.shift), new String[]{h.type, h.result}, historyBody(h, false, r.title, r.drawingNumber), i == 0, byWork.get(h.id));
        }
    }

    // ======================================================================
    // Tìm kiếm, thùng rác, thiết bị, giới thiệu
    // ======================================================================
    private void searchDialog() {
        LinearLayout f = form();
        EditText q = Ui.edit(this, f, "Từ khóa (có dấu hoặc không dấu)", "", false);
        showForm("Tìm kiếm toàn cục", f, "Tìm", () -> { String term = value(q); if (term.isEmpty()) throw new Exception("Nhập từ khóa cần tìm"); push("search", term); });
    }

    private void showSearch(String term) {
        List<HelpDeskRepository.Record> results = repo.search(term == null ? "" : term);
        content.addView(Ui.note(this, results.size() + " kết quả cho “" + term + "”"));
        if (results.isEmpty()) empty("Không tìm thấy hồ sơ phù hợp.");
        for (HelpDeskRepository.Record r : results) recordCard(content, r, r.drawingNumber != null);
    }

    private void showTrash() {
        content.addView(Ui.note(this, "Hồ sơ trong thùng rác được giữ 30 ngày rồi tự xóa."));
        List<HelpDeskRepository.Record> all = repo.trash();
        if (all.isEmpty()) { empty("Thùng rác đang trống."); return; }
        for (HelpDeskRepository.Record r : all) {
            boolean drawing = r.drawingNumber != null;
            LinearLayout c = Ui.card(this);
            c.addView(row(Ui.chip(this, (drawing ? "BẢN VẼ · " : "BẤT THƯỜNG · ") + r.code, R.color.muted, R.color.surface_alt)));
            TextView t = Ui.text(this, drawing ? r.drawingNumber : r.title, 15, true, R.color.ink);
            t.setPadding(0, Ui.dp(this, 8), 0, 0);
            c.addView(t);
            c.addView(Ui.text(this, "Đã xóa: " + fmt(r.deletedAt), 12, false, R.color.muted));
            LinearLayout b = Ui.button(this, "Khôi phục", Ui.BTN_TONAL, R.drawable.ic_check, v -> { repo.restore(drawing ? "DRAWING" : "ANOMALY", r.id); toast("Đã khôi phục hồ sơ và lịch sử"); rerender(); });
            c.addView(b, Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 0));
            Ui.addCard(content, c);
        }
    }

    private void showEquipment() {
        content.addView(Ui.button(this, "Thêm thiết bị", Ui.BTN_PRIMARY, R.drawable.ic_add, v -> newEquipment()), Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12));
        boolean any = false;
        try (android.database.Cursor c = LocalDatabase.get(this).getReadableDatabase().rawQuery("SELECT canonical_name,aliases,kks,system_area,area FROM equipment ORDER BY canonical_name", null)) {
            while (c.moveToNext()) {
                any = true;
                LinearLayout card = Ui.card(this);
                card.addView(Ui.text(this, c.getString(0), 15, true, R.color.ink));
                card.addView(Ui.text(this, "KKS: " + dash(c.getString(2)) + " · " + dash(c.getString(3)) + " / " + dash(c.getString(4)), 12.5f, false, R.color.muted));
                if (!n(c.getString(1)).isEmpty()) card.addView(Ui.text(this, "Tên gọi khác: " + c.getString(1), 12.5f, false, R.color.muted));
                Ui.addCard(content, card);
            }
        }
        if (!any) empty("Chưa có thiết bị trong danh mục.");
    }

    private void newEquipment() {
        LinearLayout f = form();
        EditText name = Ui.edit(this, f, "Tên thiết bị chuẩn *", "", false);
        EditText aliases = Ui.edit(this, f, "Tên gọi khác / alias, phân tách bằng dấu phẩy", "", false);
        EditText kks = Ui.edit(this, f, "KKS (tùy chọn, nhập chính xác)", "", false);
        EditText system = Ui.edit(this, f, "Hệ thống", "", false);
        EditText area = Ui.edit(this, f, "Khu vực", "", false);
        EditText desc = Ui.edit(this, f, "Mô tả", "", true);
        showForm("Thêm thiết bị", f, "Lưu", () -> {
            String nm = value(name);
            if (nm.isEmpty()) throw new Exception("Tên thiết bị là bắt buộc");
            android.content.ContentValues v = new android.content.ContentValues();
            long now = System.currentTimeMillis();
            v.put("id", java.util.UUID.randomUUID().toString()); v.put("canonical_name", nm); v.put("aliases", value(aliases));
            v.put("kks", value(kks).isEmpty() ? null : value(kks)); v.put("system_area", value(system)); v.put("area", value(area)); v.put("description", value(desc));
            v.put("created_at", now); v.put("updated_at", now);
            LocalDatabase.get(this).getWritableDatabase().insertOrThrow("equipment", null, v);
            toast("Đã thêm thiết bị");
            rerender();
        });
    }

    private void showAbout() {
        LinearLayout c = Ui.card(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 22));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 88), Ui.dp(this, 88)));
        c.addView(logo);
        TextView name = Ui.text(this, getString(R.string.app_name), 22, true, R.color.ink);
        name.setPadding(0, Ui.dp(this, 12), 0, 0);
        c.addView(name);
        String ver = "";
        try { ver = "Phiên bản " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        c.addView(Ui.text(this, ver, 13, false, R.color.muted));
        TextView desc = Ui.text(this, "Sổ quản lý bất thường thiết bị và chỉnh sửa bản vẽ cho vận hành nhà máy điện.", 14, false, R.color.ink);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, Ui.dp(this, 12), 0, 0);
        c.addView(desc);
        Ui.addCard(content, c);

        section("Quyền riêng tư & an toàn");
        LinearLayout p = Ui.card(this);
        p.addView(Ui.text(this, "Dữ liệu lưu trên thiết bị. Không có tài khoản, backend, analytics, quảng cáo hoặc đồng bộ đám mây. Ứng dụng hoạt động hoàn toàn offline, không xin quyền truy cập mạng và không có chức năng AI. Ứng dụng không điều khiển DCS/PLC và không thay thế quy trình vận hành/an toàn.", 13.5f, false, R.color.ink));
        Ui.addCard(content, p);

        section("Bản quyền");
        LinearLayout cr = Ui.card(this);
        cr.setBackground(Ui.shape(this, R.color.primary_soft, 16, 0));
        TextView head = Ui.text(this, "© 2026 ANQT1", 15, true, R.color.primary);
        cr.addView(head);
        TextView body = Ui.text(this, "Tất cả bản quyền thuộc về ANQT1. Mọi hành vi sao chép, phân phối hoặc sử dụng trái phép dưới mọi hình thức đều bị nghiêm cấm.", 14, false, R.color.ink);
        body.setPadding(0, Ui.dp(this, 6), 0, 0);
        cr.addView(body);
        Ui.addCard(content, cr);
    }

    // ======================================================================
    // Báo cáo, sao lưu, cài đặt
    // ======================================================================
    private void reportDialog() {
        LinearLayout f = form();
        Picker scope = new Picker(this, f, "Phạm vi", new String[]{"Chung: Bất thường + Bản vẽ", "Chỉ bất thường thiết bị", "Chỉ bản vẽ"}, 0);
        Picker format = new Picker(this, f, "Định dạng", new String[]{"CSV", "Excel (.xlsx)", "PDF"}, 0);
        Calendar y2020 = Calendar.getInstance(Ui.VN); y2020.clear(); y2020.set(2020, Calendar.JANUARY, 1);
        DateTimeField from = new DateTimeField(this, f, "Từ ngày", y2020.getTimeInMillis(), false);
        Calendar today = Calendar.getInstance(Ui.VN); today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);
        DateTimeField to = new DateTimeField(this, f, "Đến ngày", today.getTimeInMillis(), false);
        showForm("Tạo báo cáo offline", f, "Tiếp tục", () -> {
            pendingReportScope = scope.index == 1 ? "ANOMALY" : scope.index == 2 ? "DRAWING" : "ALL";
            pendingExportType = format.value();
            long start = from.millis, end = to.millis + 86_400_000L - 1;
            if (start > end) throw new Exception("Khoảng ngày không hợp lệ");
            String ext = "CSV".equals(pendingExportType) ? "csv" : pendingExportType.startsWith("Excel") ? "xlsx" : "pdf";
            String mime = "csv".equals(ext) ? "text/csv" : "xlsx".equals(ext) ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "application/pdf";
            pendingReportFrom = start; pendingReportTo = end;
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE); i.setType(mime);
            i.putExtra(Intent.EXTRA_TITLE, "EVNQT1_Bao_cao_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + "." + ext);
            startActivityForResult(i, REQ_EXPORT);
        });
    }

    private void backupMenu() {
        new AlertDialog.Builder(this).setTitle("Sao lưu / Khôi phục").setItems(new String[]{"Tạo backup mã hóa .elbackup", "Khôi phục thay thế dữ liệu từ backup"}, (d, w) -> backupPasswordDialog(w == 1)).show();
    }

    private void backupPasswordDialog(boolean restore) {
        LinearLayout f = form();
        f.addView(Ui.note(this, "Tệp backup có thể chứa dữ liệu kỹ thuật và tệp đính kèm; hãy bảo vệ mật khẩu."));
        EditText password = Ui.edit(this, f, "Mật khẩu backup (tối thiểu 10 ký tự)", "", false);
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        showForm(restore ? "Mật khẩu giải mã backup" : "Tạo backup an toàn", f, "Tiếp tục", () -> {
            pendingBackupPassword = value(password);
            if (pendingBackupPassword.length() < 10) throw new Exception("Mật khẩu cần tối thiểu 10 ký tự");
            if (restore) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/octet-stream");
                startActivityForResult(i, REQ_IMPORT);
            } else {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/octet-stream");
                i.putExtra(Intent.EXTRA_TITLE, "EVNQT1_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + ".elbackup");
                startActivityForResult(i, REQ_BACKUP);
            }
        });
    }


    private void alertSettings() {
        LinearLayout f = form();
        EditText overdue = Ui.edit(this, f, "Số ngày tồn tại trước cảnh báo", repo.getSetting("alert_overdue_days", "7"), false);
        EditText stale = Ui.edit(this, f, "Số ngày không cập nhật", repo.getSetting("alert_stale_days", "3"), false);
        EditText repeat = Ui.edit(this, f, "Số bất thường lặp lại", repo.getSetting("alert_repeat_count", "3"), false);
        EditText window = Ui.edit(this, f, "Cửa sổ lặp lại (ngày)", repo.getSetting("alert_repeat_days", "30"), false);
        for (EditText e : new EditText[]{overdue, stale, repeat, window}) e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        showForm("Ngưỡng cảnh báo nội bộ", f, "Lưu", () -> {
            long a, b, c, e;
            try { a = Long.parseLong(value(overdue)); b = Long.parseLong(value(stale)); c = Long.parseLong(value(repeat)); e = Long.parseLong(value(window)); }
            catch (NumberFormatException ex) { throw new Exception("Các ngưỡng phải là số nguyên"); }
            if (a < 1 || b < 1 || c < 2 || e < 1) throw new Exception("Ngưỡng phải là số dương; số lần lặp từ 2 trở lên");
            repo.setSetting("alert_overdue_days", String.valueOf(a)); repo.setSetting("alert_stale_days", String.valueOf(b));
            repo.setSetting("alert_repeat_count", String.valueOf(c)); repo.setSetting("alert_repeat_days", String.valueOf(e));
            repo.refreshAlerts(System.currentTimeMillis());
            toast("Đã cập nhật bộ luật cảnh báo cục bộ");
            push("alerts", null);
        });
    }

    // ======================================================================
    // Lịch ca trực
    // ======================================================================
    private long monthStart(int y, int m) { return LocalDate.of(y, m, 1).atStartOfDay(ScheduleRules.ZONE).toInstant().toEpochMilli(); }
    private long nextMonthStart(int y, int m) { return LocalDate.of(y, m, 1).plusMonths(1).atStartOfDay(ScheduleRules.ZONE).toInstant().toEpochMilli(); }

    private void shiftMonth(int delta) {
        LocalDate d = LocalDate.of(shiftYear, shiftMonth, 1).plusMonths(delta);
        shiftYear = d.getYear(); shiftMonth = d.getMonthValue();
        rerender();
    }

    private TextView crewPill(String crew, boolean issue) {
        TextView p;
        if (crew == null) {
            p = Ui.text(this, "—", 14, true, R.color.danger);
            p.setBackground(Ui.shape(this, issue ? R.color.danger_soft : R.color.surface_alt, 10, 0));
        } else {
            int col = crewColor(crew);
            p = Ui.text(this, crew, 14, true, col);
            GradientDrawable g = new GradientDrawable();
            g.setColor(Ui.alpha(Ui.color(this, col), 0x26));
            g.setCornerRadius(Ui.dp(this, 10));
            if (issue) g.setStroke(Ui.dp(this, 1.5f), Ui.color(this, R.color.danger));
            p.setBackground(g);
        }
        p.setGravity(Gravity.CENTER);
        p.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
        return p;
    }

    private void showShifts() {
        // Khối nhập từ file
        LinearLayout imp = Ui.card(this);
        GradientDrawable ig = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Ui.color(this, R.color.brand), Ui.color(this, R.color.brand_deep)});
        ig.setCornerRadius(Ui.dp(this, 20));
        imp.setBackground(ig);
        imp.setPadding(Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.icon(this, R.drawable.ic_calendar, R.color.accent, 22));
        TextView ht = Ui.text(this, "Nhập lịch ca từ file Excel", 17, true, R.color.on_brand);
        ht.setPadding(Ui.dp(this, 8), 0, 0, 0);
        head.addView(ht);
        imp.addView(head);
        TextView hd = Ui.text(this, "Chọn file .xlsx hoặc .csv theo mẫu: bảng Ngày/Thứ/Sáng/Chiều/Đêm và danh sách nhân sự các kíp. Ứng dụng đọc trực tiếp trong máy (không cần mạng), kiểm tra với lịch thật rồi cho bạn xem lại trước khi lưu.", 13, false, R.color.on_brand);
        hd.setAlpha(0.85f);
        hd.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));
        imp.addView(hd);
        imp.addView(Ui.button(this, "Chọn file lịch ca", Ui.BTN_TONAL, R.drawable.ic_backup, v -> startScheduleImport()));
        LinearLayout sub = new LinearLayout(this);
        sub.addView(Ui.button(this, "Tải file mẫu", Ui.BTN_TONAL, R.drawable.ic_drawing, v -> saveSampleTemplate()), Ui.margins(Ui.weight(1), this, 0, 8, 4, 0));
        sub.addView(Ui.button(this, "Cài giờ ca", Ui.BTN_TONAL, R.drawable.ic_clock, v -> shiftTimeDialog()), Ui.margins(Ui.weight(1), this, 4, 8, 0, 0));
        imp.addView(sub);
        String[][] tm = shiftTimes();
        TextView cur = Ui.text(this, "Giờ ca hiện tại: Sáng " + tm[0][0] + "–" + tm[1][0] + " · Chiều " + tm[0][1] + "–" + tm[1][1] + " · Đêm " + tm[0][2] + "–" + tm[1][2], 12, false, R.color.on_brand);
        cur.setAlpha(0.8f);
        cur.setPadding(0, Ui.dp(this, 10), 0, 0);
        imp.addView(cur);
        Ui.addCard(content, imp);

        // Chọn tháng
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        ImageView prev = Ui.icon(this, R.drawable.ic_back, R.color.ink, 22);
        prev.setPadding(Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9));
        Ui.clickable(prev, 20, R.color.surface, R.color.outline, v -> shiftMonth(-1));
        ImageView next = Ui.icon(this, R.drawable.ic_chevron, R.color.ink, 22);
        next.setPadding(Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9));
        Ui.clickable(next, 20, R.color.surface, R.color.outline, v -> shiftMonth(1));
        nav.addView(prev, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        TextView mt = Ui.text(this, "Tháng " + ScheduleRules.monthLabel(shiftMonth, shiftYear), 17, true, R.color.ink);
        mt.setGravity(Gravity.CENTER);
        nav.addView(mt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        nav.addView(next, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        content.addView(nav, Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 10));

        long from = monthStart(shiftYear, shiftMonth), to = nextMonthStart(shiftYear, shiftMonth);
        List<ShiftOccurrence> shifts = repo.shiftsBetween(from, to);
        long now = System.currentTimeMillis();

        if (shifts.isEmpty()) {
            empty("Chưa có lịch ca cho tháng này. Chọn file Excel/CSV để nhập cả tháng, hoặc thêm từng ca thủ công bên dưới.");
        } else {
            // Nhân sự các kíp trong tháng
            Map<String, String> rosters = new LinkedHashMap<>();
            for (String c : ScheduleDraft.CREWS) rosters.put(c, "");
            for (ShiftOccurrence s : shifts) if (rosters.containsKey(s.code) && rosters.get(s.code).isEmpty() && !s.roster.isEmpty()) rosters.put(s.code, s.roster);
            LinearLayout rc = Ui.card(this);
            LinearLayout rh = new LinearLayout(this);
            rh.setGravity(Gravity.CENTER_VERTICAL);
            rh.addView(Ui.icon(this, R.drawable.ic_group, R.color.primary, 20));
            TextView rt = Ui.text(this, "Nhân sự các kíp", 15, true, R.color.ink);
            rt.setPadding(Ui.dp(this, 8), 0, 0, 0);
            rh.addView(rt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView edit = Ui.text(this, "Sửa", 13, true, R.color.primary);
            edit.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
            Ui.clickable(edit, 10, R.color.primary_soft, 0, v -> editRosterDialog(rosters, from, to));
            rh.addView(edit);
            rc.addView(rh);
            for (Map.Entry<String, String> e : rosters.entrySet()) {
                if (e.getValue().isEmpty()) continue;
                LinearLayout line = new LinearLayout(this);
                line.setPadding(0, Ui.dp(this, 8), 0, 0);
                TextView badge = crewPill(e.getKey(), false);
                line.addView(badge, new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32)));
                TextView names = Ui.text(this, e.getValue(), 13.5f, false, R.color.ink);
                names.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), 0, 0);
                line.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                rc.addView(line);
            }
            Ui.addCard(content, rc);

            // Bảng ngày – ca
            section("Lịch từng ngày");
            LinearLayout table = Ui.card(this);
            table.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
            LinearLayout hd2 = new LinearLayout(this);
            hd2.addView(Ui.text(this, "Ngày", 11.5f, true, R.color.muted), new LinearLayout.LayoutParams(Ui.dp(this, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
            for (String sl : ScheduleDraft.SLOT_LABELS) {
                TextView h = Ui.text(this, sl, 11.5f, true, R.color.muted);
                h.setGravity(Gravity.CENTER);
                hd2.addView(h, Ui.margins(Ui.weight(1), this, 3, 0, 3, 0));
            }
            table.addView(hd2);
            Map<String, ShiftOccurrence[]> byDate = new LinkedHashMap<>();
            List<ShiftOccurrence> manual = new ArrayList<>();
            for (ShiftOccurrence s : shifts) {
                int slot = "MORNING".equals(s.slot) ? 0 : "AFTERNOON".equals(s.slot) ? 1 : "NIGHT".equals(s.slot) ? 2 : -1;
                if (slot < 0) { manual.add(s); continue; }
                ShiftOccurrence[] arr = byDate.get(s.shiftDate);
                if (arr == null) { arr = new ShiftOccurrence[3]; byDate.put(s.shiftDate, arr); }
                arr[slot] = s;
            }
            String todayIso = LocalDate.now(ScheduleRules.ZONE).toString();
            for (Map.Entry<String, ShiftOccurrence[]> e : byDate.entrySet()) {
                LocalDate d = LocalDate.parse(e.getKey());
                boolean today = e.getKey().equals(todayIso);
                String wd = ScheduleRules.weekdayLabel(d);
                LinearLayout line = new LinearLayout(this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
                if (today) line.setBackground(Ui.shape(this, R.color.primary_soft, 12, 0));
                LinearLayout dayBox = new LinearLayout(this);
                dayBox.setOrientation(LinearLayout.VERTICAL);
                dayBox.addView(Ui.text(this, String.valueOf(d.getDayOfMonth()), 15, true, today ? R.color.primary : R.color.ink));
                dayBox.addView(Ui.text(this, wd, 11, true, "CN".equals(wd) ? R.color.danger : R.color.muted));
                line.addView(dayBox, new LinearLayout.LayoutParams(Ui.dp(this, 60), ViewGroup.LayoutParams.WRAP_CONTENT));
                for (int s = 0; s < 3; s++) {
                    ShiftOccurrence o = e.getValue()[s];
                    line.addView(crewPill(o == null ? null : o.code, false), Ui.margins(Ui.weight(1), this, 3, 0, 3, 0));
                }
                table.addView(line);
            }
            Ui.addCard(content, table);
            content.addView(Ui.note(this, "Giờ ca theo cài đặt của bạn (nút “Cài giờ ca”); ca đêm kết thúc vào sáng hôm sau."));

            if (!manual.isEmpty()) {
                section("Ca nhập thủ công");
                for (ShiftOccurrence s : manual) {
                    LinearLayout c = Ui.card(this);
                    c.addView(row(Ui.chip(this, "Kíp " + s.code, crewColor(s.code), R.color.surface_alt)));
                    c.addView(Ui.text(this, fmt(s.startMillis) + " → " + fmt(s.endMillis), 13, false, R.color.muted));
                    if (!s.roster.isEmpty()) c.addView(Ui.text(this, s.roster, 13.5f, false, R.color.ink));
                    Ui.addCard(content, c);
                }
            }
        }
        section("Quản lý");
        buttonRow(Ui.button(this, "Thêm ca thủ công", Ui.BTN_OUTLINE, R.drawable.ic_add, v -> newShift()), Ui.button(this, "Xóa lịch tháng", Ui.BTN_DANGER, R.drawable.ic_delete, v -> deleteMonthDialog(from, to, shifts.size())));
        content.addView(Ui.note(this, "Công việc chỉ được tự gán ca khi có đúng một lần ca khớp thời điểm; nếu không có hoặc bị chồng lấn, phiên được lưu ở trạng thái chưa xác định ca. Xóa lịch không làm mất ca/nhân sự đã ghi trong các phiên công việc cũ."));
    }

    private void deleteMonthDialog(long from, long to, int count) {
        if (count == 0) { toast("Tháng này chưa có lịch ca"); return; }
        new AlertDialog.Builder(this).setTitle("Xóa lịch ca tháng " + ScheduleRules.monthLabel(shiftMonth, shiftYear) + "?").setMessage("Xóa " + count + " lần ca. Các phiên công việc đã ghi vẫn giữ nguyên ca và nhân sự tại thời điểm ghi.")
                .setNegativeButton("Hủy", null).setPositiveButton("Xóa lịch", (d, w) -> { int n = repo.deleteShiftsBetween(from, to); toast("Đã xóa " + n + " lần ca"); rerender(); }).show();
    }

    private void editRosterDialog(Map<String, String> rosters, long from, long to) {
        LinearLayout f = form();
        Map<String, EditText> fields = new LinkedHashMap<>();
        for (String c : ScheduleDraft.CREWS) fields.put(c, Ui.edit(this, f, "Kíp " + c + " (ngăn cách bằng dấu phẩy)", rosters.get(c), true));
        showForm("Sửa nhân sự các kíp", f, "Lưu", () -> {
            int total = 0;
            for (String c : ScheduleDraft.CREWS) {
                String nv = value(fields.get(c));
                if (!nv.equals(rosters.get(c))) total += repo.updateCrewRoster(c, nv, from, to);
            }
            toast("Đã cập nhật nhân sự cho " + total + " lần ca");
            rerender();
        });
    }

    private void newShift() {
        LinearLayout f = form();
        Calendar c = Calendar.getInstance(Ui.VN); c.set(Calendar.HOUR_OF_DAY, 7); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        long st = c.getTimeInMillis();
        Picker code = new Picker(this, f, "Kíp", ScheduleDraft.CREWS, 0);
        DateTimeField start = new DateTimeField(this, f, "Giờ bắt đầu", st, true);
        DateTimeField end = new DateTimeField(this, f, "Giờ kết thúc", st + 8 * 3_600_000L, true);
        EditText roster = Ui.edit(this, f, "Danh sách nhân sự trực", "", true);
        EditText note = Ui.edit(this, f, "Ghi chú", "", false);
        showForm("Thêm lần ca trực", f, "Lưu", () -> {
            long en = end.millis;
            if (en <= start.millis) en += 86_400_000L;
            HelpDeskRepository.ShiftInput x = new HelpDeskRepository.ShiftInput();
            x.date = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(start.millis), ScheduleRules.ZONE).toLocalDate().toString();
            x.code = code.value(); x.startAt = start.millis; x.endAt = en; x.roster = value(roster); x.note = value(note); x.source = "manual";
            repo.addShift(x);
            toast("Đã lưu lần ca trực");
            rerender();
        });
    }

    private void showBusy(String message, Runnable onCancel) {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 8));
        l.addView(new ProgressBar(this), new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        TextView t = Ui.text(this, message, 14, false, R.color.ink);
        t.setPadding(Ui.dp(this, 16), 0, 0, 0);
        l.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        dismissBusy();
        busyDialog = new AlertDialog.Builder(this).setView(l).setCancelable(false).setNegativeButton("Hủy", (d, w) -> onCancel.run()).create();
        busyDialog.show();
    }
    private void dismissBusy() { if (busyDialog != null) { try { busyDialog.dismiss(); } catch (RuntimeException ignored) { } busyDialog = null; } }

    private void showSchedulePreview() {
        final ScheduleDraft d = draft;
        if (d == null) { stack.clear(); rerender(); return; }
        List<String> issues = ScheduleRules.validate(d);
        int filled = 0, total = d.days.length * 3;
        for (ScheduleDraft.Day day : d.days) for (String c : day.crew) if (c != null) filled++;

        // Tóm tắt + tháng/năm
        LinearLayout sum = Ui.card(this);
        sum.addView(Ui.text(this, "Kết quả đọc từ tệp", 17, true, R.color.ink));
        sum.addView(Ui.text(this, "Đã đọc " + filled + "/" + total + " ô ca của " + d.days.length + " ngày. Kiểm tra lại, chạm vào ô để sửa trước khi lưu.", 13, false, R.color.muted));
        LinearLayout pickers = new LinearLayout(this);
        pickers.setPadding(0, Ui.dp(this, 12), 0, 0);
        String[] months = new String[12];
        for (int i = 0; i < 12; i++) months[i] = "Tháng " + (i + 1);
        int curY = LocalDate.now(ScheduleRules.ZONE).getYear();
        int y0 = Math.min(d.year, curY - 1), y1 = Math.max(d.year, curY + 1);
        String[] years = new String[y1 - y0 + 1];
        for (int i = 0; i < years.length; i++) years[i] = String.valueOf(y0 + i);
        LinearLayout pm = new LinearLayout(this); pm.setOrientation(LinearLayout.VERTICAL);
        LinearLayout py = new LinearLayout(this); py.setOrientation(LinearLayout.VERTICAL);
        Picker monthPick = new Picker(this, pm, "Tháng", months, d.month - 1);
        Picker yearPick = new Picker(this, py, "Năm", years, d.year - y0);
        Runnable onDate = () -> {
            d.month = monthPick.index + 1; d.year = y0 + yearPick.index;
            d.resizeDays(YearMonth.of(d.year, d.month).lengthOfMonth());
            rerender();
        };
        monthPick.onChange = onDate; yearPick.onChange = onDate;
        pickers.addView(pm, Ui.margins(Ui.weight(1), this, 0, 0, 8, 0));
        pickers.addView(py, Ui.weight(1));
        sum.addView(pickers);
        Ui.addCard(content, sum);
        if (!d.notes.isEmpty()) {
            LinearLayout nc = Ui.card(this);
            nc.setBackground(Ui.shape(this, R.color.info_soft, 16, 0));
            for (String note : d.notes) { TextView t = Ui.text(this, "ℹ " + note, 13, false, R.color.ink); t.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2)); nc.addView(t); }
            Ui.addCard(content, nc);
        }

        // Cảnh báo / xác nhận khớp
        LinearLayout vc = Ui.card(this);
        if (issues.isEmpty()) {
            vc.setBackground(Ui.shape(this, R.color.success_soft, 16, 0));
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.addView(Ui.icon(this, R.drawable.ic_check, R.color.success, 22));
            TextView t = Ui.text(this, "Khớp lịch tháng " + ScheduleRules.monthLabel(d.month, d.year) + ": các thứ trong tuần, mã kíp và nhân sự đều hợp lệ.", 13.5f, true, R.color.success);
            t.setPadding(Ui.dp(this, 10), 0, 0, 0);
            r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            vc.addView(r);
        } else {
            vc.setBackground(Ui.shape(this, R.color.warning_soft, 16, 0));
            vc.addView(Ui.text(this, issues.size() + " điểm cần kiểm tra", 15, true, R.color.warning));
            int shown = Math.min(issues.size(), 8);
            for (int i = 0; i < shown; i++) { TextView t = Ui.text(this, "• " + issues.get(i), 13, false, R.color.ink); t.setPadding(0, Ui.dp(this, 3), 0, 0); vc.addView(t); }
            if (issues.size() > shown) vc.addView(Ui.text(this, "… và " + (issues.size() - shown) + " mục khác (các ô lỗi được tô viền đỏ bên dưới).", 12.5f, false, R.color.muted));
        }
        Ui.addCard(content, vc);

        // Giờ ca
        LinearLayout tc = Ui.card(this);
        LinearLayout th = new LinearLayout(this);
        th.setGravity(Gravity.CENTER_VERTICAL);
        th.addView(Ui.icon(this, R.drawable.ic_clock, R.color.primary, 20));
        TextView tt = Ui.text(this, "Giờ đi ca", 15, true, R.color.ink);
        tt.setPadding(Ui.dp(this, 8), 0, 0, 0);
        th.addView(tt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView te = Ui.text(this, "Sửa", 13, true, R.color.primary);
        te.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        Ui.clickable(te, 10, R.color.primary_soft, 0, v -> editSlotTimes(d));
        th.addView(te);
        tc.addView(th);
        for (int s = 0; s < 3; s++) {
            TextView t = Ui.text(this, ScheduleDraft.SLOT_LABELS[s] + ": " + d.slotStart[s] + " → " + d.slotEnd[s] + (s == 2 ? " (hôm sau)" : ""), 13.5f, false, R.color.ink);
            t.setPadding(0, Ui.dp(this, 5), 0, 0);
            tc.addView(t);
        }
        Ui.addCard(content, tc);

        // Nhân sự
        LinearLayout rc = Ui.card(this);
        rc.addView(Ui.text(this, "Nhân sự các kíp", 15, true, R.color.ink));
        rc.addView(Ui.note(this, "Ngăn cách các tên bằng dấu phẩy. Sửa trực tiếp nếu trong tệp có lỗi chính tả."));
        for (String c : ScheduleDraft.CREWS) {
            EditText e = Ui.edit(this, rc, "Kíp " + c, d.rosters.get(c), true);
            e.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence a, int b, int cc, int dd) {}
                public void onTextChanged(CharSequence a, int b, int cc, int dd) {}
                public void afterTextChanged(Editable ed) { d.rosters.put(c, ed.toString().trim()); }
            });
        }
        Ui.addCard(content, rc);

        // Bảng ngày
        section("Lịch từng ngày · chạm ô để sửa");
        LinearLayout table = Ui.card(this);
        table.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        LinearLayout hd = new LinearLayout(this);
        hd.addView(Ui.text(this, "Ngày", 11.5f, true, R.color.muted), new LinearLayout.LayoutParams(Ui.dp(this, 76), ViewGroup.LayoutParams.WRAP_CONTENT));
        for (String sl : ScheduleDraft.SLOT_LABELS) {
            TextView h = Ui.text(this, sl, 11.5f, true, R.color.muted);
            h.setGravity(Gravity.CENTER);
            hd.addView(h, Ui.margins(Ui.weight(1), this, 3, 0, 3, 0));
        }
        table.addView(hd);
        for (ScheduleDraft.Day day : d.days) {
            boolean bad = ScheduleRules.dayHasIssue(d, day);
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
            if (bad) line.setBackground(Ui.shape(this, R.color.danger_soft, 12, 0));
            LinearLayout dayBox = new LinearLayout(this);
            dayBox.setOrientation(LinearLayout.VERTICAL);
            dayBox.addView(Ui.text(this, String.valueOf(day.day), 15, true, R.color.ink));
            String real = "";
            try { real = ScheduleRules.weekdayLabel(LocalDate.of(d.year, d.month, day.day)); } catch (RuntimeException ignored) { real = "?"; }
            boolean mismatch = day.weekday != null && !day.weekday.equals(real);
            dayBox.addView(Ui.text(this, real + (mismatch ? " (tệp: " + day.weekday + ")" : ""), 10.5f, true, mismatch ? R.color.danger : "CN".equals(real) ? R.color.danger : R.color.muted));
            line.addView(dayBox, new LinearLayout.LayoutParams(Ui.dp(this, 72), ViewGroup.LayoutParams.WRAP_CONTENT));
            for (int s = 0; s < 3; s++) {
                final int slot = s;
                TextView pill = crewPill(day.crew[s], bad && day.crew[s] == null);
                pill.setOnClickListener(v -> pickCrew(d, day, slot));
                line.addView(pill, Ui.margins(Ui.weight(1), this, 3, 0, 3, 0));
            }
            table.addView(line);
        }
        Ui.addCard(content, table);

        String label = ScheduleRules.monthLabel(d.month, d.year);
        buttonRow(Ui.button(this, "Hủy bỏ", Ui.BTN_OUTLINE, R.drawable.ic_close, v -> { draft = null; pop(); }), Ui.button(this, "Lưu lịch " + label, Ui.BTN_PRIMARY, R.drawable.ic_check, v -> saveSchedule(d)));
    }

    private void pickCrew(ScheduleDraft d, ScheduleDraft.Day day, int slot) {
        String[] items = {"A", "B", "C", "D", "E", "Để trống"};
        int sel = items.length - 1;
        for (int i = 0; i < 5; i++) if (items[i].equals(day.crew[slot])) sel = i;
        new AlertDialog.Builder(this).setTitle("Ngày " + day.day + " · ca " + ScheduleDraft.SLOT_LABELS[slot]).setSingleChoiceItems(items, sel, (dlg, which) -> {
            day.crew[slot] = which < 5 ? items[which] : null;
            dlg.dismiss();
            rerender();
        }).setNegativeButton("Hủy", null).show();
    }

    private void editSlotTimes(ScheduleDraft d) {
        LinearLayout f = form();
        f.addView(Ui.note(this, "Định dạng HH:mm-HH:mm, ví dụ 07:00-15:00. Ca có giờ kết thúc nhỏ hơn giờ bắt đầu được hiểu là kết thúc vào ngày hôm sau."));
        EditText[] e = new EditText[3];
        for (int s = 0; s < 3; s++) e[s] = Ui.edit(this, f, "Ca " + ScheduleDraft.SLOT_LABELS[s].toLowerCase(Locale.ROOT), d.slotStart[s] + "-" + d.slotEnd[s], false);
        showForm("Giờ đi ca", f, "Lưu", () -> {
            String[] st = new String[3], en = new String[3];
            for (int s = 0; s < 3; s++) {
                String[] p = value(e[s]).replace(" ", "").split("-");
                if (p.length != 2 || ScheduleRules.parseTime(p[0]) == null || ScheduleRules.parseTime(p[1]) == null) throw new Exception("Giờ ca " + ScheduleDraft.SLOT_LABELS[s] + " không hợp lệ (cần HH:mm-HH:mm)");
                st[s] = ScheduleRules.parseTime(p[0]).toString(); en[s] = ScheduleRules.parseTime(p[1]).toString();
            }
            for (int s = 0; s < 3; s++) { d.slotStart[s] = st[s]; d.slotEnd[s] = en[s]; }
            rerender();
        });
    }

    private void saveSchedule(ScheduleDraft d) {
        final List<ScheduleRules.Occurrence> items;
        try { items = ScheduleRules.expand(d); } catch (Exception e) { error(e); return; }
        if (items.isEmpty()) { toast("Chưa có ô ca nào hợp lệ để lưu"); return; }
        long from = Long.MAX_VALUE, to = Long.MIN_VALUE;
        for (ScheduleRules.Occurrence o : items) { from = Math.min(from, o.startMillis); to = Math.max(to, o.endMillis); }
        int overlap = repo.countShiftsOverlapping(from, to);
        int issues = ScheduleRules.validate(d).size();
        String label = ScheduleRules.monthLabel(d.month, d.year);
        StringBuilder m = new StringBuilder("Lưu " + items.size() + " lần ca của tháng " + label + ".");
        if (overlap > 0) m.append("\n\nĐã có ").append(overlap).append(" lần ca trong khoảng thời gian này: ca trùng ngày/ca sẽ được cập nhật, các ca khác chồng lấn sẽ được thay thế. Phiên công việc cũ vẫn giữ nguyên ca đã ghi.");
        if (issues > 0) m.append("\n\nCòn ").append(issues).append(" điểm cần kiểm tra; ô để trống sẽ không tạo ca.");
        new AlertDialog.Builder(this).setTitle("Lưu lịch ca?").setMessage(m.toString()).setNegativeButton("Xem lại", null).setPositiveButton("Lưu", (dlg, w) -> {
            try {
                int[] r = repo.replaceMonthSchedule(items, "Nhập từ tệp lịch ca " + label);
                shiftYear = d.year; shiftMonth = d.month; draft = null;
                toast("Đã lưu lịch ca: " + r[0] + " mới, " + r[1] + " cập nhật, " + r[2] + " thay thế");
                stack.clear(); push("shifts", null);
            } catch (Exception e) { error(e); }
        }).show();
    }

    // ======================================================================
    // Nhập lịch ca từ file Excel/CSV (offline), cài giờ ca
    // ======================================================================
    private static final String[] SHIFT_KEYS = {"shift_morning", "shift_afternoon", "shift_night"};
    private static final String[] SHIFT_DEFAULTS = {"07:00-15:00", "15:00-23:00", "23:00-07:00"};

    /** Giờ ca đã cài: {mảng giờ bắt đầu, mảng giờ kết thúc} theo thứ tự Sáng, Chiều, Đêm (HH:mm). */
    private String[][] shiftTimes() {
        String[] st = new String[3], en = new String[3];
        for (int i = 0; i < 3; i++) {
            String[] p = repo.getSetting(SHIFT_KEYS[i], SHIFT_DEFAULTS[i]).split("-");
            java.time.LocalTime a = p.length == 2 ? ScheduleRules.parseTime(p[0]) : null, b = p.length == 2 ? ScheduleRules.parseTime(p[1]) : null;
            if (a == null || b == null) { p = SHIFT_DEFAULTS[i].split("-"); a = ScheduleRules.parseTime(p[0]); b = ScheduleRules.parseTime(p[1]); }
            st[i] = a.toString(); en[i] = b.toString();
        }
        return new String[][]{st, en};
    }

    private void shiftTimeDialog() {
        final String[][] cur = shiftTimes();
        LinearLayout f = form();
        f.addView(Ui.note(this, "Đặt giờ bắt đầu và kết thúc cho từng ca. Giờ này dùng khi nhập lịch từ file và khi tự gán ca cho công việc. Nếu giờ kết thúc nhỏ hơn giờ bắt đầu (ví dụ ca đêm 23:00 → 07:00) thì ca kết thúc vào ngày hôm sau."));
        Ui.TimeField[] from = new Ui.TimeField[3], to = new Ui.TimeField[3];
        for (int s = 0; s < 3; s++) {
            f.addView(Ui.text(this, "Ca " + ScheduleDraft.SLOT_LABELS[s].toLowerCase(Locale.ROOT), 14, true, R.color.ink));
            LinearLayout line = new LinearLayout(this);
            line.setPadding(0, Ui.dp(this, 6), 0, 0);
            LinearLayout l1 = new LinearLayout(this); l1.setOrientation(LinearLayout.VERTICAL);
            LinearLayout l2 = new LinearLayout(this); l2.setOrientation(LinearLayout.VERTICAL);
            from[s] = new Ui.TimeField(this, l1, "Từ", cur[0][s]);
            to[s] = new Ui.TimeField(this, l2, "Đến", cur[1][s]);
            line.addView(l1, Ui.margins(Ui.weight(1), this, 0, 0, 6, 0));
            line.addView(l2, Ui.weight(1));
            f.addView(line);
        }
        final android.widget.CheckBox apply = new android.widget.CheckBox(this);
        apply.setText("Áp dụng luôn cho lịch tháng " + ScheduleRules.monthLabel(shiftMonth, shiftYear) + " đang xem");
        apply.setTextColor(Ui.color(this, R.color.ink));
        apply.setChecked(true);
        f.addView(apply);
        showForm("Cài giờ ca", f, "Lưu", () -> {
            String[] st = new String[3], en = new String[3];
            for (int s = 0; s < 3; s++) {
                st[s] = from[s].value; en[s] = to[s].value;
                if (st[s].equals(en[s])) throw new Exception("Ca " + ScheduleDraft.SLOT_LABELS[s].toLowerCase(Locale.ROOT) + ": giờ bắt đầu và kết thúc không được trùng nhau");
            }
            // Ba ca phải liền mạch để mỗi thời điểm chỉ thuộc một ca.
            if (!st[1].equals(en[0]) || !st[2].equals(en[1]) || !st[0].equals(en[2])) throw new Exception("Các ca cần nối liền nhau: giờ kết thúc ca sáng = giờ bắt đầu ca chiều, kết thúc ca chiều = bắt đầu ca đêm, kết thúc ca đêm = bắt đầu ca sáng.");
            for (int s = 0; s < 3; s++) repo.setSetting(SHIFT_KEYS[s], st[s] + "-" + en[s]);
            int changed = 0;
            if (apply.isChecked()) changed = repo.retimeSlots(monthStart(shiftYear, shiftMonth), nextMonthStart(shiftYear, shiftMonth), st, en);
            toast("Đã lưu giờ ca" + (changed > 0 ? " và cập nhật " + changed + " lần ca" : ""));
            rerender();
        });
    }

    private void startScheduleImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values", "application/csv", "text/plain", "application/octet-stream"});
        startActivityForResult(i, REQ_SCHEDULE_FILE);
    }

    private void readScheduleFile(Uri uri) {
        final int job = ++scheduleJob;
        showBusy("Đang đọc file lịch ca…", () -> scheduleJob++);
        final int fy = shiftYear, fm = shiftMonth;
        final String[][] tm = shiftTimes();
        io.execute(() -> {
            try {
                byte[] data;
                try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new Exception("Không mở được tệp đã chọn");
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[16384]; int n; long total = 0;
                    while ((n = in.read(buf)) != -1) { total += n; if (total > 25L * 1024 * 1024) throw new Exception("Tệp quá lớn (tối đa 25 MB)"); out.write(buf, 0, n); }
                    data = out.toByteArray();
                }
                ScheduleDraft parsed;
                if (data.length > 3 && data[0] == 'P' && data[1] == 'K') {
                    parsed = ScheduleSheetParser.parseSheets(XlsxReader.read(new java.io.ByteArrayInputStream(data)), fy, fm, tm[0], tm[1]);
                } else {
                    parsed = ScheduleSheetParser.parse(CsvReader.parse(new String(data, java.nio.charset.StandardCharsets.UTF_8)), fy, fm, tm[0], tm[1]);
                }
                main.post(() -> {
                    if (isDestroyed() || job != scheduleJob) return;
                    dismissBusy();
                    draft = parsed;
                    if (!"schedule_preview".equals(current().name)) push("schedule_preview", null); else rerender();
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (isDestroyed() || job != scheduleJob) return;
                    dismissBusy();
                    new AlertDialog.Builder(this).setTitle("Chưa đọc được lịch ca").setMessage(msg(e) + "\n\nMẹo: dùng file .xlsx hoặc .csv giống file mẫu. Bấm “Tải file mẫu” trong màn hình Lịch ca để lấy mẫu.")
                            .setNegativeButton("Đóng", null).setPositiveButton("Chọn file khác", (d, w) -> startScheduleImport()).show();
                });
            }
        });
    }

    private void saveSampleTemplate() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        i.putExtra(Intent.EXTRA_TITLE, "Mau_Lich_Truc_Ca.xlsx");
        startActivityForResult(i, REQ_SAMPLE);
    }

    private void writeSample(Uri uri) {
        try (java.io.InputStream in = getAssets().open("Mau_Lich_Truc_Ca.xlsx"); java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new Exception("Không ghi được vào vị trí đã chọn");
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            toast("Đã lưu file mẫu. Mở bằng Excel, sửa lịch rồi nhập lại vào ứng dụng.");
        } catch (Exception e) { error(e); }
    }

    // ======================================================================
    // Thống kê bất thường (offline): tồn tại, phát sinh mới, đã xử lý, loại hay xảy ra
    // ======================================================================
    private static final String[] STATS_LABELS = {"7 ngày", "30 ngày", "Tháng này", "Tháng trước", "Quý này", "Năm nay", "Tùy chọn"};
    private static final int STATS_CUSTOM = 6;
    private int statsPreset = 2, statsGroup = 0;
    private long statsFrom = 0, statsTo = 0; // khoảng tùy chọn: [from, to) theo ngày
    private int openShown = 5, newShown = 5, resolvedShown = 5;
    private final SimpleDateFormat dayFmt = new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));

    /** Khoảng thời gian đang chọn, nửa mở [from, to). */
    private long[] statsRange(long now) {
        dayFmt.setTimeZone(Ui.VN);
        java.time.ZoneId z = ScheduleRules.ZONE;
        LocalDate today = java.time.Instant.ofEpochMilli(now).atZone(z).toLocalDate();
        switch (statsPreset) {
            case 0: return AnalysisRange.resolve(AnalysisRange.LAST7, now, z);
            case 1: return AnalysisRange.resolve(AnalysisRange.LAST30, now, z);
            case 2: return AnalysisRange.resolve(AnalysisRange.THIS_MONTH, now, z);
            case 3: return AnalysisRange.resolve(AnalysisRange.LAST_MONTH, now, z);
            case 4: {
                LocalDate q = LocalDate.of(today.getYear(), ((today.getMonthValue() - 1) / 3) * 3 + 1, 1);
                return new long[]{q.atStartOfDay(z).toInstant().toEpochMilli(), q.plusMonths(3).atStartOfDay(z).toInstant().toEpochMilli()};
            }
            case 5: return AnalysisRange.resolve(AnalysisRange.THIS_YEAR, now, z);
            default:
                if (statsFrom <= 0 || statsTo <= statsFrom) { long[] d = AnalysisRange.resolve(AnalysisRange.LAST30, now, z); statsFrom = d[0]; statsTo = d[1]; }
                return new long[]{statsFrom, statsTo};
        }
    }

    private void pickStatsDate(boolean isFrom, long now) {
        long[] rg = statsRange(now);
        Calendar cal = Calendar.getInstance(Ui.VN);
        cal.setTimeInMillis(isFrom ? rg[0] : rg[1] - 1);
        new android.app.DatePickerDialog(this, (dp, y, m, d) -> {
            Calendar c = Calendar.getInstance(Ui.VN);
            c.clear(); c.set(y, m, d, 0, 0, 0);
            long dayStart = c.getTimeInMillis();
            c.add(Calendar.DAY_OF_MONTH, 1);
            long dayEnd = c.getTimeInMillis();
            long from = rg[0], to = rg[1];
            if (isFrom) { from = dayStart; if (to <= from) to = dayEnd; }
            else { to = dayEnd; if (from >= to) from = dayStart; }
            statsFrom = from; statsTo = to; statsPreset = STATS_CUSTOM;
            openShown = newShown = resolvedShown = 5;
            rerender();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private String durationText(long ms) {
        if (ms < 0) return "—";
        long d = ms / 86_400_000L, h = (ms % 86_400_000L) / 3_600_000L, m = (ms % 3_600_000L) / 60_000L;
        if (d > 0) return d + " ngày" + (h > 0 ? " " + h + " giờ" : "");
        if (h > 0) return h + " giờ" + (m > 0 ? " " + m + " phút" : "");
        return Math.max(1, m) + " phút";
    }

    private void showStats() {
        final long now = System.currentTimeMillis();
        final long[] rg = statsRange(now);
        final vn.evnqt1.helpdesk.core.StatsReport s = repo.stats(rg[0], rg[1], now);

        // ---- chọn khoảng thời gian
        LinearLayout range = Ui.card(this);
        range.addView(Ui.text(this, "KHOẢNG THỜI GIAN", 11, true, R.color.muted));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        for (int i = 0; i < STATS_LABELS.length; i++) {
            final int idx = i;
            boolean on = i == statsPreset;
            TextView c = Ui.text(this, STATS_LABELS[i], 13, true, on ? R.color.on_brand : R.color.ink);
            c.setGravity(Gravity.CENTER);
            c.setMinHeight(Ui.dp(this, 40));
            c.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
            Ui.clickable(c, 20, on ? R.color.brand : R.color.surface_alt, on ? 0 : R.color.outline, v -> { statsPreset = idx; openShown = newShown = resolvedShown = 5; rerender(); });
            chips.addView(c, Ui.margins(Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), this, 0, 8, 8, 0));
        }
        hs.addView(chips);
        range.addView(hs);
        LinearLayout dates = new LinearLayout(this);
        dates.addView(dateButton("TỪ NGÀY", rg[0], v -> pickStatsDate(true, now)), Ui.margins(Ui.weight(1), this, 0, 12, 8, 0));
        dates.addView(dateButton("ĐẾN NGÀY", rg[1] - 1, v -> pickStatsDate(false, now)), Ui.margins(Ui.weight(1), this, 0, 12, 0, 0));
        range.addView(dates);
        long days = Math.max(1, Math.round((rg[1] - rg[0]) / 86_400_000.0));
        TextView sum = Ui.text(this, "Kỳ phân tích: " + days + " ngày · so sánh với " + days + " ngày liền trước", 12, false, R.color.muted);
        sum.setPadding(0, Ui.dp(this, 10), 0, 0);
        range.addView(sum);
        Ui.addCard(content, range);

        // ---- 4 chỉ số chính
        section("Tổng quan");
        int net = s.net();
        tileRow(
                kpiTile("Đang tồn tại", String.valueOf(s.openTotal), s.openUnprocessed + " chưa xử lý · " + s.openInProgress + " đang xử lý", R.color.muted, R.drawable.ic_warning, R.color.warning, R.color.warning_soft, () -> { anomalyFilter = "Đang tồn tại"; openTab("Bất thường"); }),
                kpiTile("Phát sinh mới", String.valueOf(s.newList.size()), deltaText(s.newList.size(), s.prevNew), deltaColor(s.newList.size() - s.prevNew, false), R.drawable.ic_add, R.color.info, R.color.info_soft, null));
        tileRow(
                kpiTile("Đã xử lý", String.valueOf(s.resolvedList.size()), deltaText(s.resolvedList.size(), s.prevResolved), deltaColor(s.resolvedList.size() - s.prevResolved, true), R.drawable.ic_check, R.color.success, R.color.success_soft, null),
                kpiTile("Tồn đọng kỳ này", (net > 0 ? "+" : "") + net, net > 0 ? "Phát sinh nhiều hơn xử lý" : net < 0 ? "Xử lý nhiều hơn phát sinh" : "Phát sinh bằng xử lý", net > 0 ? R.color.danger : net < 0 ? R.color.success : R.color.muted, R.drawable.ic_stats, net > 0 ? R.color.danger : R.color.primary, net > 0 ? R.color.danger_soft : R.color.primary_soft, null));
        LinearLayout facts = Ui.card(this);
        facts.addView(factRow(R.drawable.ic_clock, "Thời gian xử lý trung bình", s.avgResolveMs < 0 ? "Chưa có bất thường đóng trong kỳ" : durationText(s.avgResolveMs) + " (từ lúc phát hiện đến khi đóng)"));
        facts.addView(factRow(R.drawable.ic_calendar, "Tuổi trung bình của bất thường đang tồn tại", s.openTotal == 0 ? "Không có bất thường tồn đọng" : durationText(s.avgOpenAgeMs)));
        facts.addView(factRow(R.drawable.ic_shield, "Phát sinh trong kỳ còn chưa xử lý xong", s.newList.isEmpty() ? "—" : s.newStillOpen + " / " + s.newList.size() + " bất thường"));
        if (s.reopenedInRange > 0) facts.addView(factRow(R.drawable.ic_info, "Từng được mở lại trước khi đóng", s.reopenedInRange + " bất thường"));
        Ui.addCard(content, facts);

        // ---- xu hướng
        section("Xu hướng theo " + s.buckets.unitLabel());
        LinearLayout trend = Ui.card(this);
        if (s.newList.isEmpty() && s.resolvedList.isEmpty()) {
            TextView none = Ui.text(this, "Không có bất thường nào phát sinh hoặc được xử lý trong kỳ này.", 13.5f, false, R.color.muted);
            none.setGravity(Gravity.CENTER);
            none.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 16));
            trend.addView(none);
        } else {
            trend.addView(row(legendDot(R.color.info, "Phát sinh mới"), legendDot(R.color.success, "Đã xử lý")));
            final vn.evnqt1.helpdesk.ui.BarChartView chart = new vn.evnqt1.helpdesk.ui.BarChartView(this);
            chart.setData(s.buckets.labels, s.newPerBucket, s.resolvedPerBucket, Ui.color(this, R.color.info), Ui.color(this, R.color.success));
            final TextView caption = Ui.text(this, "Chạm vào một cột để xem số liệu chi tiết.", 12.5f, false, R.color.muted);
            caption.setPadding(0, Ui.dp(this, 6), 0, 0);
            chart.setOnSelect(i -> caption.setText(i < 0 ? "Chạm vào một cột để xem số liệu chi tiết." : (s.buckets.unit == vn.evnqt1.helpdesk.core.StatsBuckets.DAY ? "Ngày " : s.buckets.unit == vn.evnqt1.helpdesk.core.StatsBuckets.WEEK ? "Tuần từ " : s.buckets.unit == vn.evnqt1.helpdesk.core.StatsBuckets.MONTH ? "Tháng " : "Quý từ ") + s.buckets.labels[i] + ": " + s.newPerBucket[i] + " phát sinh mới · " + s.resolvedPerBucket[i] + " đã xử lý"));
            trend.addView(chart, Ui.margins(Ui.matchWrap(), this, 0, 8, 0, 0));
            trend.addView(caption);
        }
        Ui.addCard(content, trend);

        // ---- đang tồn tại
        section("Đang tồn tại (" + s.openTotal + ")");
        LinearLayout open = Ui.card(this);
        if (s.openTotal == 0) {
            TextView none = Ui.text(this, "Không có bất thường nào đang tồn đọng.", 13.5f, false, R.color.muted);
            none.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            open.addView(none);
        } else {
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            int[] sevRes = {R.color.danger, R.color.warning, R.color.info, R.color.muted};
            int[] sevColors = new int[4];
            for (int i = 0; i < 4; i++) sevColors[i] = Ui.color(this, sevRes[i]);
            vn.evnqt1.helpdesk.ui.DonutView donut = new vn.evnqt1.helpdesk.ui.DonutView(this);
            donut.setData(s.openBySeverity, sevColors, String.valueOf(s.openTotal), "đang tồn tại");
            top.addView(donut);
            LinearLayout legend = new LinearLayout(this);
            legend.setOrientation(LinearLayout.VERTICAL);
            legend.setPadding(Ui.dp(this, 16), 0, 0, 0);
            for (int i = 0; i < 4; i++) {
                LinearLayout line = new LinearLayout(this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
                View dot = new View(this);
                dot.setBackground(Ui.shape(this, sevRes[i], 5, 0));
                line.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 10), Ui.dp(this, 10)));
                TextView nm = Ui.text(this, vn.evnqt1.helpdesk.core.StatsReport.SEVERITIES[i], 13.5f, false, R.color.ink);
                nm.setPadding(Ui.dp(this, 8), 0, 0, 0);
                line.addView(nm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                line.addView(Ui.text(this, String.valueOf(s.openBySeverity[i]), 14.5f, true, R.color.ink));
                legend.addView(line);
            }
            top.addView(legend, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            open.addView(top);
        }
        Ui.addCard(content, open);
        statList(s.openList, 0, "Danh sách đang tồn tại — cũ nhất trước", "Không có bất thường nào đang tồn đọng.");

        // ---- phát sinh mới
        section("Phát sinh mới trong kỳ (" + s.newList.size() + ")");
        statList(s.newList, 1, null, "Không có bất thường nào được phát hiện trong kỳ này.");

        // ---- đã xử lý
        section("Đã xử lý trong kỳ (" + s.resolvedList.size() + ")");
        statList(s.resolvedList, 2, null, "Chưa có bất thường nào được đóng trong kỳ này.");

        // ---- hay xảy ra nhất
        section("Hay xảy ra nhất");
        LinearLayout groups = Ui.card(this);
        LinearLayout seg = new LinearLayout(this);
        String[] segLabels = {"Loại", "Thiết bị", "Hệ thống"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            boolean on = i == statsGroup;
            TextView c = Ui.text(this, segLabels[i], 13, true, on ? R.color.on_brand : R.color.ink);
            c.setGravity(Gravity.CENTER);
            c.setMinHeight(Ui.dp(this, 40));
            Ui.clickable(c, 12, on ? R.color.brand : R.color.surface_alt, on ? 0 : R.color.outline, v -> { statsGroup = idx; rerender(); });
            seg.addView(c, Ui.margins(Ui.weight(1), this, 0, 0, i == 2 ? 0 : 8, 0));
        }
        groups.addView(seg);
        List<vn.evnqt1.helpdesk.core.StatsReport.Group> list = statsGroup == 0 ? s.byCategory : statsGroup == 1 ? s.byEquipment : s.bySystem;
        if (list.isEmpty()) {
            TextView none = Ui.text(this, "Chưa có bất thường phát sinh trong kỳ để thống kê.", 13.5f, false, R.color.muted);
            none.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
            groups.addView(none);
        } else {
            int shown = Math.min(8, list.size()), max = list.get(0).count;
            for (int i = 0; i < shown; i++) groups.addView(groupRow(i + 1, list.get(i), max, s.newList.size()));
            if (list.size() > shown) groups.addView(Ui.note(this, "… và " + (list.size() - shown) + " nhóm ít xảy ra hơn."));
        }
        Ui.addCard(content, groups);
        content.addView(Ui.note(this, statsGroup == 0
                ? "“Loại” được phân loại tự động theo từ khóa trong tiêu đề/mô tả (rò rỉ, rung, nhiệt độ, điện…), chỉ mang tính tham khảo. Chạm vào một dòng để xem các bất thường trong nhóm."
                : "Chỉ tính bất thường phát sinh trong kỳ đã chọn; thiết bị/hệ thống gộp không phân biệt hoa thường và dấu. Chạm vào một dòng để xem danh sách."));
    }

    private View dateButton(String label, long millis, View.OnClickListener l) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        b.addView(Ui.text(this, label, 10.5f, true, R.color.muted));
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        dayFmt.setTimeZone(Ui.VN);
        TextView d = Ui.text(this, dayFmt.format(new Date(millis)), 15, true, R.color.ink);
        line.addView(d, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        line.addView(Ui.icon(this, R.drawable.ic_calendar, R.color.primary, 18));
        b.addView(line);
        Ui.clickable(b, 12, R.color.surface_alt, R.color.outline, l);
        return b;
    }

    private String deltaText(int cur, int prev) {
        int d = cur - prev;
        return d == 0 ? "Bằng kỳ trước (" + prev + ")" : (d > 0 ? "▲ +" : "▼ ") + d + " so với kỳ trước (" + prev + ")";
    }

    /** Màu mô tả xu hướng: tăng số bất thường mới là xấu, tăng số xử lý là tốt. */
    private int deltaColor(int diff, boolean upIsGood) {
        if (diff == 0) return R.color.muted;
        return (diff > 0) == upIsGood ? R.color.success : R.color.danger;
    }

    private LinearLayout kpiTile(String label, String value, String sub, int subColor, int icon, int fg, int soft, Runnable action) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout disc = new LinearLayout(this);
        disc.setGravity(Gravity.CENTER);
        disc.setBackground(Ui.shape(this, R.color.surface, 10, 0));
        disc.addView(Ui.icon(this, icon, fg, 16));
        top.addView(disc, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)));
        TextView lb = Ui.text(this, label, 12.5f, true, fg);
        lb.setPadding(Ui.dp(this, 8), 0, 0, 0);
        lb.setMaxLines(2);
        top.addView(lb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        t.addView(top);
        TextView v = Ui.text(this, value, 32, true, R.color.ink);
        v.setPadding(0, Ui.dp(this, 6), 0, 0);
        t.addView(v);
        TextView sb = Ui.text(this, sub, 11.5f, false, subColor);
        sb.setMinLines(2);
        sb.setMaxLines(2);
        t.addView(sb);
        if (action != null) Ui.clickable(t, 18, soft, 0, x -> action.run());
        else t.setBackground(Ui.shape(this, soft, 18, 0));
        return t;
    }

    /** Hai thẻ cùng một hàng, luôn cao bằng nhau. */
    private void tileRow(View a, View b) {
        LinearLayout l = new LinearLayout(this);
        LinearLayout.LayoutParams pa = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1), pb = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        pa.setMargins(0, 0, Ui.dp(this, 5), 0);
        pb.setMargins(Ui.dp(this, 5), 0, 0, 0);
        l.addView(a, pa);
        l.addView(b, pb);
        content.addView(l, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 10));
    }

    private View factRow(int icon, String title, String value) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        r.addView(Ui.icon(this, icon, R.color.primary, 20));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(Ui.dp(this, 12), 0, 0, 0);
        tx.addView(Ui.text(this, title, 12, false, R.color.muted));
        tx.addView(Ui.text(this, value, 14, true, R.color.ink));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return r;
    }

    private View legendDot(int colorRes, String label) {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(Ui.shape(this, colorRes, 4, 0));
        l.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 10), Ui.dp(this, 10)));
        TextView t = Ui.text(this, label, 12.5f, false, R.color.muted);
        t.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 10), 0);
        l.addView(t);
        return l;
    }

    /** Danh sách bất thường dạng thu gọn: hiện 5 dòng đầu, "Xem thêm" mở rộng dần. kind: 0 = đang tồn tại, 1 = phát sinh mới, 2 = đã xử lý. */
    private void statList(List<vn.evnqt1.helpdesk.core.StatsReport.Item> items, int kind, String caption, String emptyText) {
        if (items.isEmpty()) { if (kind != 0) empty(emptyText); return; }
        if (caption != null) content.addView(Ui.note(this, caption));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        content.addView(box);
        fillStatList(box, items, kind, kind == 0 ? openShown : kind == 1 ? newShown : resolvedShown);
    }

    private void fillStatList(LinearLayout box, List<vn.evnqt1.helpdesk.core.StatsReport.Item> items, int kind, int count) {
        box.removeAllViews();
        int to = Math.min(items.size(), count);
        for (int i = 0; i < to; i++) box.addView(statItemRow(items.get(i), kind, null), Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 8));
        if (to < items.size()) {
            final int next = Math.min(items.size(), count + 10);
            LinearLayout more = Ui.button(this, "Xem thêm (" + (items.size() - to) + ")", Ui.BTN_OUTLINE, 0, v -> {
                if (kind == 0) openShown = next; else if (kind == 1) newShown = next; else resolvedShown = next;
                fillStatList(box, items, kind, next);
            });
            box.addView(more, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 8));
        }
    }

    private View statItemRow(vn.evnqt1.helpdesk.core.StatsReport.Item it, int kind, View.OnClickListener override) {
        LinearLayout c = new LinearLayout(this);
        c.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        View stripe = new View(this);
        stripe.setBackground(Ui.shape(this, severityColors(it.severity)[0], 3, 0));
        c.addView(stripe, Ui.margins(new LinearLayout.LayoutParams(Ui.dp(this, 4), ViewGroup.LayoutParams.MATCH_PARENT), this, 0, 0, 12, 0));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout chips = row(Ui.chip(this, it.code, R.color.primary, R.color.primary_soft), severityChip(it.severity));
        if (kind == 1) chips.addView(statusChip(it.status));
        col.addView(chips);
        TextView title = Ui.text(this, it.title == null ? "(Chưa có tiêu đề)" : it.title, 14.5f, true, R.color.ink);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(0, Ui.dp(this, 6), 0, 0);
        col.addView(title);
        col.addView(Ui.text(this, n(it.equipment) + (n(it.kks).isEmpty() ? "" : " · KKS: " + it.kks), 12.5f, false, R.color.muted));
        String meta;
        if (kind == 2) meta = "Đã xử lý " + fmt(it.resolvedAt) + " · mất " + durationText(Math.max(0, it.resolvedAt - it.detectedAt)) + (it.reopened ? " · từng mở lại" : "");
        else if (kind == 0) meta = "Phát hiện " + fmt(it.detectedAt) + " · tồn tại " + durationText(Math.max(0, System.currentTimeMillis() - it.detectedAt));
        else meta = "Phát hiện " + fmt(it.detectedAt) + (it.isOpen() ? "" : " · đã xử lý " + dayShort(it.resolvedAt));
        TextView mt = Ui.text(this, meta, 11.5f, false, kind == 2 ? R.color.success : R.color.muted);
        mt.setPadding(0, Ui.dp(this, 4), 0, 0);
        col.addView(mt);
        c.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final String id = it.id;
        Ui.clickable(c, 16, R.color.surface, R.color.outline, override != null ? override : v -> push("anomaly", id));
        return c;
    }

    private String dayShort(long t) { dayFmt.setTimeZone(Ui.VN); return t <= 0 ? "" : dayFmt.format(new Date(t)); }

    private View groupRow(int rank, vn.evnqt1.helpdesk.core.StatsReport.Group g, int max, int total) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 6));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView rk = Ui.text(this, String.valueOf(rank), 12, true, rank == 1 ? R.color.on_brand : R.color.muted);
        rk.setGravity(Gravity.CENTER);
        rk.setBackground(Ui.shape(this, rank == 1 ? R.color.brand : R.color.surface_alt, 10, 0));
        top.addView(rk, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
        TextView nm = Ui.text(this, g.name, 14, true, R.color.ink);
        nm.setMaxLines(2);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nm.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 8), 0);
        top.addView(nm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        int pct = total == 0 ? 0 : Math.round(g.count * 100f / total);
        top.addView(Ui.text(this, g.count + " · " + pct + "%", 13, true, R.color.primary));
        r.addView(top);
        LinearLayout track = new LinearLayout(this);
        track.setBackground(Ui.shape(this, R.color.surface_alt, 4, 0));
        View fill = new View(this);
        fill.setBackground(Ui.shape(this, rank == 1 ? R.color.brand : R.color.info, 4, 0));
        track.addView(fill, new LinearLayout.LayoutParams(0, Ui.dp(this, 8), g.count));
        if (max > g.count) track.addView(new View(this), new LinearLayout.LayoutParams(0, Ui.dp(this, 8), max - g.count));
        r.addView(track, Ui.margins(Ui.matchWrap(), this, 32, 6, 0, 0));
        if (g.open > 0) {
            TextView o = Ui.text(this, g.open + " còn tồn tại", 11.5f, false, R.color.warning);
            o.setPadding(Ui.dp(this, 32), Ui.dp(this, 4), 0, 0);
            r.addView(o);
        }
        Ui.clickable(r, 12, R.color.surface, 0, v -> showGroupDialog(g));
        return r;
    }

    private void showGroupDialog(vn.evnqt1.helpdesk.core.StatsReport.Group g) {
        LinearLayout box = form();
        final AlertDialog[] holder = new AlertDialog[1];
        for (vn.evnqt1.helpdesk.core.StatsReport.Item it : g.items) {
            final String id = it.id;
            box.addView(statItemRow(it, 1, v -> { holder[0].dismiss(); push("anomaly", id); }), Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 8));
        }
        holder[0] = new AlertDialog.Builder(this).setTitle(g.name + " (" + g.count + ")").setView(scrolled(box)).setNegativeButton("Đóng", null).create();
        holder[0].show();
    }

    // ---------------------------------------------------------------- bảng chọn dạng bottom sheet (thao tác một tay)
    private void showSheet(String title, String[] labels, int[] icons, Runnable[] actions) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 18));
        View handle = new View(this);
        handle.setBackground(Ui.shape(this, R.color.outline, 3, 0));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = Ui.dp(this, 12);
        box.addView(handle, hp);
        TextView t = Ui.text(this, title, 17, true, R.color.ink);
        t.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 8));
        box.addView(t);
        final AlertDialog[] holder = new AlertDialog[1];
        for (int i = 0; i < labels.length; i++) {
            final Runnable a = actions[i];
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setMinimumHeight(Ui.dp(this, 56));
            r.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
            LinearLayout disc = new LinearLayout(this);
            disc.setGravity(Gravity.CENTER);
            disc.setBackground(Ui.shape(this, R.color.primary_soft, 14, 0));
            disc.addView(Ui.icon(this, icons[i], R.color.primary, 22));
            r.addView(disc, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
            TextView l = Ui.text(this, labels[i], 15.5f, true, R.color.ink);
            l.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
            r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            r.addView(Ui.icon(this, R.drawable.ic_chevron, R.color.muted, 22));
            Ui.clickable(r, 14, R.color.surface, 0, v -> { holder[0].dismiss(); a.run(); });
            box.addView(r);
        }
        holder[0] = new AlertDialog.Builder(this).setView(box).create();
        holder[0].show();
        android.view.Window w = holder[0].getWindow();
        if (w != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Ui.color(this, R.color.surface));
            float r = Ui.dp(this, 24);
            bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            w.setBackgroundDrawable(bg);
            w.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            w.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels, Ui.dp(this, 560)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void fabMenu() {
        showSheet("Thêm nhanh",
                new String[]{"Ghi công việc bất thường", "Ghi công việc bản vẽ", "Tạo bất thường mới", "Tạo hồ sơ bản vẽ"},
                new int[]{R.drawable.ic_edit, R.drawable.ic_drawing, R.drawable.ic_add, R.drawable.ic_report},
                new Runnable[]{this::chooseAnomalyWork, this::chooseDrawingWork, this::newAnomaly, this::newDrawing});
    }

    /** Hiện/ẩn nút nổi: chỉ ở trang chính cần thao tác nhanh và khi bàn phím không bật. */
    private void applyFab() { if (fab != null) fab.setVisibility(fabWanted && !imeVisible ? View.VISIBLE : View.GONE); }

    /** "5 phút trước", "3 giờ trước", "2 ngày trước", ... */
    private String ago(long t) {
        long d = Math.max(0, System.currentTimeMillis() - t);
        if (d < 60_000L) return "vừa xong";
        if (d < 3_600_000L) return (d / 60_000L) + " phút trước";
        if (d < 86_400_000L) return (d / 3_600_000L) + " giờ trước";
        if (d < 30L * 86_400_000L) return (d / 86_400_000L) + " ngày trước";
        return fmt(t);
    }

    // ======================================================================
    // Trợ lý phân tích (offline, không AI)
    // ======================================================================
    private static final String[][] PRESETS = {
            {AssistantIntent.OVERVIEW, "Tổng quan tình hình", "Số liệu bất thường, bản vẽ, cảnh báo và ca đang trực"},
            {AssistantIntent.OLDEST_OPEN, "Bất thường tồn đọng lâu nhất", "Các hồ sơ chưa xử lý xong, cũ nhất trước"},
            {AssistantIntent.NEW_ANOMALIES, "Bất thường mới phát sinh", "Danh sách theo khoảng thời gian chọn"},
            {AssistantIntent.SEVERITY_STATUS, "Thống kê mức độ × trạng thái", "Bảng chéo số lượng theo khoảng thời gian"},
            {AssistantIntent.TOP_EQUIPMENT, "Thiết bị / hệ thống lặp lại nhiều nhất", "Xếp theo số lần xuất hiện bất thường"},
            {AssistantIntent.RESOLUTION_TIME, "Thời gian xử lý trung bình", "Trung bình, nhanh nhất, lâu nhất"},
            {AssistantIntent.WORKLOAD_BY_CREW, "Khối lượng công việc theo kíp", "Số lần ghi nhận công việc của từng kíp"},
            {AssistantIntent.DRAWING_REVISIONS, "Bản vẽ chỉnh sửa nhiều nhất", "Xếp theo số revision"},
            {AssistantIntent.WHO_ON_DUTY, "Ai đang trực / kíp nào trực lúc…", "Tra theo thời điểm, kèm lịch 3 ca trong ngày"},
            {AssistantIntent.PERSON_SCHEDULE, "Lịch trực của một người", "Các ca của một nhân sự trong khoảng thời gian"},
            {AssistantIntent.SHIFT_COUNT, "Số ca trực của từng người", "Đếm ca sáng/chiều/đêm, kiểm tra chia đều"},
            {AssistantIntent.ALERTS, "Cảnh báo đang có hiệu lực", "Các cảnh báo nội bộ theo ngưỡng đã cài"},
    };

    private void showAssistant() {
        LinearLayout hero = Ui.card(this);
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Ui.color(this, R.color.brand), Ui.color(this, R.color.brand_deep)});
        hg.setCornerRadius(Ui.dp(this, 20));
        hero.setBackground(hg);
        hero.setPadding(Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.icon(this, R.drawable.ic_sparkle, R.color.accent, 22));
        TextView ht = Ui.text(this, "Trợ lý phân tích offline", 17, true, R.color.on_brand);
        ht.setPadding(Ui.dp(this, 8), 0, 0, 0);
        head.addView(ht);
        hero.addView(head);
        TextView hd = Ui.text(this, "Chạy hoàn toàn trong máy, không cần mạng và không dùng AI. Chọn một mẫu phân tích bên dưới, hoặc gõ câu hỏi ngắn; trợ lý hiểu theo từ khóa và chỉ tổng hợp số liệu từ hồ sơ bạn đã nhập.", 13, false, R.color.on_brand);
        hd.setAlpha(0.85f);
        hd.setPadding(0, Ui.dp(this, 8), 0, 0);
        hero.addView(hd);
        Ui.addCard(content, hero);

        LinearLayout qc = Ui.card(this);
        final EditText q = Ui.edit(this, qc, "Câu hỏi", "", false);
        q.setHint("Ví dụ: kíp nào đang trực? / bất thường tồn đọng lâu nhất");
        qc.addView(Ui.button(this, "Hỏi trợ lý", Ui.BTN_PRIMARY, R.drawable.ic_search, v -> { String text = value(q); if (text.isEmpty()) { toast("Nhập câu hỏi hoặc chọn một mẫu bên dưới"); return; } askAssistant(text); }));
        Ui.addCard(content, qc);

        section("Mẫu phân tích có sẵn");
        for (String[] p : PRESETS) menuItem(p[1], p[2], R.drawable.ic_report, () -> presetDialog(p[0]));
        content.addView(Ui.note(this, "Gợi ý câu hỏi: “Lịch trực của Nguyễn Anh Quân tháng sau”, “Thiết bị nào hỏng nhiều nhất 30 ngày”, “Khối lượng công việc theo kíp tháng trước”, “Đếm ca đêm tháng này”."));
    }

    private void askAssistant(String question) {
        AnalysisEngine engine = new AnalysisEngine(this, repo);
        AssistantIntent in = AssistantIntent.detect(question, engine.knownNames());
        analysis = runIntent(engine, in);
        push("analysis", null);
    }

    private AnalysisResult runIntent(AnalysisEngine e, AssistantIntent in) {
        long now = System.currentTimeMillis();
        switch (in.type) {
            case AssistantIntent.OVERVIEW: return e.overview(now);
            case AssistantIntent.OLDEST_OPEN: return e.oldestOpen(now, 10);
            case AssistantIntent.NEW_ANOMALIES: return e.newAnomalies(in.rangeGiven ? in.range : AnalysisRange.LAST30, now);
            case AssistantIntent.SEVERITY_STATUS: return e.severityStatus(in.range, now);
            case AssistantIntent.TOP_EQUIPMENT: return e.topEquipment(in.range, 0, now);
            case AssistantIntent.RESOLUTION_TIME: return e.resolutionTime(in.range, now);
            case AssistantIntent.WORKLOAD_BY_CREW: return e.workloadByCrew(in.range, now, in.crew);
            case AssistantIntent.DRAWING_REVISIONS: return e.drawingRevisions(in.range, now);
            case AssistantIntent.WHO_ON_DUTY: return e.whoOnDuty(now);
            case AssistantIntent.PERSON_SCHEDULE: return e.personSchedule(in.person, in.range, now);
            case AssistantIntent.SHIFT_COUNT: return e.shiftCount(in.range, now);
            case AssistantIntent.ALERTS: return e.alerts();
            default: return e.search(in.query);
        }
    }

    private void presetDialog(String type) {
        final AnalysisEngine engine = new AnalysisEngine(this, repo);
        final long now = System.currentTimeMillis();
        if (type.equals(AssistantIntent.OVERVIEW) || type.equals(AssistantIntent.OLDEST_OPEN) || type.equals(AssistantIntent.ALERTS)) {
            AssistantIntent in = new AssistantIntent(); in.type = type; analysis = runIntent(engine, in); push("analysis", null); return;
        }
        String title = "Phân tích";
        for (String[] p : PRESETS) if (p[0].equals(type)) title = p[1];
        LinearLayout f = form();
        Picker range = null, group = null, crew = null, person = null;
        DateTimeField when = null;
        int defRange = type.equals(AssistantIntent.NEW_ANOMALIES) ? AnalysisRange.LAST30 : (type.equals(AssistantIntent.WORKLOAD_BY_CREW) || type.equals(AssistantIntent.SHIFT_COUNT) || type.equals(AssistantIntent.PERSON_SCHEDULE)) ? AnalysisRange.THIS_MONTH : AnalysisRange.ALL;
        if (type.equals(AssistantIntent.WHO_ON_DUTY)) {
            when = new DateTimeField(this, f, "Thời điểm cần tra", now, true);
        } else {
            if (type.equals(AssistantIntent.PERSON_SCHEDULE)) {
                List<String> names = engine.knownNames();
                if (names.isEmpty()) { toast("Chưa có nhân sự trong lịch ca. Hãy nhập lịch ca từ file Excel trước."); return; }
                person = new Picker(this, f, "Nhân sự", names.toArray(new String[0]), 0);
            }
            if (type.equals(AssistantIntent.TOP_EQUIPMENT)) group = new Picker(this, f, "Nhóm theo", new String[]{"Thiết bị", "KKS", "Hệ thống / khu vực"}, 0);
            if (type.equals(AssistantIntent.WORKLOAD_BY_CREW)) crew = new Picker(this, f, "Kíp", new String[]{"Tất cả kíp", "A", "B", "C", "D", "E"}, 0);
            range = new Picker(this, f, "Khoảng thời gian", AnalysisRange.LABELS, defRange);
        }
        final Picker fr = range, fg = group, fc = crew, fp = person; final DateTimeField fw = when;
        showForm(title, f, "Phân tích", () -> {
            int r = fr == null ? AnalysisRange.ALL : fr.index;
            AnalysisResult res;
            switch (type) {
                case AssistantIntent.NEW_ANOMALIES: res = engine.newAnomalies(r, now); break;
                case AssistantIntent.SEVERITY_STATUS: res = engine.severityStatus(r, now); break;
                case AssistantIntent.TOP_EQUIPMENT: res = engine.topEquipment(r, fg.index, now); break;
                case AssistantIntent.RESOLUTION_TIME: res = engine.resolutionTime(r, now); break;
                case AssistantIntent.WORKLOAD_BY_CREW: res = engine.workloadByCrew(r, now, fc.index == 0 ? null : fc.value()); break;
                case AssistantIntent.DRAWING_REVISIONS: res = engine.drawingRevisions(r, now); break;
                case AssistantIntent.WHO_ON_DUTY: res = engine.whoOnDuty(fw.millis); break;
                case AssistantIntent.PERSON_SCHEDULE: res = engine.personSchedule(fp.value(), r, now); break;
                case AssistantIntent.SHIFT_COUNT: res = engine.shiftCount(r, now); break;
                default: throw new Exception("Mẫu phân tích không được hỗ trợ");
            }
            analysis = res;
            push("analysis", null);
        });
    }

    private String plainText(AnalysisResult r) {
        StringBuilder b = new StringBuilder(r.title).append("\n");
        for (String l : r.lines) b.append(l).append("\n");
        if (r.header.length > 0) b.append("\n").append(String.join("\t", r.header)).append("\n");
        for (String[] row : r.rows) b.append(String.join("\t", row)).append("\n");
        return b.toString();
    }

    private void showAnalysis() {
        if (analysis == null) { Ui.addCard(content, errorCard("Chưa có kết quả phân tích.")); return; }
        final AnalysisResult r = analysis;
        LinearLayout head = Ui.card(this);
        head.addView(Ui.text(this, r.title, 18, true, R.color.ink));
        for (String l : r.lines) { TextView t = Ui.text(this, l, 14, false, R.color.ink); t.setPadding(0, Ui.dp(this, 6), 0, 0); head.addView(t); }
        if (r.isEmpty()) head.addView(Ui.text(this, "Không có dữ liệu phù hợp.", 14, false, R.color.muted));
        Ui.addCard(content, head);

        if (r.header.length > 0 && !r.rows.isEmpty()) {
            int cols = r.header.length;
            float[] w = new float[cols];
            for (int c = 0; c < cols; c++) {
                int len = r.header[c].length();
                for (String[] row : r.rows) if (c < row.length) len = Math.max(len, Math.min(row[c].length(), 28));
                w[c] = Math.max(3, Math.min(len, 22));
            }
            LinearLayout tbl = Ui.card(this);
            tbl.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            LinearLayout hr = new LinearLayout(this);
            for (int c = 0; c < cols; c++) { TextView h = Ui.text(this, r.header[c], 11.5f, true, R.color.muted); hr.addView(h, Ui.margins(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w[c]), this, 0, 0, 6, 0)); }
            tbl.addView(hr);
            tbl.addView(Ui.divider(this));
            for (int i = 0; i < r.rows.size(); i++) {
                String[] row = r.rows.get(i);
                LinearLayout line = new LinearLayout(this);
                line.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
                for (int c = 0; c < cols; c++) { TextView t = Ui.text(this, c < row.length ? row[c] : "", 13, c == 0, R.color.ink); line.addView(t, Ui.margins(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w[c]), this, 0, 0, 6, 0)); }
                tbl.addView(line);
            }
            Ui.addCard(content, tbl);
        }
        if (!r.links.isEmpty()) {
            section(r.linksTitle);
            for (String[] l : r.links) {
                LinearLayout c = new LinearLayout(this);
                c.setGravity(Gravity.CENTER_VERTICAL);
                c.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
                TextView t = Ui.text(this, l[2], 14, true, R.color.ink);
                c.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                c.addView(Ui.icon(this, R.drawable.ic_chevron, R.color.muted, 22));
                final String type = "DRAWING".equals(l[0]) ? "drawing" : "anomaly", id = l[1];
                Ui.clickable(c, 14, R.color.surface, R.color.outline, v -> push(type, id));
                Ui.addCard(content, c);
            }
        }
        buttonRow(Ui.button(this, "Sao chép kết quả", Ui.BTN_TONAL, R.drawable.ic_report, v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) { cm.setPrimaryClip(android.content.ClipData.newPlainText("Kết quả phân tích", plainText(r))); toast("Đã sao chép kết quả"); }
        }), Ui.button(this, "Phân tích khác", Ui.BTN_OUTLINE, R.drawable.ic_sparkle, v -> pop()));
        content.addView(Ui.note(this, "Số liệu tổng hợp từ hồ sơ trong máy, tính offline. Không thay thế đánh giá kỹ thuật của người vận hành."));
    }

    // ======================================================================
    // Kết quả Activity
    // ======================================================================
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_FORM_ATTACH) { onFormFilesPicked(data); return; }
        if (requestCode == REQ_ATTACHMENT) { onDetailFilesPicked(data); return; }
        if (data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_SCHEDULE_FILE) { readScheduleFile(uri); return; }
        if (requestCode == REQ_SAMPLE) { writeSample(uri); return; }
        if (requestCode == REQ_EXPORT) {
            try {
                ReportExporter exporter = new ReportExporter(this, repo);
                if ("CSV".equals(pendingExportType)) exporter.exportCsv(uri, pendingReportScope, pendingReportFrom, pendingReportTo);
                else if (pendingExportType.startsWith("Excel")) exporter.exportXlsx(uri, pendingReportScope, pendingReportFrom, pendingReportTo);
                else exporter.exportPdf(uri, pendingReportScope, pendingReportFrom, pendingReportTo);
                toast("Đã xuất báo cáo vào vị trí đã chọn");
            } catch (Exception e) { error(e); }
        } else if (requestCode == REQ_BACKUP) {
            final String pw = pendingBackupPassword;
            io.execute(() -> { try { new BackupManager(this).exportBackup(uri, pw); main.post(() -> toast("Đã tạo backup .elbackup")); } catch (Exception e) { main.post(() -> error(e)); } });
        } else if (requestCode == REQ_IMPORT) {
            final String password = pendingBackupPassword;
            io.execute(() -> {
                try {
                    BackupManager manager = new BackupManager(this);
                    org.json.JSONObject preview = manager.preview(uri, password);
                    main.post(() -> new AlertDialog.Builder(this).setTitle("Xem trước backup")
                            .setMessage("Schema: " + preview.optInt("schemaVersion") + "\nBất thường: " + preview.optLong("anomalies") + "\nBản vẽ: " + preview.optLong("drawings") + "\nLần ca: " + preview.optLong("shifts") + "\nTệp đính kèm: " + preview.optInt("attachments") + "\n\nKhôi phục thay thế toàn bộ dữ liệu hiện tại? Nên có backup hiện tại trước khi tiếp tục.")
                            .setNegativeButton("Hủy", null).setPositiveButton("Khôi phục thay thế", (d, w) -> io.execute(() -> {
                                try { manager.restoreReplace(uri, password); main.post(() -> { toast("Đã khôi phục backup"); openTab("Tổng quan"); }); } catch (Exception ex) { main.post(() -> error(ex)); }
                            })).show());
                } catch (Exception ex) { main.post(() -> error(ex)); }
            });
        }
    }
}
