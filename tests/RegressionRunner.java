package com.qingtodo.acceptance;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.qingtodo.app.MainActivity;
import com.qingtodo.app.TodoStore;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Regression checks for actual text geometry, safe navigation and dialog lifecycle. */
public final class RegressionRunner extends Instrumentation {
    private Activity activity;
    private TodoStore store;
    private boolean large;
    private final List<String> passed = new ArrayList<>();
    @Override public void onCreate(Bundle args) { super.onCreate(args); large = "true".equals(args.getString("large", "false")); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            store = new TodoStore(getTargetContext());
            activity = startActivitySync(new Intent().setClassName(getTargetContext(), "com.qingtodo.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();
            onUi(() -> { checkNav("待办，已选择", "待办"); checkNav("模板", "模板"); });
            verify(true, "home bottom-navigation glyphs share their icon centers");
            onUi(() -> checkDock("添加事项"));
            verify(true, "add action occupies its own region outside the task viewport");
            clickDesc("模板");
            onUi(() -> { checkNav("模板，已选择", "模板"); checkNav("待办", "待办"); checkDock("新建模板"); });
            verify(true, "template navigation and action remain centered and never overlap list controls");
            if (large) {
                verify(activity.getResources().getConfiguration().fontScale >= 1.45f, "the enlarged-font check actually runs at 1.5x font scale");
                verify(true, "geometry also passes with the device's enlarged font scale");
            } else {
                clickDesc("待办");
                clickDesc("添加事项");
                field("事项名称", "防重复保存验证");
                int count = store.tasks().size();
                onUi(() -> { View save = findText(root(), "添加到清单"); save.performClick(); save.performClick(); });
                settle();
                verify(store.tasks().size() == count + 1, "double-tapping save creates exactly one record");

                clickDesc("添加事项");
                field("事项名称", "旋转后的草稿");
                field("备注", "这段还没有保存的文字需要保留");
                onUi(() -> ((CheckBox) findText(root(), "同时保存为模板，下次直接用")).setChecked(true));
                recreate();
                verify(dialog() != null && textOf("事项名称").equals("旋转后的草稿") && textOf("备注").equals("这段还没有保存的文字需要保留"), "activity recreation keeps the open editor and its unsaved fields");
                verify(((CheckBox) findText(root(), "同时保存为模板，下次直接用")).isChecked(), "activity recreation also preserves the save-template choice");
                clickDesc("关闭");

                onUi(() -> {
                    try {
                        Method open = MainActivity.class.getDeclaredMethod("openEditor", boolean.class, TodoStore.Item.class); open.setAccessible(true);
                        open.invoke(activity, false, null);
                        open.invoke(activity, true, null);
                    } catch (Exception e) { throw new RuntimeException(e); }
                });
                settle();
                verify(dialog() != null && findDesc(root(), "模板名称") != null, "a dismissed editor cannot clear the references of a newly opened editor");
                field("模板名称", "弹窗替换验证");
                clickText("保存模板");
                boolean saved = false; for (TodoStore.Item t : store.templates()) if (t.title.equals("弹窗替换验证")) saved = true;
                verify(saved, "the replacement editor remains fully usable");

                onUi(() -> {
                    try {
                        Method shorten = MainActivity.class.getDeclaredMethod("shortened", String.class); shorten.setAccessible(true);
                        String value = (String) shorten.invoke(activity, "aaaaaaaaaaaaa🙂🙂🙂");
                        if (!value.equals("aaaaaaaaaaaaa🙂…")) throw new AssertionError("Emoji split in half: " + value);
                    } catch (Exception e) { throw new RuntimeException(e); }
                });
                verify(true, "long titles are shortened without splitting emoji surrogate pairs");

                onUi(() -> invokeInt("switchFilter", 2));
                clickDesc("模板");
                clickDesc("套用模板：弹窗替换验证");
                clickDesc("待办");
                clickText("查看");
                verify(((Integer) privateField("filter")) == 1 && findText(root(), "弹窗替换验证") != null,
                    "template View action refreshes the pending filter even after manually navigating home");
                onUi(() -> invokeInt("switchFilter", 0));

                clickDesc("更多操作：防重复保存验证");
                popup("删除事项");
                clickDesc("模板");
                verify(findText(root(), "撤销") != null, "delete undo survives switching to another page");
                clickText("撤销");
                verify(hasTask("防重复保存验证"), "undo can restore a task while viewing the template page");

                clickDesc("待办");
                clickDesc("更多操作：防重复保存验证");
                popup("删除事项");
                recreate();
                verify(findText(root(), "撤销") != null, "undo survives activity recreation until its original deadline");
                clickText("撤销");
                verify(hasTask("防重复保存验证"), "the restored undo action keeps the original task data");

                // Execute with animations enabled, then rapidly interrupt both directions.
                shell("settings put global animator_duration_scale 1");
                shell("settings put global transition_animation_scale 1");
                SystemClock.sleep(800);
                verify(android.animation.ValueAnimator.areAnimatorsEnabled(), "native property animations are enabled during the interruption check");
                onUi(() -> {
                    View more = findDesc(root(), "更多操作：防重复保存验证");
                    View tile = (View) more.getParent();
                    tile.performClick();
                    if (tile.isEnabled() || more.isEnabled()) throw new AssertionError("Stale task controls remain usable during completion animation");
                    tile.performClick();
                });
                waitForAnimations();
                int marked = 0; for (TodoStore.Item item : store.tasks()) if (item.title.equals("防重复保存验证") && item.done) marked++;
                verify(marked == 1, "completion animation prevents duplicate toggles and stale menu actions");
                onUi(() -> {
                    findDesc(root(), "模板").performClick();
                    FrameLayout incoming = (FrameLayout) privateField("currentPage");
                    if (incoming.getAlpha() != 0f || incoming.getTranslationX() <= 0f || ((FrameLayout) privateField("content")).getChildCount() != 2)
                        throw new AssertionError("Forward transition did not begin with a sliding, fading incoming page");
                });
                waitForAnimations();
                verify(true, "enabled page transitions animate translation and opacity before settling");
                clickDesc("待办");
                waitForAnimations();
                onUi(() -> {
                    for (int i = 0; i < 16; i++) {
                        View nav = findDesc(root(), i % 2 == 0 ? "模板" : "待办");
                        if (nav == null) throw new AssertionError("Navigation lost during transition");
                        nav.performClick();
                    }
                });
                waitForAnimations();
                FrameLayout pages = (FrameLayout) privateField("content");
                FrameLayout current = (FrameLayout) privateField("currentPage");
                verify(pages.getChildCount() == 1 && current.getAlpha() > .99f && Math.abs(current.getTranslationX()) < .5f, "rapidly interrupted page transitions leave exactly one visible, fully opaque page");
                verify(findText(root(), "待办") != null && findDesc(root(), "添加事项") != null, "the final page and action agree after rapid switching");
                shell("settings put global animator_duration_scale 0");
                shell("settings put global transition_animation_scale 0");
            }
            if (!large) {
                for (TodoStore.Item t : store.tasks()) if (t.title.equals("防重复保存验证") || t.title.equals("弹窗替换验证")) store.deleteTask(t.id);
                for (TodoStore.Item t : store.templates()) if (t.title.equals("弹窗替换验证")) store.deleteTemplate(t.id);
            }
            result.putString("stream", "\nPASS " + passed.size() + (large ? " enlarged-font geometry checks\n" : " regression checks\n") + String.join("\n", passed) + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("stream", "FAIL after " + passed.size() + " checks: " + android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED, result);
        } finally { if (store != null) store.close(); }
    }
    private void verify(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        passed.add("PASS: " + message);
        Bundle status = new Bundle(); status.putString("stream", "\nPASS: " + message); sendStatus(0, status);
    }
    private boolean hasTask(String title) { for (TodoStore.Item t : store.tasks()) if (t.title.equals(title)) return true; return false; }
    private void checkNav(String description, String label) {
        ViewGroup nav = (ViewGroup) findDesc(root(), description);
        TextView caption = (TextView) findText(nav, label);
        View pill = nav.getChildAt(0);
        int[] a = new int[2], b = new int[2]; caption.getLocationOnScreen(a); pill.getLocationOnScreen(b);
        if (caption.getLayout() == null) throw new AssertionError("Caption not laid out");
        // Single-line TextView uses a wide scrolling Layout and compensates with scrollX.
        float textCenter = a[0] + caption.getCompoundPaddingLeft() - caption.getScrollX() + (caption.getLayout().getLineLeft(0) + caption.getLayout().getLineRight(0)) / 2f;
        float iconCenter = b[0] + pill.getWidth() / 2f;
        if (Math.abs(textCenter - iconCenter) > 2f || (caption.getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK) != Gravity.CENTER_HORIZONTAL) throw new AssertionError("Bottom text and icon misaligned: " + label + " " + textCenter + " / " + iconCenter);
        if (a[1] < b[1] + pill.getHeight() || caption.getHeight() < caption.getLayout().getHeight() + caption.getCompoundPaddingTop() + caption.getCompoundPaddingBottom()) throw new AssertionError("Caption overlaps or clips: " + label);
    }
    private void checkDock(String description) {
        View button = findDesc(root(), description);
        View viewport = (View) privateField("content");
        Rect a = new Rect(), b = new Rect(); button.getGlobalVisibleRect(a); viewport.getGlobalVisibleRect(b);
        if (a.isEmpty() || b.bottom > a.top) throw new AssertionError("Action overlaps scroll viewport");
        ViewGroup dockButton = (ViewGroup) button;
        if (dockButton.getBaseline() != -1 || ((android.widget.LinearLayout) dockButton).isBaselineAligned()) throw new AssertionError("Icon/text baseline alignment unexpectedly active");
    }
    private Object privateField(String name) {
        try { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private void invokeInt(String name, int value) {
        try { Method m = MainActivity.class.getDeclaredMethod(name, int.class); m.setAccessible(true); m.invoke(activity, value); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private Dialog dialog() { return (Dialog) privateField("editor"); }
    private View root() { Dialog d = dialog(); return d != null ? d.getWindow().getDecorView() : activity.getWindow().getDecorView(); }
    private void onUi(Runnable r) {
        final Throwable[] failure = {null};
        runOnMainSync(() -> { try { r.run(); } catch (Throwable e) { failure[0] = e; } });
        waitForIdleSync();
        if (failure[0] != null) throw new AssertionError("UI check failed", failure[0]);
    }
    private void settle() { SystemClock.sleep(400); waitForIdleSync(); }
    private void clickDesc(String label) { onUi(() -> { View v = findDesc(root(), label); if (v == null) throw new AssertionError("Missing: " + label); v.performClick(); }); settle(); }
    private void clickText(String label) { onUi(() -> { View v = findText(root(), label); if (v == null) throw new AssertionError("Missing: " + label); v.performClick(); }); settle(); }
    private void field(String name, String value) { onUi(() -> ((EditText) findDesc(root(), name)).setText(value)); }
    private String textOf(String name) { return ((EditText) findDesc(root(), name)).getText().toString(); }
    private View findDesc(View v, String value) {
        if (v == null) return null;
        if (value.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription())) return v;
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { View f = findDesc(g.getChildAt(i), value); if (f != null) return f; } }
        return null;
    }
    private View findText(View v, String value) {
        if (v == null) return null;
        if (v instanceof TextView && value.contentEquals(((TextView) v).getText())) return v;
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { View f = findText(g.getChildAt(i), value); if (f != null) return f; } }
        return null;
    }
    private void recreate() {
        ActivityMonitor monitor = addMonitor("com.qingtodo.app.MainActivity", null, false);
        onUi(() -> activity.recreate());
        Activity recreated = monitor.waitForActivityWithTimeout(15000);
        removeMonitor(monitor);
        if (recreated == null) throw new AssertionError("Activity recreation timed out");
        activity = recreated; settle();
    }
    private void popup(String label) {
        final boolean[] clicked = {false};
        onUi(() -> {
            for (View window : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                View node = findText(window, label);
                if (node == null) continue;
                while (node.getParent() instanceof View) {
                    if (node.getParent() instanceof android.widget.AdapterView) {
                        android.widget.AdapterView<?> list = (android.widget.AdapterView<?>) node.getParent();
                        int pos = list.getPositionForView(node);
                        clicked[0] = list.performItemClick(node, pos, list.getItemIdAtPosition(pos)); return;
                    }
                    node = (View) node.getParent();
                }
            }
        });
        if (!clicked[0]) throw new AssertionError("Popup missing: " + label);
        settle();
    }
    private void waitForAnimations() {
        long deadline = SystemClock.uptimeMillis() + 12000;
        while (SystemClock.uptimeMillis() < deadline) {
            settle();
            FrameLayout current = (FrameLayout) privateField("currentPage");
            if (current.getAlpha() > .99f && ((FrameLayout) privateField("content")).getChildCount() == 1) return;
        }
        throw new AssertionError("Animation did not finish");
    }
    private void shell(String command) throws Exception {
        android.os.ParcelFileDescriptor fd = getUiAutomation().executeShellCommand(command);
        try (java.io.InputStream in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) { byte[] b = new byte[1024]; while (in.read(b) != -1) { } }
    }
}
