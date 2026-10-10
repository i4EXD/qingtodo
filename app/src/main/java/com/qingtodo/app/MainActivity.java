package com.qingtodo.app;

import android.app.Activity;
import android.app.Dialog;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowInsetsController;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.PathInterpolator;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** A small native Android app, with no WebView, network or external dependencies. */
public class MainActivity extends Activity {
    private TodoStore store;
    private LinearLayout root;
    private FrameLayout host;
    private FrameLayout content;
    private FrameLayout actionDock;
    private LinearLayout bottomBar;
    private ScrollView scroll;
    private FrameLayout currentPage;
    private final int[] scrollPositions = new int[]{0, 0};
    private int bottomInset = 0;
    private int renderGeneration = 0;
    private int page = 0;
    private int filter = 0;
    private int bg, surface, ink, muted, primary, onPrimary, soft, outline, danger, finished;
    private ColorTheme colorTheme;
    private boolean dark;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View snackView;
    private Runnable snackTimeout;
    private TodoStore.Item pendingUndo;
    private boolean pendingUndoTemplate;
    private boolean pendingUndoCompleted;
    private int pendingUndoCompletedCount;
    private long undoExpiresAt;
    private Dialog aboutDialog;
    private Dialog themeDialog;
    private ScrollView themeScroll;
    private String snackMessage, snackAction;
    private Runnable snackCallback;
    private long snackExpiresAt;
    private int snackDestination;
    private Dialog editor;
    private EditText editorTitle, editorNote;
    private CheckBox editorSaveTemplate;
    private boolean editorIsTemplate;
    private long editorId = -1;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                if (page == 1) switchPage(0); else finish();
            });
        }
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        palette();
        store = new TodoStore(this);
        if (savedState == null || !savedState.getBoolean("undo_completed", false)
                || savedState.getLong("undo_expires", 0) <= SystemClock.elapsedRealtime()) store.discardCompletedUndo();
        if (savedState != null) {
            page = savedState.getInt("page", 0);
            filter = savedState.getInt("filter", 0);
            scrollPositions[0] = savedState.getInt("scroll_tasks", 0);
            scrollPositions[1] = savedState.getInt("scroll_templates", 0);
        }
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(bg);
        if (Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        w.getDecorView().setSystemUiVisibility(flags);
        if (Build.VERSION.SDK_INT >= 30) {
            int appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            w.getInsetsController().setSystemBarsAppearance(dark ? 0 : appearance, appearance);
        }

        host = new FrameLayout(this);
        host.setBackgroundColor(bg);
        root = column();
        root.setBackgroundColor(bg);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                bottomInset = bars.bottom;
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            positionSnack();
            return insets;
        });
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        actionDock = new FrameLayout(this);
        root.addView(actionDock, new LinearLayout.LayoutParams(-1, dp(72)));
        bottomBar = row();
        root.addView(bottomBar, new LinearLayout.LayoutParams(-1, dp(80)));
        host.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContentView(host);
        root.requestApplyInsets();
        render(false);
        if (savedState != null && savedState.getLong("undo_expires", 0) > SystemClock.elapsedRealtime()) {
            pendingUndoCompleted = savedState.getBoolean("undo_completed", false);
            if (pendingUndoCompleted) pendingUndoCompletedCount = store.completedUndoCount();
            else {
                pendingUndo = new TodoStore.Item(savedState.getLong("undo_id"), savedState.getString("undo_title"), savedState.getString("undo_note"), savedState.getBoolean("undo_done"), savedState.getLong("undo_created"));
                pendingUndoTemplate = savedState.getBoolean("undo_template");
            }
            undoExpiresAt = savedState.getLong("undo_expires");
            presentUndo();
        } else if (savedState != null && savedState.getLong("snack_expires", 0) > SystemClock.elapsedRealtime()
                && savedState.getString("snack_message") != null) {
            int destination = savedState.getInt("snack_destination", 0);
            showSnack(savedState.getString("snack_message"), destination == 1 ? "查看" : null,
                    destination == 1 ? () -> showPendingTasks() : null,
                    savedState.getLong("snack_expires") - SystemClock.elapsedRealtime());
            snackDestination = destination;
        }
        if (savedState != null && savedState.getBoolean("editor_open", false)) {
            boolean template = savedState.getBoolean("editor_template", false);
            long id = savedState.getLong("editor_id", -1);
            TodoStore.Item found = null;
            if (id != -1) {
                for (TodoStore.Item item : template ? store.templates() : store.tasks()) if (item.id == id) found = item;
            }
            if (id == -1 || found != null) {
                openEditor(template, found);
                editorTitle.setText(savedState.getString("draft_title", ""));
                editorNote.setText(savedState.getString("draft_note", ""));
                restoreSelection(editorTitle, savedState.getInt("draft_title_start", editorTitle.length()), savedState.getInt("draft_title_end", editorTitle.length()));
                restoreSelection(editorNote, savedState.getInt("draft_note_start", editorNote.length()), savedState.getInt("draft_note_end", editorNote.length()));
                if (savedState.getBoolean("draft_note_focus", false)) editorNote.requestFocus();
                if (editorSaveTemplate != null) editorSaveTemplate.setChecked(savedState.getBoolean("draft_template", false));
            }
        } else if (savedState != null && savedState.getBoolean("themes_open", false)) {
            showThemes();
        } else if (savedState != null && savedState.getBoolean("about_open", false)) {
            showAbout();
        }
    }

    private void restoreSelection(EditText field, int start, int end) {
        field.setSelection(Math.max(0, Math.min(start, field.length())), Math.max(0, Math.min(end, field.length())));
    }

    private void palette() {
        colorTheme = ColorTheme.fromId(getSharedPreferences("appearance", MODE_PRIVATE).getString("color_theme", "purple"));
        getTheme().applyStyle(colorTheme.nativeStyle(), true);
        bg = colorTheme.background(dark);
        surface = colorTheme.surface(dark);
        ink = colorTheme.ink(dark);
        muted = colorTheme.muted(dark);
        primary = colorTheme.primary(dark);
        onPrimary = colorTheme.onPrimary(dark);
        soft = colorTheme.soft(dark);
        outline = colorTheme.outline(dark);
        danger = Color.parseColor(dark ? "#FFB4AB" : "#B34D58");
        finished = muted;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); v.setBaselineAligned(false); return v; }
    private boolean motionEnabled() { return ValueAnimator.areAnimatorsEnabled() && !isFinishing(); }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setFontFeatureSettings("kern");
        v.setIncludeFontPadding(false);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }
    private GradientDrawable shape(int color, float radius, boolean border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (border) d.setStroke(dp(1), outline);
        return d;
    }
    private RippleDrawable ripple(int color, float radius, boolean border) {
        int rippleColor = (primary & 0x00FFFFFF) | 0x22000000;
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), shape(color, radius, border), shape(Color.WHITE, radius, false));
    }
    private void gap(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private TextView button(String label, int fill, int foreground) {
        TextView b = text(label, 14, foreground, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(12), dp(18), dp(12));
        b.setMinHeight(dp(48));
        b.setMaxLines(1);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setBackground(ripple(fill, 24, false));
        b.setClickable(true);
        b.setFocusable(true);
        b.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
            }
        });
        return b;
    }
    private Icon iconButton(String type, int color, String description) {
        Icon icon = new Icon(type, color);
        icon.setBackground(ripple(Color.TRANSPARENT, 24, false));
        icon.setContentDescription(description);
        icon.setClickable(true);
        icon.setFocusable(true);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        icon.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.ImageButton");
            }
        });
        return icon;
    }

    private void render(boolean keepScroll) { render(keepScroll, 0); }

    private void render(boolean keepScroll, int motion) {
        final int generation = ++renderGeneration;
        int y = keepScroll && scroll != null ? scroll.getScrollY() : scrollPositions[page];
        FrameLayout previous = currentPage;
        for (int i = content.getChildCount() - 1; i >= 0; i--) {
            View stale = content.getChildAt(i);
            stale.animate().cancel();
            if (stale != previous) content.removeView(stale);
        }
        currentPage = new FrameLayout(this);
        final FrameLayout incoming = currentPage;
        content.addView(incoming, new FrameLayout.LayoutParams(-1, -1));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        incoming.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        final ScrollView newScroll = scroll;
        LinearLayout body = column();
        body.setPadding(dp(24), dp(18), dp(24), dp(24));
        FrameLayout bounded = new FrameLayout(this);
        int available = getResources().getConfiguration().screenWidthDp;
        FrameLayout.LayoutParams bodyParams = new FrameLayout.LayoutParams(available > 640 ? dp(640) : -1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        bounded.addView(body, bodyParams);
        scroll.addView(bounded);
        if (page == 0) renderTasks(body); else renderTemplates(body);
        renderNav();
        addFab();
        newScroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View view, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                newScroll.removeOnLayoutChangeListener(this);
                if (generation == renderGeneration) newScroll.scrollTo(0, y);
            }
        });
        positionSnack();
        if (previous != null && motion != 0 && motionEnabled()) {
            previous.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            disableTree(previous);
            float direction = motion == 1 ? 1 : motion == -1 ? -1 : 0;
            if (root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) direction *= -1;
            float distance = dp(direction == 0 ? 0 : 24) * direction;
            incoming.setTranslationX(distance);
            incoming.setTranslationY(direction == 0 ? dp(6) : 0);
            incoming.setAlpha(0f);
            PathInterpolator easing = new PathInterpolator(.2f, 0f, 0f, 1f);
            previous.animate().alpha(0f).translationX(-distance * .5f).setDuration(direction == 0 ? 120 : 180).setInterpolator(easing).withEndAction(() -> {
                if (previous.getParent() == content && previous != currentPage) content.removeView(previous);
            }).start();
            incoming.animate().alpha(1f).translationX(0f).translationY(0f).setDuration(direction == 0 ? 180 : 280).setInterpolator(easing).withEndAction(() -> {
                if (generation == renderGeneration && incoming == currentPage) {
                    for (int i = content.getChildCount() - 1; i >= 0; i--) if (content.getChildAt(i) != incoming) content.removeViewAt(i);
                }
            }).start();
        } else if (previous != null) content.removeView(previous);
    }

    private void disableTree(View view) {
        view.setEnabled(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) disableTree(group.getChildAt(i));
        }
    }

    private void switchPage(int target) {
        if (page == target) return;
        if (scroll != null) scrollPositions[page] = scroll.getScrollY();
        int oldPage = page;
        page = target;
        render(false, target > oldPage ? 1 : -1);
    }

    private void switchFilter(int target) {
        if (filter == target) return;
        filter = target;
        scrollPositions[0] = 0;
        render(false, 2);
    }

    private void showPendingTasks() {
        filter = 1;
        scrollPositions[0] = 0;
        if (page == 0) render(false, 2); else switchPage(0);
    }

    private void header(LinearLayout body, String title, String subtitle) {
        LinearLayout top = row();
        TextView date = text(new SimpleDateFormat("M 月 d 日 · EEEE", Locale.SIMPLIFIED_CHINESE).format(new Date()), 12, muted, false);
        top.addView(date, new LinearLayout.LayoutParams(0, -2, 1));
        Icon themes = iconButton("palette", muted, "颜色主题");
        themes.setOnClickListener(v -> showThemes());
        top.addView(themes);
        Icon about = iconButton("info", muted, "关于轻待办");
        about.setOnClickListener(v -> showAbout());
        top.addView(about);
        body.addView(top);
        TextView heading = text(title, 32, ink, true);
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        body.addView(heading);
        gap(body, 9);
        TextView sub = text(subtitle, 13, muted, false);
        sub.setLineSpacing(dp(3), 1f);
        body.addView(sub);
        gap(body, 24);
    }

    private void renderTasks(LinearLayout body) {
        List<TodoStore.Item> tasks = store.tasks();
        int done = 0;
        for (TodoStore.Item item : tasks) if (item.done) done++;
        int pending = tasks.size() - done;
        header(body, "待办", "按自己的节奏，一件一件完成。");

        LinearLayout summary = column();
        summary.setPadding(dp(20), dp(20), dp(20), dp(20));
        summary.setBackground(shape(soft, 24, false));
        LinearLayout summaryTop = row();
        summaryTop.addView(text("完成进度", 12, primary, true), new LinearLayout.LayoutParams(0, -2, 1));
        summaryTop.addView(text(done + " / " + tasks.size(), 12, primary, true));
        summary.addView(summaryTop);
        gap(summary, 13);
        summary.addView(text(tasks.isEmpty() ? "从一件小事开始" : pending == 0 ? "全部完成，做得很好！" : "还有 " + pending + " 件事待完成", 20, ink, true));
        gap(summary, 16);
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(Math.max(1, tasks.size()));
        progress.setProgress(done);
        progress.setProgressTintList(ColorStateList.valueOf(primary));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(colorTheme.track(dark)));
        progress.setProgressBackgroundTintMode(android.graphics.PorterDuff.Mode.SRC_IN);
        progress.setContentDescription("已完成 " + done + " 件，共 " + tasks.size() + " 件");
        summary.addView(progress, new LinearLayout.LayoutParams(-1, dp(6)));
        body.addView(summary, new LinearLayout.LayoutParams(-1, -2));
        gap(body, 20);

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filters = row();
        String[] labels = {"全部  " + tasks.size(), "未完成  " + pending, "已完成  " + done};
        for (int i = 0; i < 3; i++) {
            final int chosen = i;
            TextView chip = button(labels[i], filter == i ? primary : surface, filter == i ? onPrimary : muted);
            chip.setTextSize(13);
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            chip.setMinHeight(dp(44));
            chip.setSelected(filter == i);
            chip.setOnClickListener(v -> switchFilter(chosen));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMargins(0, 0, dp(i == 2 ? 0 : 8), 0);
            filters.addView(chip, params);
        }
        filterScroll.addView(filters);
        body.addView(filterScroll);
        if (filter == 2 && done > 0) {
            gap(body, 12);
            TextView clear = button("清除已完成", soft, primary);
            clear.setContentDescription("清除已完成的 " + done + " 件事项");
            clear.setOnClickListener(v -> {
                if (!clear.isEnabled()) return;
                clear.setEnabled(false);
                clearCompleted();
            });
            body.addView(clear, new LinearLayout.LayoutParams(-1, -2));
        }
        gap(body, 24);

        int displayed = 0;
        for (TodoStore.Item item : tasks) if (filter == 0 || filter == 1 && !item.done || filter == 2 && item.done) displayed++;
        if (displayed == 0) {
            if (tasks.isEmpty()) empty(body, "list", "清单还是空的", "写下第一件待办，或从模板快速添加。", "从模板添加", () -> switchPage(1));
            else if (filter == 1) empty(body, "celebrate", "待办都完成啦", "给自己一点休息，再开始新的小目标。", null, null);
            else empty(body, "check", "这里等着你的第一个勾", "在事项上轻点一下，就能标记完成。", null, null);
            return;
        }
        boolean pendingHeader = false, doneHeader = false;
        for (TodoStore.Item item : tasks) {
            if (filter == 1 && item.done || filter == 2 && !item.done) continue;
            if (!item.done && !pendingHeader) { section(body, "进行中", pending); pendingHeader = true; }
            if (item.done && !doneHeader) { if (pendingHeader) gap(body, 12); section(body, "已完成", done); doneHeader = true; }
            taskRow(body, item);
        }
        gap(body, 6);
        body.addView(text("轻点事项完成 · 长按事项编辑", 11, muted, false));
    }

    private void section(LinearLayout body, String label, int count) {
        LinearLayout line = row();
        TextView heading = text(label, 13, muted, true);
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        line.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(text(Integer.toString(count), 12, muted, false));
        body.addView(line);
        gap(body, 12);
    }

    private void taskRow(LinearLayout body, TodoStore.Item item) {
        LinearLayout tile = row();
        tile.setPadding(dp(6), dp(10), dp(4), dp(10));
        tile.setMinimumHeight(dp(82));
        tile.setBackground(ripple(surface, 20, true));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setContentDescription(item.title + (item.note.isEmpty() ? "" : "，" + item.note) + (item.done ? "，已完成" : "，未完成"));
        tile.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setCheckable(true);
                info.setChecked(item.done);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, "编辑事项"));
            }
        });
        tile.setOnLongClickListener(v -> { openEditor(false, item); return true; });
        Icon check = new Icon(item.done ? "checked" : "circle", item.done ? primary : muted);
        tile.setOnClickListener(v -> toggleTask(tile, check, item));
        check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout copy = column();
        copy.setPadding(dp(2), dp(3), dp(6), dp(3));
        TextView title = text(item.title, 16, item.done ? finished : ink, true);
        title.setMaxLines(4);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(dp(3), 1f);
        if (item.done) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        copy.addView(title);
        if (!item.note.isEmpty()) {
            gap(copy, 7);
            TextView note = text(item.note, 12, item.done ? finished : muted, false);
            note.setMaxLines(2);
            note.setEllipsize(TextUtils.TruncateAt.END);
            note.setLineSpacing(dp(2), 1f);
            copy.addView(note);
        }
        tile.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        Icon more = iconButton("more", muted, "更多操作：" + item.title);
        more.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, more);
            menu.getMenu().add("编辑事项");
            menu.getMenu().add("存为模板");
            menu.getMenu().add("删除事项");
            menu.setOnMenuItemClickListener(choice -> {
                String label = choice.getTitle().toString();
                if (label.equals("编辑事项")) openEditor(false, item);
                else if (label.equals("存为模板")) change(() -> store.addTemplate(item.title, item.note), "已保存到模板");
                else deleteTask(item);
                return true;
            });
            menu.show();
        });
        tile.addView(more);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        body.addView(tile, params);
    }

    private void renderTemplates(LinearLayout body) {
        List<TodoStore.Item> templates = store.templates();
        header(body, "模板", "常做的事，保存一次，下次直接套用。");
        LinearLayout hint = row();
        hint.setPadding(dp(16), dp(14), dp(16), dp(14));
        hint.setBackground(shape(soft, 20, false));
        Icon icon = new Icon("bolt", primary);
        hint.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView hintText = text("点「套用」，立即添加到待办清单", 13, primary, false);
        hintText.setPadding(dp(8), 0, 0, 0);
        hint.addView(hintText, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(hint);
        gap(body, 26);
        section(body, "我的模板", templates.size());
        if (templates.isEmpty()) {
            empty(body, "template", "把常做的事存起来", "自定义名称和备注，下次一键添加。", "新建模板", () -> openEditor(true, null));
            return;
        }
        for (TodoStore.Item item : templates) templateRow(body, item);
        gap(body, 4);
        TextView foot = text("模板可以反复套用，完成状态独立保存。", 11, muted, false);
        foot.setLineSpacing(dp(3), 1f);
        body.addView(foot);
    }

    private void templateRow(LinearLayout body, TodoStore.Item item) {
        LinearLayout tile = column();
        tile.setPadding(dp(18), dp(12), dp(14), dp(16));
        tile.setBackground(shape(surface, 20, true));
        LinearLayout top = row();
        Icon mark = new Icon("template", primary);
        mark.setBackground(shape(soft, 12, false));
        top.addView(mark, new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView title = text(item.title, 16, ink, true);
        title.setPadding(dp(12), 0, dp(4), 0);
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Icon more = iconButton("more", muted, "模板操作：" + item.title);
        more.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, more);
            menu.getMenu().add("编辑模板");
            menu.getMenu().add("删除模板");
            menu.setOnMenuItemClickListener(choice -> {
                if (choice.getTitle().toString().equals("编辑模板")) openEditor(true, item);
                else deleteTemplate(item);
                return true;
            });
            menu.show();
        });
        top.addView(more);
        tile.addView(top);
        if (!item.note.isEmpty()) {
            gap(tile, 6);
            TextView note = text(item.note, 13, muted, false);
            note.setMaxLines(3);
            note.setEllipsize(TextUtils.TruncateAt.END);
            note.setLineSpacing(dp(3), 1f);
            tile.addView(note);
        }
        gap(tile, 14);
        LinearLayout actions = row();
        TextView edit = button("编辑", Color.TRANSPARENT, muted);
        edit.setPadding(dp(8), dp(8), dp(8), dp(8));
        edit.setOnClickListener(v -> openEditor(true, item));
        actions.addView(edit);
        actions.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        TextView apply = button("＋  套用", soft, primary);
        apply.setContentDescription("套用模板：" + item.title);
        apply.setOnClickListener(v -> {
            try {
                store.addTask(item.title, item.note);
                snack("已添加「" + shortened(item.title) + "」", "查看", () -> showPendingTasks());
            } catch (RuntimeException error) { failure(error); }
        });
        actions.addView(apply);
        tile.addView(actions);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        body.addView(tile, params);
    }

    private void empty(LinearLayout body, String type, String title, String description, String action, Runnable callback) {
        LinearLayout area = column();
        area.setGravity(Gravity.CENTER_HORIZONTAL);
        area.setPadding(dp(12), dp(26), dp(12), dp(28));
        Icon illustration = new Icon(type, primary);
        illustration.setBackground(shape(soft, 30, false));
        area.addView(illustration, new LinearLayout.LayoutParams(dp(80), dp(80)));
        gap(area, 22);
        TextView heading = text(title, 18, ink, true);
        heading.setGravity(Gravity.CENTER);
        area.addView(heading);
        gap(area, 10);
        TextView sub = text(description, 13, muted, false);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(dp(4), 1f);
        area.addView(sub);
        if (action != null) {
            gap(area, 18);
            TextView link = button(action, Color.TRANSPARENT, primary);
            link.setOnClickListener(v -> callback.run());
            area.addView(link);
        }
        body.addView(area, new LinearLayout.LayoutParams(-1, -2));
    }

    private void renderNav() {
        bottomBar.removeAllViews();
        bottomBar.setPadding(dp(24), dp(8), dp(24), dp(10));
        bottomBar.setBackgroundColor(surface);
        String[] labels = {"待办", "模板"};
        String[] icons = {"list", "template"};
        for (int i = 0; i < 2; i++) {
            final int target = i;
            LinearLayout nav = column();
            nav.setGravity(Gravity.CENTER);
            nav.setClickable(true);
            nav.setFocusable(true);
            nav.setSelected(page == i);
            nav.setContentDescription(labels[i] + (page == i ? "，已选择" : ""));
            nav.setBackground(ripple(Color.TRANSPARENT, 24, false));
            FrameLayout capsule = new FrameLayout(this);
            capsule.setBackground(shape(page == i ? soft : Color.TRANSPARENT, 16, false));
            Icon icon = new Icon(icons[i], page == i ? primary : muted);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            capsule.addView(icon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            nav.addView(capsule, new LinearLayout.LayoutParams(dp(64), dp(32)));
            gap(nav, 5);
            TextView caption = text(labels[i], 12, page == i ? primary : muted, page == i);
            caption.setGravity(Gravity.CENTER);
            caption.setIncludeFontPadding(true);
            caption.setSingleLine(true);
            caption.setEllipsize(TextUtils.TruncateAt.END);
            caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            nav.addView(caption, new LinearLayout.LayoutParams(-1, -2));
            Paint.FontMetricsInt metrics = caption.getPaint().getFontMetricsInt();
            int textHeight = Math.max(caption.getLineHeight(), metrics.bottom - metrics.top);
            int height = Math.max(dp(76), dp(63) + textHeight);
            bottomBar.setLayoutParams(new LinearLayout.LayoutParams(-1, height));
            nav.setOnClickListener(v -> switchPage(target));
            bottomBar.addView(nav, new LinearLayout.LayoutParams(0, -1, 1));
            if (page == i && motionEnabled()) {
                capsule.setScaleX(.92f);
                capsule.setScaleY(.92f);
                capsule.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(new PathInterpolator(.2f, 0f, 0f, 1f)).start();
            }
        }
    }

    private void addFab() {
        actionDock.removeAllViews();
        actionDock.setBackgroundColor(bg);
        LinearLayout fab = row();
        fab.setGravity(Gravity.CENTER);
        fab.setPadding(dp(17), 0, dp(22), 0);
        fab.setBackground(ripple(primary, 18, false));
        fab.setClickable(true);
        fab.setFocusable(true);
        String label = page == 0 ? "添加事项" : "新建模板";
        fab.setContentDescription(label);
        Icon plus = new Icon("plus", onPrimary);
        fab.addView(plus, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView title = text(label, 15, onPrimary, true);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(7), 0, 0, 0);
        fab.addView(title);
        int buttonHeight = Math.max(dp(52), title.getLineHeight() + dp(24));
        actionDock.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonHeight + dp(16)));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, buttonHeight, Gravity.CENTER);
        params.setMargins(dp(24), dp(8), dp(24), dp(8));
        actionDock.addView(fab, params);
        fab.setOnClickListener(v -> openEditor(page == 1, null));
    }

    private Dialog sheet(LinearLayout body) {
        Dialog dialog = new Dialog(this);
        ScrollView container = new ScrollView(this);
        container.setFillViewport(false);
        container.setVerticalScrollBarEnabled(false);
        container.setBackground(shape(surface, 28, false));
        container.setClipToOutline(true);
        container.addView(body);
        dialog.setContentView(container);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.35f);
            w.setWindowAnimations(R.style.SheetMotion);
            w.setGravity(Gravity.BOTTOM);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            w.setNavigationBarColor(surface);
            if (Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);
            if (!dark) w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            if (Build.VERSION.SDK_INT >= 30) {
                w.getInsetsController().setSystemBarsAppearance(dark ? 0 : WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
            w.setLayout(getResources().getConfiguration().screenWidthDp > 640 ? dp(600) : -1, -2);
        }
        return dialog;
    }

    private LinearLayout sheetBody(String title, Runnable close) {
        LinearLayout body = column();
        body.setPadding(dp(24), dp(12), dp(24), dp(26));
        View handle = new View(this);
        handle.setBackground(shape(outline, 3, false));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(32), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        body.addView(handle, handleParams);
        gap(body, 12);
        LinearLayout head = row();
        TextView heading = text(title, 23, ink, true);
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        head.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Icon x = iconButton("close", muted, "关闭");
        x.setOnClickListener(v -> close.run());
        head.addView(x);
        body.addView(head);
        gap(body, 12);
        return body;
    }

    private EditText field(String hint, int maximum, boolean multiline) {
        EditText e = new EditText(this);
        e.setTextColor(ink);
        e.setHintTextColor(muted);
        e.setHint(hint);
        e.setTextSize(16);
        e.setPadding(dp(16), dp(14), dp(16), dp(14));
        e.setBackground(shape(bg, 14, true));
        e.setHighlightColor((primary & 0x00FFFFFF) | 0x44000000);
        if (Build.VERSION.SDK_INT >= 29) {
            GradientDrawable cursor = shape(primary, 1, false);
            cursor.setSize(dp(2), dp(24));
            e.setTextCursorDrawable(cursor);
        }
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maximum)});
        e.setInputType(InputType.TYPE_CLASS_TEXT | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES : InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setMinLines(multiline ? 2 : 1);
        e.setMaxLines(multiline ? 4 : 2);
        return e;
    }

    private void openEditor(boolean template, TodoStore.Item existing) {
        if (editor != null) editor.dismiss();
        editorIsTemplate = template;
        editorId = existing == null ? -1 : existing.id;
        editorSaveTemplate = null;
        String heading = template ? (existing == null ? "新建模板" : "编辑模板") : (existing == null ? "添加事项" : "编辑事项");
        LinearLayout body = sheetBody(heading, () -> { if (editor != null) editor.dismiss(); });
        editorTitle = field(template ? "比如：每晚练习日语" : "想做些什么？", 120, false);
        editorTitle.setContentDescription(template ? "模板名称" : "事项名称");
        editorNote = field("添加备注（可选）", 2000, true);
        editorNote.setContentDescription("备注");

        if (!template && existing == null && !store.templates().isEmpty()) {
            body.addView(text("从模板快速填写", 12, muted, true));
            gap(body, 10);
            HorizontalScrollView presetScroll = new HorizontalScrollView(this);
            presetScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout presets = row();
            for (TodoStore.Item preset : store.templates()) {
                TextView chip = button(shortened(preset.title), soft, primary);
                chip.setTextSize(12);
                chip.setMinHeight(dp(44));
                chip.setContentDescription("填写模板：" + preset.title);
                chip.setOnClickListener(v -> {
                    editorTitle.setText(preset.title);
                    editorNote.setText(preset.note);
                    editorTitle.setSelection(editorTitle.length());
                });
                chip.setPadding(dp(16), dp(8), dp(16), dp(8));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
                params.setMargins(0, 0, dp(8), 0);
                presets.addView(chip, params);
            }
            presetScroll.addView(presets);
            body.addView(presetScroll);
            gap(body, 22);
        }
        body.addView(text(template ? "模板名称" : "事项名称", 12, muted, true));
        gap(body, 9);
        body.addView(editorTitle, new LinearLayout.LayoutParams(-1, -2));
        gap(body, 17);
        body.addView(text("备注", 12, muted, true));
        gap(body, 9);
        body.addView(editorNote, new LinearLayout.LayoutParams(-1, -2));
        gap(body, 10);
        if (!template) {
            editorSaveTemplate = new CheckBox(this);
            editorSaveTemplate.setText("同时保存为模板，下次直接用");
            editorSaveTemplate.setTextColor(muted);
            editorSaveTemplate.setTextSize(13);
            editorSaveTemplate.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{primary, muted}));
            editorSaveTemplate.setMinHeight(dp(48));
            body.addView(editorSaveTemplate);
        } else {
            TextView tip = text("套用时会生成一个新的未完成事项。", 12, muted, false);
            tip.setPadding(0, dp(8), 0, dp(8));
            body.addView(tip);
        }
        gap(body, 16);
        TextView save = button(template ? "保存模板" : existing == null ? "添加到清单" : "保存修改", primary, onPrimary);
        save.setMinHeight(dp(52));
        body.addView(save, new LinearLayout.LayoutParams(-1, -2));
        if (existing != null) {
            editorTitle.setText(existing.title);
            editorNote.setText(existing.note);
        }
        save.setOnClickListener(v -> {
            if (!save.isEnabled() || editor == null || editorTitle == null || editorNote == null) return;
            String title = TodoStore.cleanText(editorTitle.getText().toString());
            String note = TodoStore.cleanText(editorNote.getText().toString());
            if (!TodoStore.hasTitleContent(title)) {
                editorTitle.setError("请输入" + (template ? "模板" : "事项") + "名称");
                editorTitle.requestFocus();
                return;
            }
            save.setEnabled(false);
            try {
                android.database.sqlite.SQLiteDatabase db = store.getWritableDatabase();
                db.beginTransaction();
                try {
                    if (template) {
                        if (existing == null) store.addTemplate(title, note); else store.updateTemplate(existing.id, title, note);
                    } else {
                        if (existing == null) store.addTask(title, note); else store.updateTask(existing.id, title, note);
                        if (editorSaveTemplate.isChecked()) store.addTemplate(title, note);
                    }
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
                Dialog activeEditor = editor;
                activeEditor.dismiss();
                if (!template && existing == null && filter == 2) filter = 1;
                if (existing == null) scrollPositions[page] = 0;
                render(existing != null, 2);
                snack(template ? "模板已保存" : existing == null ? "已添加到清单" : "修改已保存", null, null);
            } catch (RuntimeException error) { save.setEnabled(true); failure(error); }
        });
        final Dialog createdEditor = sheet(body);
        editor = createdEditor;
        createdEditor.setOnDismissListener(dialog -> {
            if (editor == createdEditor) {
                editor = null;
                editorTitle = null;
                editorNote = null;
                editorSaveTemplate = null;
            }
        });
        editor.show();
        Window w = editor.getWindow();
        if (w != null) w.setLayout(getResources().getConfiguration().screenWidthDp > 640 ? dp(600) : -1, -2);
    }

    private void applyTheme(ColorTheme selected) {
        if (colorTheme == selected) return;
        getSharedPreferences("appearance", MODE_PRIVATE).edit().putString("color_theme", selected.id).apply();
        palette();
        host.setBackgroundColor(bg);
        root.setBackgroundColor(bg);
        getWindow().setBackgroundDrawable(shape(bg, 0, false));
        getWindow().setNavigationBarColor(bg);
        render(true, 2);
        if (pendingUndo != null || pendingUndoCompleted) presentUndo();
        else if (snackView != null && snackExpiresAt > SystemClock.elapsedRealtime()) {
            int destination = snackDestination;
            showSnack(snackMessage, snackAction, snackCallback, snackExpiresAt - SystemClock.elapsedRealtime());
            snackDestination = destination;
        }
        if (themeDialog != null && themeDialog.isShowing()) {
            themeScroll.setBackground(shape(surface, 28, false));
            themeScroll.removeAllViews();
            themeScroll.addView(themeBody());
            themeDialog.getWindow().setNavigationBarColor(surface);
        }
    }

    private LinearLayout themeBody() {
        LinearLayout body = sheetBody("颜色主题", () -> { if (themeDialog != null) themeDialog.dismiss(); });
        TextView hint = text("换个喜欢的颜色，给清单一点新心情。\n浅色和深色会跟随系统切换。", 13, muted, false);
        hint.setLineSpacing(dp(4), 1f);
        body.addView(hint);
        gap(body, 20);
        ColorTheme[] choices = ColorTheme.values();
        for (int i = 0; i < choices.length; i += 2) {
            LinearLayout pair = row();
            for (int j = 0; j < 2; j++) {
                final ColorTheme choice = choices[i + j];
                final boolean selected = choice == colorTheme;
                LinearLayout tile = column();
                tile.setGravity(Gravity.CENTER);
                tile.setPadding(dp(10), dp(14), dp(10), dp(14));
                GradientDrawable fill = shape(selected ? soft : bg, 18, true);
                if (selected) fill.setStroke(dp(2), primary);
                tile.setBackground(new RippleDrawable(ColorStateList.valueOf((primary & 0x00FFFFFF) | 0x22000000), fill, shape(Color.WHITE, 18, false)));
                tile.setClickable(true);
                tile.setFocusable(true);
                tile.setSelected(selected);
                tile.setContentDescription("选择主题：" + choice.label);
                tile.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                    @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        info.setClassName("android.widget.RadioButton");
                        info.setCheckable(true);
                        info.setChecked(selected);
                    }
                });
                FrameLayout swatch = new FrameLayout(this);
                swatch.setBackground(shape(choice.primary(dark), 100, false));
                swatch.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                if (selected) swatch.addView(new Icon("tick", choice.onPrimary(dark)), new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER));
                tile.addView(swatch, new LinearLayout.LayoutParams(dp(38), dp(38)));
                gap(tile, 9);
                TextView label = text(choice.label, 14, selected ? primary : ink, selected);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(1);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                tile.addView(label, new LinearLayout.LayoutParams(-1, -2));
                tile.setOnClickListener(v -> { if (themeDialog != null && themeDialog.isShowing()) applyTheme(choice); });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                params.setMargins(j == 0 ? 0 : dp(6), 0, j == 0 ? dp(6) : 0, 0);
                pair.addView(tile, params);
            }
            body.addView(pair);
            gap(body, 12);
        }
        gap(body, 4);
        TextView done = button("完成", soft, primary);
        done.setOnClickListener(v -> { if (themeDialog != null) themeDialog.dismiss(); });
        body.addView(done, new LinearLayout.LayoutParams(-1, -2));
        return body;
    }

    private void showThemes() {
        if (themeDialog != null && themeDialog.isShowing()) return;
        LinearLayout body = themeBody();
        final Dialog picker = sheet(body);
        themeScroll = (ScrollView) body.getParent();
        themeDialog = picker;
        picker.setOnDismissListener(dialog -> { if (themeDialog == picker) { themeDialog = null; themeScroll = null; } });
        picker.show();
        picker.getWindow().setLayout(getResources().getConfiguration().screenWidthDp > 640 ? dp(600) : -1, -2);
    }

    private void showAbout() {
        if (aboutDialog != null && aboutDialog.isShowing()) return;
        final Dialog[] holder = new Dialog[1];
        LinearLayout body = sheetBody("轻待办", () -> holder[0].dismiss());
        body.addView(text("少一点负担，多一点完成。", 16, ink, true));
        gap(body, 15);
        TextView details = text("• 点击事项或勾选图标，切换完成状态\n• 长按事项，或点击右侧 ⋮ 编辑、存模板、删除\n• 在「已完成」里一键清除，提示条支持撤销\n• 删除后可在提示条中撤销\n• 模板可随时编辑、删除和反复套用\n• 顶部调色盘可以切换颜色主题\n\n数据保存在当前手机，无需登录，离线可用。\n卸载应用或清除应用数据会删除清单和模板。\n\n版本 1.3.0", 14, muted, false);
        details.setLineSpacing(dp(6), 1f);
        body.addView(details);
        gap(body, 22);
        TextView ok = button("知道啦", soft, primary);
        ok.setOnClickListener(v -> holder[0].dismiss());
        body.addView(ok, new LinearLayout.LayoutParams(-1, -2));
        holder[0] = sheet(body);
        aboutDialog = holder[0];
        holder[0].setOnDismissListener(dialog -> { if (aboutDialog == holder[0]) aboutDialog = null; });
        holder[0].show();
        Window w = holder[0].getWindow();
        if (w != null) w.setLayout(getResources().getConfiguration().screenWidthDp > 640 ? dp(600) : -1, -2);
    }

    private void change(Runnable action, String message) {
        try {
            action.run();
            render(true, 2);
            if (message != null) snack(message, null, null);
        } catch (RuntimeException error) { failure(error); }
    }
    private void failure(RuntimeException error) { Toast.makeText(this, "保存失败，请检查手机存储空间后重试。", Toast.LENGTH_LONG).show(); }
    private String shortened(String title) {
        android.icu.text.BreakIterator boundaries = android.icu.text.BreakIterator.getCharacterInstance(Locale.getDefault());
        boundaries.setText(title);
        int end = boundaries.first();
        for (int i = 0; i < 14; i++) {
            int next = boundaries.next();
            if (next == android.icu.text.BreakIterator.DONE) return title;
            end = next;
        }
        return end < title.length() ? title.substring(0, end) + "…" : title;
    }

    private void toggleTask(LinearLayout tile, Icon check, TodoStore.Item item) {
        if (!tile.isEnabled()) return;
        try {
            store.setDone(item.id, !item.done);
            if (!motionEnabled()) { render(true); return; }
            final FrameLayout capturedPage = currentPage;
            disableTree(tile);
            check.type = item.done ? "circle" : "checked";
            check.color = item.done ? muted : primary;
            check.invalidate();
            check.setScaleX(1.18f);
            check.setScaleY(1.18f);
            check.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(new PathInterpolator(.2f, 0f, 0f, 1f)).start();
            tile.animate().alpha(.55f).setDuration(160).start();
            handler.postDelayed(() -> { if (page == 0 && currentPage == capturedPage && !isFinishing()) render(true, 2); }, 160);
        } catch (RuntimeException error) { failure(error); }
    }
    private void deleteTask(TodoStore.Item item) {
        try {
            store.deleteTask(item.id);
            render(true, 2);
            prepareUndo(item, false);
        } catch (RuntimeException error) { failure(error); }
    }
    private void deleteTemplate(TodoStore.Item item) {
        try {
            store.deleteTemplate(item.id);
            render(true, 2);
            prepareUndo(item, true);
        } catch (RuntimeException error) { failure(error); }
    }
    private void clearCompleted() {
        try {
            int count = store.clearCompleted();
            if (count == 0) { render(true); return; }
            // The transaction replaced the old batch snapshot; keep the new one.
            pendingUndoCompleted = false;
            dismissSnack();
            pendingUndoCompleted = true;
            pendingUndoCompletedCount = count;
            undoExpiresAt = SystemClock.elapsedRealtime() + recommendedTimeout(10000, true);
            render(true, 2);
            presentUndo();
        } catch (RuntimeException error) { render(true); failure(error); }
    }
    private void dismissSnack() { dismissSnack(true); }
    private void dismissSnack(boolean discardBatch) {
        if (pendingUndoCompleted && discardBatch) store.discardCompletedUndo();
        pendingUndo = null;
        pendingUndoCompleted = false;
        pendingUndoCompletedCount = 0;
        undoExpiresAt = 0;
        removeSnackView();
    }
    private void removeSnackView() {
        if (snackTimeout != null) handler.removeCallbacks(snackTimeout);
        View previous = snackView;
        snackView = null;
        snackTimeout = null;
        snackMessage = null;
        snackAction = null;
        snackCallback = null;
        snackExpiresAt = 0;
        snackDestination = 0;
        if (previous != null) {
            previous.animate().cancel();
            host.removeView(previous);
        }
    }
    private void snack(String message, String action, Runnable callback) {
        dismissSnack();
        showSnack(message, action, callback, recommendedTimeout(action == null ? 3000 : 10000, action != null));
        snackDestination = "查看".equals(action) ? 1 : 0;
    }
    private int recommendedTimeout(int duration, boolean controls) {
        if (Build.VERSION.SDK_INT >= 29) {
            AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
            if (manager != null) return manager.getRecommendedTimeoutMillis(duration, AccessibilityManager.FLAG_CONTENT_TEXT | (controls ? AccessibilityManager.FLAG_CONTENT_CONTROLS : 0));
        }
        return duration;
    }
    private void prepareUndo(TodoStore.Item item, boolean template) {
        dismissSnack();
        pendingUndo = item;
        pendingUndoTemplate = template;
        undoExpiresAt = SystemClock.elapsedRealtime() + recommendedTimeout(10000, true);
        presentUndo();
    }
    private void presentUndo() {
        if (pendingUndoCompleted) {
            if (pendingUndoCompletedCount == 0 || undoExpiresAt <= SystemClock.elapsedRealtime()) { dismissSnack(); return; }
            final int count = pendingUndoCompletedCount;
            showSnack("已清除 " + count + " 件已完成事项", "撤销", () -> {
                change(() -> store.restoreCompleted(), "已恢复 " + count + " 件事项");
            }, undoExpiresAt - SystemClock.elapsedRealtime());
            return;
        }
        final TodoStore.Item item = pendingUndo;
        final boolean template = pendingUndoTemplate;
        if (item == null || undoExpiresAt <= SystemClock.elapsedRealtime()) { dismissSnack(); return; }
        showSnack(template ? "模板已删除" : "已删除「" + shortened(item.title) + "」", "撤销", () -> {
            change(() -> { if (template) store.restoreTemplate(item); else store.restoreTask(item); }, template ? "已恢复模板" : "已恢复事项");
        }, undoExpiresAt - SystemClock.elapsedRealtime());
    }
    private void positionSnack() {
        if (snackView == null || actionDock == null || bottomBar == null) return;
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) snackView.getLayoutParams();
        p.setMargins(root.getPaddingLeft() + dp(20), 0, root.getPaddingRight() + dp(20), bottomInset + actionDock.getLayoutParams().height + bottomBar.getLayoutParams().height + dp(8));
        snackView.setLayoutParams(p);
    }
    private void showSnack(String message, String action, Runnable callback, long timeout) {
        removeSnackView();
        LinearLayout bar = row();
        bar.setPadding(dp(16), dp(8), dp(8), dp(8));
        bar.setBackground(shape(colorTheme.snackBackground(dark), 16, false));
        bar.setElevation(dp(4));
        TextView copy = text(message, 13, colorTheme.snackInk(dark), false);
        copy.setMaxLines(2);
        copy.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        bar.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) {
            TextView link = button(action, Color.TRANSPARENT, colorTheme.snackAction(dark));
            link.setOnClickListener(v -> {
                if (snackView != bar || !link.isEnabled()) return;
                link.setEnabled(false);
                dismissSnack(false);
                callback.run();
            });
            bar.addView(link);
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        params.setMargins(dp(20), 0, dp(20), 0);
        host.addView(bar, params);
        snackView = bar;
        snackMessage = message;
        snackAction = action;
        snackCallback = callback;
        snackExpiresAt = SystemClock.elapsedRealtime() + Math.max(1, timeout);
        positionSnack();
        if (motionEnabled()) {
            bar.setAlpha(0f);
            bar.setTranslationY(dp(12));
            bar.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(new PathInterpolator(.2f, 0f, 0f, 1f)).start();
        }
        snackTimeout = () -> {
            if (snackView == bar) {
                dismissSnack();
            }
        };
        handler.postDelayed(snackTimeout, Math.max(1, timeout));
    }

    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("page", page);
        state.putInt("filter", filter);
        if (scroll != null) scrollPositions[page] = scroll.getScrollY();
        state.putInt("scroll_tasks", scrollPositions[0]);
        state.putInt("scroll_templates", scrollPositions[1]);
        state.putBoolean("about_open", aboutDialog != null && aboutDialog.isShowing());
        state.putBoolean("themes_open", themeDialog != null && themeDialog.isShowing());
        if (pendingUndoCompleted && undoExpiresAt > SystemClock.elapsedRealtime()) {
            state.putBoolean("undo_completed", true);
            state.putLong("undo_expires", undoExpiresAt);
        } else if (pendingUndo != null && undoExpiresAt > SystemClock.elapsedRealtime()) {
            state.putLong("undo_expires", undoExpiresAt);
            state.putLong("undo_id", pendingUndo.id);
            state.putString("undo_title", pendingUndo.title);
            state.putString("undo_note", pendingUndo.note);
            state.putBoolean("undo_done", pendingUndo.done);
            state.putLong("undo_created", pendingUndo.created);
            state.putBoolean("undo_template", pendingUndoTemplate);
        } else if (snackView != null && snackExpiresAt > SystemClock.elapsedRealtime()
                && (snackCallback == null || snackDestination == 1)) {
            state.putString("snack_message", snackMessage);
            state.putLong("snack_expires", snackExpiresAt);
            state.putInt("snack_destination", snackDestination);
        }
        state.putBoolean("editor_open", editor != null && editor.isShowing());
        if (editor != null && editorTitle != null && editorNote != null) {
            state.putBoolean("editor_template", editorIsTemplate);
            state.putLong("editor_id", editorId);
            state.putString("draft_title", editorTitle.getText().toString());
            state.putString("draft_note", editorNote.getText().toString());
            state.putInt("draft_title_start", editorTitle.getSelectionStart());
            state.putInt("draft_title_end", editorTitle.getSelectionEnd());
            state.putInt("draft_note_start", editorNote.getSelectionStart());
            state.putInt("draft_note_end", editorNote.getSelectionEnd());
            state.putBoolean("draft_note_focus", editorNote.hasFocus());
            state.putBoolean("draft_template", editorSaveTemplate != null && editorSaveTemplate.isChecked());
        }
    }
    @Override public void onBackPressed() {
        if (page == 1) switchPage(0); else super.onBackPressed();
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (currentPage != null) currentPage.animate().cancel();
        if (snackView != null) snackView.animate().cancel();
        if (editor != null) editor.dismiss();
        if (aboutDialog != null) aboutDialog.dismiss();
        if (themeDialog != null) themeDialog.dismiss();
        if (store != null) store.close();
        super.onDestroy();
    }

    /** Vector strokes rendered at native resolution, with 48dp controls around them. */
    private final class Icon extends View {
        private String type;
        private int color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(String type, int color) { super(MainActivity.this); this.type = type; this.color = color; }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight()) * (getWidth() >= dp(70) ? .45f : .52f);
            canvas.save();
            canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            canvas.scale(size / 24f, size / 24f);
            paint.setColor(color);
            paint.setStrokeWidth(1.8f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            if (type.equals("plus")) {
                canvas.drawLine(12, 4, 12, 20, paint); canvas.drawLine(4, 12, 20, 12, paint);
            } else if (type.equals("close")) {
                canvas.drawLine(6, 6, 18, 18, paint); canvas.drawLine(18, 6, 6, 18, paint);
            } else if (type.equals("circle")) {
                paint.setColor(ColorTheme.blend(muted, surface, .18f));
                canvas.drawCircle(12, 12, 8.5f, paint);
            } else if (type.equals("checked")) {
                paint.setStyle(Paint.Style.FILL); canvas.drawCircle(12, 12, 9, paint);
                paint.setStyle(Paint.Style.STROKE); paint.setColor(onPrimary);
                path(canvas, 7.5f, 12, 10.5f, 15, 16.5f, 9);
            } else if (type.equals("check") || type.equals("celebrate")) {
                canvas.drawCircle(12, 12, 9, paint); path(canvas, 7, 12, 10.5f, 15.5f, 17, 9);
            } else if (type.equals("tick")) {
                path(canvas, 5, 12, 10, 17, 19, 7);
            } else if (type.equals("more")) {
                paint.setStyle(Paint.Style.FILL); canvas.drawCircle(12, 5, 1.5f, paint); canvas.drawCircle(12, 12, 1.5f, paint); canvas.drawCircle(12, 19, 1.5f, paint);
            } else if (type.equals("info")) {
                canvas.drawCircle(12, 12, 8.5f, paint); canvas.drawLine(12, 11, 12, 16, paint);
                paint.setStyle(Paint.Style.FILL); canvas.drawCircle(12, 7.8f, 1, paint);
            } else if (type.equals("palette")) {
                canvas.drawCircle(12, 12, 9, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(8, 8, 1.5f, paint); canvas.drawCircle(15, 7, 1.5f, paint);
                canvas.drawCircle(17, 13, 1.5f, paint); canvas.drawCircle(8, 15, 1.5f, paint);
            } else if (type.equals("template")) {
                canvas.drawRoundRect(4, 3, 17, 18, 2, 2, paint); path(canvas, 18, 7, 21, 7, 21, 21, 8, 21);
                canvas.drawLine(8, 8, 13, 8, paint); canvas.drawLine(8, 12, 13, 12, paint);
            } else if (type.equals("bolt")) {
                path(canvas, 14, 2, 5, 14, 11, 14, 10, 22, 19, 10, 13, 10, 14, 2);
            } else {
                canvas.drawRoundRect(3, 3, 21, 21, 3, 3, paint);
                path(canvas, 6.5f, 9, 8, 10.5f, 10.5f, 7.5f);
                path(canvas, 6.5f, 16, 8, 17.5f, 10.5f, 14.5f);
                canvas.drawLine(13, 9, 17.5f, 9, paint); canvas.drawLine(13, 16, 17.5f, 16, paint);
            }
            canvas.restore();
        }
        private void path(Canvas canvas, float... points) {
            Path p = new Path(); p.moveTo(points[0], points[1]);
            for (int i = 2; i < points.length; i += 2) p.lineTo(points[i], points[i + 1]);
            canvas.drawPath(p, paint);
        }
    }
}
