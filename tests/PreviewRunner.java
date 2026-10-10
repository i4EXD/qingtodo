package com.qingtodo.acceptance;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import com.qingtodo.app.MainActivity;
import com.qingtodo.app.TodoStore;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Renders the app's measured native views, independent of other system windows. */
public final class PreviewRunner extends Instrumentation {
    private boolean dark;
    private Activity activity;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        dark = "dark".equals(arguments.getString("theme", "light"));
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            UiModeManager mode = (UiModeManager) getTargetContext().getSystemService(Context.UI_MODE_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= 31) mode.setApplicationNightMode(dark ? UiModeManager.MODE_NIGHT_YES : UiModeManager.MODE_NIGHT_NO);
            SystemClock.sleep(1500);
            activity = startActivitySync(new Intent().setClassName(getTargetContext(), "com.qingtodo.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            capture("home-" + (dark ? "dark" : "light") + ".png", activity.getWindow().getDecorView());
            if (!dark) {
                runOnMainSync(() -> {
                    try {
                        Field page = MainActivity.class.getDeclaredField("page"); page.setAccessible(true); page.setInt(activity, 1);
                        Method render = MainActivity.class.getDeclaredMethod("render", boolean.class); render.setAccessible(true); render.invoke(activity, false);
                    } catch (Exception e) { throw new RuntimeException(e); }
                });
                waitForIdleSync();
                capture("templates-light.png", activity.getWindow().getDecorView());
                runOnMainSync(() -> {
                    try {
                        Method open = MainActivity.class.getDeclaredMethod("openEditor", boolean.class, TodoStore.Item.class); open.setAccessible(true); open.invoke(activity, false, null);
                    } catch (Exception e) { throw new RuntimeException(e); }
                });
                waitForIdleSync();
                Field field = MainActivity.class.getDeclaredField("editor"); field.setAccessible(true);
                Dialog editor = (Dialog) field.get(activity);
                capture("add-light.png", editor.getWindow().getDecorView());
            }
            result.putString("stream", "Native view previews rendered successfully\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("stream", android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void capture(String name, View view) throws Exception {
        SystemClock.sleep(500);
        waitForIdleSync();
        final Bitmap[] image = new Bitmap[1];
        runOnMainSync(() -> {
            if (view.getWidth() <= 0 || view.getHeight() <= 0) throw new AssertionError("Native view not laid out");
            image[0] = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(image[0]);
            canvas.drawColor(Color.parseColor(dark ? "#14141D" : "#FAF9FF"));
            view.draw(canvas);
        });
        File dir = new File(getTargetContext().getExternalFilesDir(null), "native-previews");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) { image[0].compress(Bitmap.CompressFormat.PNG, 100, out); }
        image[0].recycle();
    }
}
