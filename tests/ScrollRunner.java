package com.qingtodo.acceptance;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.ScrollView;
import com.qingtodo.app.TodoStore;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/** Uses a long real task list to catch scroll restoration before child layout. */
public final class ScrollRunner extends Instrumentation {
    private Activity activity;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        TodoStore store = new TodoStore(getTargetContext());
        int code = Activity.RESULT_OK;
        ArrayList<Long> ids = new ArrayList<>();
        try {
            for (int i=0; i<20; i++) ids.add(store.addTask("滚动回归验证 " + i, "检查编辑和完成之后的位置"));
            activity = startActivitySync(new Intent().setClassName(getTargetContext(), "com.qingtodo.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();
            onUi(() -> scroll().scrollTo(0, 500));
            settle();
            final int before = scroll().getScrollY();
            if (before < 400) throw new AssertionError("Test did not scroll: " + before);
            onUi(() -> invoke("render", new Class<?>[]{boolean.class}, true));
            settle();
            int after = scroll().getScrollY();
            if (Math.abs(before-after)>2) throw new AssertionError("Scroll jumped during refresh: " + before + " -> " + after);
            onUi(() -> invoke("switchPage", new Class<?>[]{int.class}, 1)); settle();
            onUi(() -> invoke("switchPage", new Class<?>[]{int.class}, 0)); settle();
            if (Math.abs(before-scroll().getScrollY())>2) throw new AssertionError("Scroll lost across page navigation");
            result.putString("stream", "PASS: long task-list refresh preserves the measured scroll position\nPASS: switching pages preserves independent task scroll position\n");
        } catch(Throwable e) { result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(e)); code = Activity.RESULT_CANCELED; }
        finally { for(long id:ids) store.deleteTask(id); store.close(); }
        finish(code,result);
    }
    private ScrollView scroll() {
        try { Field f=activity.getClass().getDeclaredField("scroll");f.setAccessible(true);return (ScrollView)f.get(activity); }
        catch(Exception e){throw new RuntimeException(e);}
    }
    private void invoke(String name,Class<?>[] types,Object...args) {
        try {Method m=activity.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}
        catch(Exception e){throw new RuntimeException(e);}
    }
    private void onUi(Runnable r) { final Throwable[] error={null};runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]); }
    private void settle(){SystemClock.sleep(400);waitForIdleSync();}
}
