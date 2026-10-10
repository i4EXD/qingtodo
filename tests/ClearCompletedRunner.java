package com.qingtodo.acceptance;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Parcel;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.qingtodo.app.MainActivity;
import com.qingtodo.app.ColorTheme;
import com.qingtodo.app.TodoStore;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Bulk operations: actual button, exact undo, recreation, expiry and transaction rollback. */
public final class ClearCompletedRunner extends Instrumentation {
    private Activity activity;
    private boolean large;
    private int passed;
    @Override public void onCreate(Bundle args){super.onCreate(args);large="true".equals(args.getString("large","false"));start();}
    @Override public void onStart(){
        Bundle result=new Bundle();int code=Activity.RESULT_OK;
        TodoStore store=new TodoStore(getTargetContext());
        List<TodoStore.Item> original=store.tasks();String templates=fingerprint(store.templates());
        try{
            store.getWritableDatabase().delete("tasks",null,null);store.discardCompletedUndo();
            long pending=store.addTask("继续读书","未完成的备注");
            store.setDone(store.addTask("已经散步","保留原始内容 🐾"),true);
            store.setDone(store.addTask("整理桌面","另一段备注"),true);
            String before=fingerprint(store.tasks());
            activity=startActivitySync(new Intent().setClassName(getTargetContext(),"com.qingtodo.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));settle();
            verify(findText(root(),"清除已完成")==null,"all filter keeps the cleanup entry in the completed filter");
            onUi(()->invoke("switchFilter",new Class<?>[]{int.class},1));settle();
            verify(findText(root(),"清除已完成")==null,"pending filter has no completed cleanup button");
            onUi(()->invoke("switchFilter",new Class<?>[]{int.class},2));settle();
            capture(large?"clear-large-probe.png":"clear-normal-probe.png");
            final TextView[] clear={null};onUi(()->{clear[0]=(TextView)findText(root(),"清除已完成");if(clear[0]==null)throw new AssertionError("Cleanup button missing");if(clear[0].getLayout()==null)throw new AssertionError("Cleanup button has no native layout");if(clear[0].getLayout().getEllipsisCount(0)>0||clear[0].getHeight()<clear[0].getLayout().getHeight()+clear[0].getPaddingTop()+clear[0].getPaddingBottom())throw new AssertionError("Cleanup geometry: buttonHeight="+clear[0].getHeight()+", actualTextLayoutHeight="+clear[0].getLayout().getHeight()+", paddingTop="+clear[0].getPaddingTop()+", paddingBottom="+clear[0].getPaddingBottom()+", estimatedLineHeight="+clear[0].getLineHeight()+", textSize="+clear[0].getTextSize()+", ellipsis="+clear[0].getLayout().getEllipsisCount(0));});
            verify(true,"cleanup button text and touch area fit the measured native layout");
            capture(large?"clear-large.png":"clear-before.png");
            if(large)verify(activity.getResources().getConfiguration().fontScale>=1.45,"cleanup button remains readable at 1.5x font size");
            onUi(()->{clear[0].performClick();clear[0].performClick();});settle();
            verify(store.tasks().size()==1&&store.tasks().get(0).id==pending&&!store.tasks().get(0).done,"one click and a rapid duplicate click remove exactly two completed tasks");
            verify(templates.equals(fingerprint(store.templates())),"bulk cleanup preserves every template");
            verify(findText(root(),"清除已完成")==null&&findText(root(),"撤销")!=null&&store.completedUndoCount()==2,"zero completed tasks hide cleanup and expose batch undo");
            capture(large?"clear-after-large.png":"clear-after.png");
            if(!large){
                long expiry=(Long)get("undoExpiresAt");
                clickDesc("模板");clickDesc("待办");
                verify((Long)get("undoExpiresAt")==expiry&&findText(root(),"撤销")!=null,"page switches preserve the batch undo deadline");
                recreate();
                verify((Long)get("undoExpiresAt")==expiry&&findText(root(),"撤销")!=null,"activity recreation restores batch undo with the original deadline");
                onUi(()->invoke("applyTheme",new Class<?>[]{ColorTheme.class},ColorTheme.BLUE));settle();
                verify((Long)get("undoExpiresAt")==expiry&&findText(root(),"撤销")!=null,"theme changes preserve batch undo and its deadline");
            }
            final View[] undo={null};onUi(()->{undo[0]=findText(root(),"撤销");undo[0].performClick();undo[0].performClick();});settle();
            verify(before.equals(fingerprint(store.tasks()))&&store.completedUndoCount()==0,"undo restores IDs, titles, notes, completion, timestamps and ordering exactly once");
            if(large){
                onUi(()->{checkHeight((TextView)findText(root(),"全部  3"));checkHeight((TextView)findText(root(),"未完成  1"));checkHeight((TextView)findText(root(),"已完成  2"));});
                verify(true,"all filter chips preserve the full native text layout at large font size");
                onUi(()->invoke("openEditor",new Class<?>[]{boolean.class,TodoStore.Item.class},false,null));settle();
                onUi(()->{View dialog=((Dialog)get("editor")).getWindow().getDecorView();checkHeight((TextView)findText(dialog,"添加到清单"));checkPresetHeights(dialog);});
                verify(true,"editor save and preset buttons accommodate Chinese fallback font metrics");
                onUi(()->{((Dialog)get("editor")).dismiss();invoke("showThemes",new Class<?>[0]);});settle();
                onUi(()->checkHeight((TextView)findText(((Dialog)get("themeDialog")).getWindow().getDecorView(),"完成")));
                verify(true,"theme Done button accommodates the full native text height");
                onUi(()->{((Dialog)get("themeDialog")).dismiss();invoke("showAbout",new Class<?>[0]);});settle();
                onUi(()->checkHeight((TextView)findText(((Dialog)get("aboutDialog")).getWindow().getDecorView(),"知道啦")));
                verify(true,"About button accommodates the full native text height");
                onUi(()->((Dialog)get("aboutDialog")).dismiss());
            }
            if(!large){
                clickText("清除已完成");long expiry=(Long)get("undoExpiresAt");
                onUi(()->invoke("clearCompleted",new Class<?>[0]));settle();
                verify((Long)get("undoExpiresAt")==expiry&&store.completedUndoCount()==2,"cleanup with zero completed tasks keeps the existing batch undo");
                TodoStore.Item p=store.tasks().get(0);
                onUi(()->invoke("deleteTask",new Class<?>[]{TodoStore.Item.class},p));settle();
                verify(store.completedUndoCount()==0&&get("pendingUndo")!=null,"a later single deletion replaces batch undo cleanly");
                clickText("撤销");
                verify(store.tasks().size()==1&&store.tasks().get(0).id==pending,"single-delete undo remains functional after bulk cleanup");

                store.setDone(store.addTask("到期清理验证","临时快照应过期清理"),true);
                onUi(()->{invoke("render",new Class<?>[]{boolean.class},false);invoke("clearCompleted",new Class<?>[0]);});settle();
                long remaining=(Long)get("undoExpiresAt")-SystemClock.elapsedRealtime();
                SystemClock.sleep(Math.max(0,remaining)+350);waitForIdleSync();
                verify(get("snackView")==null&&store.completedUndoCount()==0,"the real undo timeout removes the temporary batch snapshot");

                store.setDone(store.addTask("事务第一项","原始 A"),true);
                store.setDone(store.addTask("事务第二项","原始 B"),true);
                String transactionBefore=fingerprint(store.tasks());
                store.getWritableDatabase().execSQL("CREATE TRIGGER audit_abort_clear BEFORE DELETE ON tasks WHEN OLD.done=1 BEGIN SELECT RAISE(ABORT,'audit rollback'); END");
                boolean failed=false;try{store.clearCompleted();}catch(RuntimeException e){failed=true;}finally{store.getWritableDatabase().execSQL("DROP TRIGGER audit_abort_clear");}
                verify(failed&&transactionBefore.equals(fingerprint(store.tasks()))&&store.completedUndoCount()==0,"an interrupted clear transaction rolls back both deletion and undo snapshot");
                List<TodoStore.Item> snapshots=store.tasks();TodoStore.Item completed=null;for(TodoStore.Item i:snapshots)if(i.done)completed=i;
                verify(store.clearCompleted()==2,"storage clears the current completed count");
                store.restoreTask(completed);String conflictBefore=fingerprint(store.tasks());failed=false;
                try{store.restoreCompleted();}catch(RuntimeException e){failed=true;}
                verify(failed&&conflictBefore.equals(fingerprint(store.tasks()))&&store.completedUndoCount()==2,"a restore collision cannot partially restore or overwrite tasks");
                store.deleteTask(completed.id);store.restoreCompleted();
                verify(transactionBefore.equals(fingerprint(store.tasks())),"the retained snapshot can restore all rows after a failed transaction");

                onUi(()->invoke("dismissSnack",new Class<?>[0]));
                store.getWritableDatabase().delete("tasks",null,null);store.discardCompletedUndo();
                StringBuilder note=new StringBuilder();for(int n=0;n<1900;n++)note.append('记');
                android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
                try{for(int n=0;n<600;n++)store.setDone(store.addTask("大量清除 "+n,note.toString()),true);db.setTransactionSuccessful();}finally{db.endTransaction();}
                String bulkBefore=fingerprint(store.tasks());
                onUi(()->{invoke("switchFilter",new Class<?>[]{int.class},1);invoke("clearCompleted",new Class<?>[0]);});settle();
                verify(store.tasks().isEmpty()&&store.completedUndoCount()==600,"600 completed tasks with long notes clear as a single batch");
                final int[] bundleSize={0};onUi(()->{Bundle state=new Bundle();((MainActivity)activity).onSaveInstanceState(state);Parcel parcel=Parcel.obtain();state.writeToParcel(parcel,0);bundleSize[0]=parcel.dataSize();parcel.recycle();});
                verify(bundleSize[0]<32768,"saved Activity state stays small for a large batch: "+bundleSize[0]+" bytes");
                recreate();clickText("撤销");
                verify(bulkBefore.equals(fingerprint(store.tasks()))&&store.completedUndoCount()==0,"large batch undo survives recreation and restores all 600 records exactly");
            }
            result.putString("stream","\nPASS "+passed+" "+(large?"large-font cleanup":"bulk cleanup")+" checks\n");
        }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("stream","FAIL after "+passed+": "+android.util.Log.getStackTraceString(e));}
        finally{
            try{if(activity!=null)onUi(()->invoke("dismissSnack",new Class<?>[0]));}catch(Throwable ignored){}
            store.getWritableDatabase().execSQL("DROP TRIGGER IF EXISTS audit_abort_clear");
            store.getWritableDatabase().delete("tasks",null,null);store.discardCompletedUndo();
            for(TodoStore.Item i:original)store.restoreTask(i);store.close();
        }
        finish(code,result);
    }
    private void verify(boolean ok,String message){if(!ok)throw new AssertionError(message);passed++;Bundle b=new Bundle();b.putString("stream","\nPASS: "+message+"\n");sendStatus(0,b);}
    private void checkHeight(TextView v){if(v==null||v.getLayout()==null||v.getHeight()<v.getLayout().getHeight()+v.getCompoundPaddingTop()+v.getCompoundPaddingBottom())throw new AssertionError("Button has insufficient text height: "+(v==null?"missing":v.getText()));}
    private void checkPresetHeights(View v){if(v instanceof TextView&&v.getContentDescription()!=null&&v.getContentDescription().toString().startsWith("填写模板："))checkHeight((TextView)v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++)checkPresetHeights(g.getChildAt(n));}}
    private String fingerprint(List<TodoStore.Item> items){StringBuilder s=new StringBuilder();for(TodoStore.Item i:items)s.append(i.id).append('|').append(i.title).append('|').append(i.note).append('|').append(i.done).append('|').append(i.created).append('\n');return s.toString();}
    private Object get(String name){try{Field f=activity.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private void invoke(String name,Class<?>[] types,Object...args){try{Method m=activity.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}catch(Exception e){throw new RuntimeException(e);}}
    private View root(){return activity.getWindow().getDecorView();}
    private View findText(View v,String s){if(v instanceof TextView&&s.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View f=findText(g.getChildAt(i),s);if(f!=null)return f;}}return null;}
    private View findDesc(View v,String s){if(s.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View f=findDesc(g.getChildAt(i),s);if(f!=null)return f;}}return null;}
    private void clickText(String s){onUi(()->{View v=findText(root(),s);if(v==null)throw new AssertionError("Missing "+s);v.performClick();});settle();}
    private void clickDesc(String s){onUi(()->{View v=findDesc(root(),s);if(v==null)throw new AssertionError("Missing "+s);v.performClick();});settle();}
    private void onUi(Runnable r){final Throwable[] e={null};runOnMainSync(()->{try{r.run();}catch(Throwable t){e[0]=t;}});waitForIdleSync();if(e[0]!=null)throw new AssertionError(e[0]);}
    private void settle(){SystemClock.sleep(250);waitForIdleSync();}
    private void recreate(){ActivityMonitor m=addMonitor("com.qingtodo.app.MainActivity",null,false);onUi(()->activity.recreate());Activity a=m.waitForActivityWithTimeout(15000);removeMonitor(m);if(a==null)throw new AssertionError("Recreation timed out");activity=a;settle();}
    private void capture(String name)throws Exception{final Bitmap[] b={null};onUi(()->{View v=root();b[0]=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b[0]));});File d=new File(getTargetContext().getExternalFilesDir(null),"audit-previews");d.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(d,name))){b[0].compress(Bitmap.CompressFormat.PNG,100,out);}b[0].recycle();}
}
