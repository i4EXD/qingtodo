package com.qingtodo.acceptance;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.os.Bundle;
import com.qingtodo.app.TodoStore;

/** Seeds records under the old APK, then checks them after a signed in-place update. */
public final class UpgradeRunner extends Instrumentation {
    private String phase;
    @Override public void onCreate(Bundle args) { super.onCreate(args); phase = args.getString("phase", "verify"); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        TodoStore store = new TodoStore(getTargetContext());
        try {
            SharedPreferences probe = getTargetContext().getSharedPreferences("upgrade_probe", 0);
            if (phase.equals("seed")) {
                long id = store.addTask("升级验证・保留事项", "创建于旧版本");
                store.setDone(id, true);
                long template = store.addTemplate("升级验证・保留模板", "模板内容也需要保留");
                if (!probe.edit().putLong("task", id).putLong("template", template).commit()) throw new AssertionError("Probe not saved");
                result.putString("stream", "PASS: old-version records seeded\n");
            } else {
                if (!getTargetContext().getPackageManager().getPackageInfo("com.qingtodo.app", 0).versionName.equals("1.3.0")) throw new AssertionError("Wrong APK version");
                boolean taskFound = false, templateFound = false;
                for (TodoStore.Item t : store.tasks()) if (t.id == probe.getLong("task", -1) && t.done && t.title.equals("升级验证・保留事项") && t.note.equals("创建于旧版本")) taskFound = true;
                for (TodoStore.Item t : store.templates()) if (t.id == probe.getLong("template", -1) && t.title.equals("升级验证・保留模板") && t.note.equals("模板内容也需要保留")) templateFound = true;
                if (!taskFound || !templateFound) throw new AssertionError("An existing task/template was lost during upgrade");
                if (store.getReadableDatabase().getVersion() != 2 || store.completedUndoCount() != 0) throw new AssertionError("Database migration failed");
                result.putString("stream", "PASS: signed 1.2.0 -> 1.3.0 update and database migration preserve tasks, notes, completion and templates\n");
            }
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED, result);
        } finally { store.close(); }
    }
}
