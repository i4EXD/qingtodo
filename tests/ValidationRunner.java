package com.qingtodo.acceptance;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import com.qingtodo.app.TodoStore;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;

/** Verifies Unicode blanks through the actual editor, plus the storage boundary. */
public final class ValidationRunner extends Instrumentation {
    private Activity activity;
    private boolean old;
    @Override public void onCreate(Bundle args) { super.onCreate(args);old="old".equals(args.getString("expect","fixed"));start(); }
    @Override public void onStart() {
        Bundle result=new Bundle(); int code=Activity.RESULT_OK;
        TodoStore store=new TodoStore(getTargetContext());
        HashSet<Long> initial=new HashSet<>();for(TodoStore.Item item:store.tasks())initial.add(item.id);
        try {
            activity=startActivitySync(new Intent().setClassName(getTargetContext(),"com.qingtodo.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();
            onUi(()->{invokeEditor();((EditText)get("editorTitle")).setText("\u3000\u00a0\u3000");findText(root(),"添加到清单").performClick();});
            settle();
            if(old) {
                if(store.tasks().size()!=initial.size()+1)throw new AssertionError("Old Unicode-blank bug not reproduced");
                result.putString("stream","PASS: reproduced 1.1.0 accepting an invisible title made only of full-width and nonbreaking spaces\n");
            } else {
                if(store.tasks().size()!=initial.size()||get("editor")==null||((EditText)get("editorTitle")).getError()==null)throw new AssertionError("Unicode-only blank title was accepted");
                boolean rejected=false;try{store.addTemplate("\u3000\u00a0","");}catch(IllegalArgumentException e){rejected=true;}
                if(!rejected)throw new AssertionError("Store accepted a Unicode-blank template");
                onUi(()->{((EditText)get("editorTitle")).setText("\u3000📚 学习日语\u00a0");((EditText)get("editorNote")).setText("\u3000备注内的 空格保留\u00a0");findText(root(),"添加到清单").performClick();});
                settle();
                TodoStore.Item created=null;for(TodoStore.Item t:store.tasks())if(!initial.contains(t.id))created=t;
                if(created==null||!created.title.equals("📚 学习日语")||!created.note.equals("备注内的 空格保留"))throw new AssertionError("Unicode trimming changed the title, emoji or internal spaces");
                android.content.ContentValues legacy=new android.content.ContentValues();legacy.put("title","\u3000\u00a0\u3000");legacy.put("note","\u3000旧版备注\u00a0");legacy.put("created",12345L);legacy.put("done",1);
                long legacyId=store.getWritableDatabase().insertOrThrow("tasks",null,legacy);TodoStore.Item snapshot=null;for(TodoStore.Item item:store.tasks())if(item.id==legacyId)snapshot=item;
                store.deleteTask(legacyId);store.restoreTask(snapshot);TodoStore.Item restored=null;for(TodoStore.Item item:store.tasks())if(item.id==legacyId)restored=item;
                if(restored==null||!restored.title.equals(snapshot.title)||!restored.note.equals(snapshot.note)||!restored.done||restored.created!=12345L)throw new AssertionError("Undo altered a legacy record");
                result.putString("stream","PASS: full-width and nonbreaking-space-only titles show an editor error and create no task\nPASS: the storage boundary also rejects Unicode-blank template names\nPASS: Unicode edge spaces are trimmed while emoji and internal spaces remain intact\nPASS: deletion undo restores a legacy Unicode-blank record and its data exactly\n");
            }
        }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));}
        finally{for(TodoStore.Item t:store.tasks())if(!initial.contains(t.id))store.deleteTask(t.id);store.close();}
        finish(code,result);
    }
    private Object get(String name){try{Field f=activity.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private View root(){Dialog d=(Dialog)get("editor");return d==null?activity.getWindow().getDecorView():d.getWindow().getDecorView();}
    private void invokeEditor(){try{Method m=activity.getClass().getDeclaredMethod("openEditor",boolean.class,TodoStore.Item.class);m.setAccessible(true);m.invoke(activity,false,null);}catch(Exception e){throw new RuntimeException(e);}}
    private View findText(View v,String label){if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View f=findText(g.getChildAt(i),label);if(f!=null)return f;}}return null;}
    private void onUi(Runnable r){final Throwable[] error={null};runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    private void settle(){SystemClock.sleep(400);waitForIdleSync();}
}
