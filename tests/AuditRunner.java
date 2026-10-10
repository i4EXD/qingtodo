package com.qingtodo.acceptance;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import com.qingtodo.app.TodoStore;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;

/** Reproduces four boundaries on the released APK, then checks their corrections. */
public final class AuditRunner extends Instrumentation {
    private Activity activity;
    private boolean fixed;
    private int checks;
    @Override public void onCreate(Bundle args){super.onCreate(args);fixed="fixed".equals(args.getString("expect","old"));start();}
    @Override public void onStart(){
        Bundle result=new Bundle();int code=Activity.RESULT_OK;
        TodoStore store=new TodoStore(getTargetContext());
        HashSet<Long> initialTasks=new HashSet<>(),initialTemplates=new HashSet<>();
        for(TodoStore.Item i:store.tasks())initialTasks.add(i.id);
        for(TodoStore.Item i:store.templates())initialTemplates.add(i.id);
        try{
            activity=startActivitySync(new Intent().setClassName(getTargetContext(),"com.qingtodo.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();
            String invisible="\u200b\ufeff\u2060";
            int before=store.tasks().size();
            onUi(()->{invoke("openEditor",new Class<?>[]{boolean.class,TodoStore.Item.class},false,null);((EditText)get("editorTitle")).setText(invisible);findText(root(),"添加到清单").performClick();});settle();
            boolean rejected=store.tasks().size()==before&&get("editor")!=null&&((EditText)get("editorTitle")).getError()!=null;
            if(fixed)require(rejected,"Invisible-only title was accepted");else require(!rejected&&store.tasks().size()==before+1,"Invisible-only title bug not reproduced");
            if(!fixed){final float[] width={-1};onUi(()->width[0]=((TextView)findText(root(),invisible)).getPaint().measureText(invisible));require(width[0]==0,"Title was visible");capture("invisible-title-old.png");}
            record("B01",fixed?"FIXED: zero-width-only title is rejected":"REPRODUCED: U+200B U+FEFF U+2060 creates a task with a native text width of 0 px");
            if(get("editor")!=null)onUi(()->((Dialog)get("editor")).dismiss());

            String family="👨‍👩‍👧‍👦";
            String emojiTitle="ABCDEFGHIJKLM"+family+"尾巴";
            store.addTemplate(emojiTitle,"复合 emoji 原文应保持完整");
            onUi(()->{invoke("openEditor",new Class<?>[]{boolean.class,TodoStore.Item.class},false,null);});settle();
            final String[] abbreviated={null};onUi(()->abbreviated[0]=(String)invoke("shortened",new Class<?>[]{String.class},emojiTitle));
            require(findText(root(),abbreviated[0])!=null,"Actual template chip was not found");
            if(fixed)require(abbreviated[0].equals("ABCDEFGHIJKLM"+family+"…"),"Compound emoji still split: "+abbreviated[0]);
            else require(abbreviated[0].equals("ABCDEFGHIJKLM👨…"),"Compound emoji bug not reproduced: "+abbreviated[0]);
            capture(fixed?"emoji-chip-fixed.png":"emoji-chip-old.png");
            record("B02",(fixed?"FIXED":"REPRODUCED")+": actual template chip text = "+abbreviated[0]);
            onUi(()->((Dialog)get("editor")).dismiss());

            onUi(()->{invoke("openEditor",new Class<?>[]{boolean.class,TodoStore.Item.class},false,null);((EditText)get("editorTitle")).setText("旋转时应保留选区");((EditText)get("editorNote")).setText("备注的编辑位置");((EditText)get("editorTitle")).setSelection(2,5);});settle();
            recreate();
            final int[] selection={-1,-1};onUi(()->{EditText t=(EditText)get("editorTitle");selection[0]=t.getSelectionStart();selection[1]=t.getSelectionEnd();});
            if(fixed)require(selection[0]==2&&selection[1]==5,"Selection was reset");else require(selection[0]!=2||selection[1]!=5,"Selection bug not reproduced");
            record("B03",(fixed?"FIXED":"REPRODUCED")+": editor selection before recreation = [2,5], after = ["+selection[0]+","+selection[1]+"]");
            onUi(()->((Dialog)get("editor")).dismiss());

            store.addTemplate("旋转查看验证","保留快捷跳转");
            onUi(()->{invoke("switchPage",new Class<?>[]{int.class},1);invoke("render",new Class<?>[]{boolean.class},false);});settle();
            onUi(()->findDesc(root(),"套用模板：旋转查看验证").performClick());settle();
            long deadline=(Long)get("snackExpiresAt");require(deadline>SystemClock.elapsedRealtime()+6000,"Original snackbar deadline too near");
            recreate();long remaining=deadline-SystemClock.elapsedRealtime();require(remaining>0,"Recreation happened after expiry");
            if(fixed){require(get("snackView")!=null&&findText(root(),"查看")!=null,"View action lost");require(Math.abs((Long)get("snackExpiresAt")-deadline)<100,"Deadline reset");onUi(()->findText(root(),"查看").performClick());settle();require((Integer)get("page")==0&&(Integer)get("filter")==1&&findText(root(),"旋转查看验证")!=null,"Restored action failed");}
            else require(get("snackView")==null&&findText(root(),"查看")==null,"Snackbar bug not reproduced");
            record("B04",(fixed?"FIXED":"REPRODUCED")+": template View snackbar after recreation, original deadline remaining = "+remaining+" ms");
            result.putString("stream","\nPASS "+checks+" "+(fixed?"correction checks":"confirmed bug reproductions")+"\n");
        }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("stream","FAIL after "+checks+": "+android.util.Log.getStackTraceString(e));}
        finally{for(TodoStore.Item i:store.tasks())if(!initialTasks.contains(i.id))store.deleteTask(i.id);for(TodoStore.Item i:store.templates())if(!initialTemplates.contains(i.id))store.deleteTemplate(i.id);store.close();}
        finish(code,result);
    }
    private void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void record(String id,String message){checks++;Bundle b=new Bundle();b.putString("stream","\n"+id+" "+message+"\n");sendStatus(0,b);}
    private Object get(String name){try{Field f=activity.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private Object invoke(String name,Class<?>[] types,Object...args){try{Method m=activity.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}catch(Exception e){throw new RuntimeException(e);}}
    private View root(){Dialog d=(Dialog)get("editor");return d==null?activity.getWindow().getDecorView():d.getWindow().getDecorView();}
    private View findText(View v,String s){if(v instanceof TextView&&s.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View f=findText(g.getChildAt(i),s);if(f!=null)return f;}}return null;}
    private View findDesc(View v,String s){if(s.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View f=findDesc(g.getChildAt(i),s);if(f!=null)return f;}}return null;}
    private void onUi(Runnable r){final Throwable[] e={null};runOnMainSync(()->{try{r.run();}catch(Throwable t){e[0]=t;}});waitForIdleSync();if(e[0]!=null)throw new AssertionError(e[0]);}
    private void settle(){SystemClock.sleep(250);waitForIdleSync();}
    private void recreate(){ActivityMonitor m=addMonitor("com.qingtodo.app.MainActivity",null,false);onUi(()->activity.recreate());Activity a=m.waitForActivityWithTimeout(15000);removeMonitor(m);if(a==null)throw new AssertionError("Recreation timed out");activity=a;settle();}
    private void capture(String name)throws Exception{final Bitmap[] b={null};onUi(()->{View v=root();b[0]=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b[0]));});File d=new File(getTargetContext().getExternalFilesDir(null),"audit-previews");d.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(d,name))){b[0].compress(Bitmap.CompressFormat.PNG,100,out);}b[0].recycle();}
}
