package com.qingtodo.acceptance;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import com.qingtodo.app.MainActivity;
import com.qingtodo.app.TodoStore;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** End-to-end acceptance checks executed on a real Android runtime. */
public final class AcceptanceRunner extends Instrumentation {
    private Activity activity;
    private TodoStore store;
    private final List<String> passed = new ArrayList<>();
    private static final String TITLE = "学习日语 30 分钟";
    private static final String NOTE = "复习 N4 语法，做一篇短阅读";

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            context.deleteDatabase("qingtodo.db");
            store = new TodoStore(context);
            activity = startActivitySync(new Intent().setClassName(context, "com.qingtodo.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();
            verify(store.tasks().isEmpty() && store.templates().size() == 3, "first launch: empty tasks and three removable starter templates");
            verify(hasText("清单还是空的"), "empty state is visible");

            clickDescription("添加事项");
            clickText("添加到清单");
            verify(dialog() != null && store.tasks().isEmpty(), "blank titles are rejected");
            setField("事项名称", TITLE);
            setField("备注", NOTE);
            onUi(() -> ((CheckBox) findText(root(), "同时保存为模板，下次直接用")).setChecked(true));
            clickText("添加到清单");
            verify(store.tasks().size() == 1 && store.templates().size() == 4, "adding a task can simultaneously save its custom template");
            verify(hasText(TITLE) && hasText(NOTE), "task title and note render on the home page");

            clickDescription(TITLE + "，" + NOTE + "，未完成");
            verify(store.tasks().get(0).done, "tap task toggles completion on");
            clickText("已完成  1");
            verify(hasText(TITLE), "completed filter shows completed tasks");
            clickDescription(TITLE + "，" + NOTE + "，已完成");
            verify(!store.tasks().get(0).done && !hasText(TITLE), "unchecking completion immediately updates the active filter");
            clickText("未完成  1");
            verify(hasText(TITLE), "pending filter shows unchecked tasks");

            onUi(() -> findDescription(root(), TITLE + "，" + NOTE + "，未完成").performLongClick());
            settle();
            setField("事项名称", "学习日语 40 分钟");
            clickText("保存修改");
            verify(store.tasks().get(0).title.equals("学习日语 40 分钟") && store.templates().get(3).title.equals(TITLE), "editing a task leaves its saved template independent");
            clickDescription("更多操作：学习日语 40 分钟");
            clickPopup("删除事项");
            verify(store.tasks().isEmpty(), "task delete removes the record");
            clickText("撤销");
            verify(store.tasks().size() == 1 && store.tasks().get(0).title.equals("学习日语 40 分钟"), "undo restores the deleted task and its data");

            clickDescription("模板");
            clickDescription("套用模板：" + TITLE);
            clickDescription("套用模板：" + TITLE);
            verify(store.tasks().size() == 3 && countTitle(TITLE) == 2, "a custom template can be applied repeatedly as independent tasks");
            clickDescription("新建模板");
            setField("模板名称", "检查设备状态");
            setField("备注", "检查传感器、气源和指示灯");
            clickText("保存模板");
            verify(store.templates().size() == 5, "custom template creation persists");
            scrollToBottom();
            clickDescription("模板操作：检查设备状态");
            clickPopup("编辑模板");
            setField("模板名称", "设备巡检");
            clickText("保存模板");
            verify(store.templates().get(4).title.equals("设备巡检"), "custom templates can be edited");
            scrollToBottom();
            clickDescription("模板操作：设备巡检");
            clickPopup("删除模板");
            verify(store.templates().size() == 4, "custom templates can be deleted");
            clickText("撤销");
            verify(store.templates().size() == 5, "deleted templates can be restored");

            onUi(() -> activity.finish());
            settle();
            store.close();
            store = new TodoStore(context);
            activity = startActivitySync(new Intent().setClassName(context, "com.qingtodo.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();
            verify(store.tasks().size() == 3 && store.templates().size() == 5, "tasks and custom templates survive closing and reopening the app");

            clickDescription("添加事项");
            clickDescription("填写模板：设备巡检");
            verify(fieldText("事项名称").equals("设备巡检") && fieldText("备注").equals("检查传感器、气源和指示灯"), "the add sheet can fill both fields from a custom template");
            clickText("添加到清单");
            verify(store.tasks().size() == 4, "template-filled add sheet saves a new task");

            List<TodoStore.Item> tasks = store.tasks();
            store.setDone(tasks.get(0).id, true);
            store.setDone(tasks.get(1).id, true);
            store.addTask("练习电吉他 20 分钟", "慢速练习，再把速度一点点加上去");
            onUi(() -> activity.finish());
            settle();
            activity = startActivitySync(new Intent().setClassName(context, "com.qingtodo.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();
            screenshot("home.png");
            clickDescription("添加事项");
            screenshot("add.png");
            clickDescription("关闭");
            clickDescription("模板");
            screenshot("templates.png");
            result.putString("stream", "\nPASS " + passed.size() + " acceptance checks\n" + String.join("\n", passed) + "\nScreenshots: " + context.getExternalFilesDir(null) + "/screenshots\n");
            result.putString("result", "PASS");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            try { screenshot("failure.png"); } catch (Throwable ignored) { }
            result.putString("stream", "\nFAILED after " + passed.size() + " checks: " + failure + "\n" + android.util.Log.getStackTraceString(failure));
            result.putString("result", "FAIL");
            finish(Activity.RESULT_CANCELED, result);
        } finally { if (store != null) store.close(); }
    }

    private void verify(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        passed.add("PASS: " + description);
        Bundle update = new Bundle();
        update.putString("stream", "\nPASS: " + description);
        sendStatus(0, update);
    }
    private int countTitle(String title) { int count = 0; for (TodoStore.Item t : store.tasks()) if (t.title.equals(title)) count++; return count; }
    private void onUi(Runnable action) { runOnMainSync(action); waitForIdleSync(); }
    private void settle() { SystemClock.sleep(400); waitForIdleSync(); }
    private Dialog dialog() {
        try { Field f = MainActivity.class.getDeclaredField("editor"); f.setAccessible(true); return (Dialog) f.get(activity); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private View root() { Dialog d = dialog(); return d != null ? d.getWindow().getDecorView() : activity.getWindow().getDecorView(); }
    private View findDescription(View parent, String description) {
        if (description.contentEquals(parent.getContentDescription() == null ? "" : parent.getContentDescription())) return parent;
        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            for (int i = 0; i < group.getChildCount(); i++) { View found = findDescription(group.getChildAt(i), description); if (found != null) return found; }
        }
        return null;
    }
    private View findText(View parent, String value) {
        if (parent instanceof TextView && value.contentEquals(((TextView) parent).getText())) return parent;
        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            for (int i = 0; i < group.getChildCount(); i++) { View found = findText(group.getChildAt(i), value); if (found != null) return found; }
        }
        return null;
    }
    private boolean hasText(String value) { return findText(root(), value) != null; }
    private void clickText(String value) { onUi(() -> { View v = findText(root(), value); if (v == null) throw new AssertionError("Text missing: " + value); v.performClick(); }); settle(); }
    private void clickDescription(String value) { onUi(() -> { View v = findDescription(root(), value); if (v == null) throw new AssertionError("Control missing: " + value); v.performClick(); }); settle(); }
    private void setField(String description, String value) { onUi(() -> ((EditText) findDescription(root(), description)).setText(value)); }
    private String fieldText(String description) { return ((EditText) findDescription(root(), description)).getText().toString(); }
    private void scrollToBottom() {
        onUi(() -> {
            android.widget.ScrollView sv = findScroll(root());
            if (sv != null) sv.fullScroll(View.FOCUS_DOWN);
        });
        settle();
    }
    private android.widget.ScrollView findScroll(View parent) {
        if (parent instanceof android.widget.ScrollView) return (android.widget.ScrollView) parent;
        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            for (int i = 0; i < group.getChildCount(); i++) { android.widget.ScrollView v = findScroll(group.getChildAt(i)); if (v != null) return v; }
        }
        return null;
    }
    private void clickPopup(String label) {
        // A PopupMenu is a separate app window. Inspect the real native menu,
        // without relying on a software emulator's system accessibility service.
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            final boolean[] clicked = {false};
            onUi(() -> {
                for (View window : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    View node = findText(window, label);
                    if (node == null) continue;
                    while (node.getParent() instanceof View) {
                        if (node.getParent() instanceof android.widget.AdapterView) {
                            android.widget.AdapterView<?> list = (android.widget.AdapterView<?>) node.getParent();
                            int position = list.getPositionForView(node);
                            clicked[0] = list.performItemClick(node, position, list.getItemIdAtPosition(position));
                            return;
                        }
                        node = (View) node.getParent();
                    }
                }
            });
            if (clicked[0]) { settle(); return; }
        }
        SystemClock.sleep(800);
        android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        if (root == null) throw new AssertionError("No accessible popup window: " + label);
        List<android.view.accessibility.AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(label);
        if (matches.isEmpty()) throw new AssertionError("Popup item missing: " + label);
        android.view.accessibility.AccessibilityNodeInfo node = matches.get(0);
        while (node != null && !node.isClickable()) node = node.getParent();
        if (node == null || !node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) throw new AssertionError("Popup click failed: " + label);
        settle();
    }
    private void screenshot(String name) throws Exception {
        settle();
        Bitmap image = getUiAutomation().takeScreenshot();
        if (image == null) throw new AssertionError("No screenshot");
        File dir = new File(getTargetContext().getExternalFilesDir(null), "screenshots");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) { image.compress(Bitmap.CompressFormat.PNG, 100, out); }
        image.recycle();
    }
}
