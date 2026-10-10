package com.qingtodo.acceptance;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.qingtodo.app.ColorTheme;
import com.qingtodo.app.TodoStore;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Real UI palette selection, state preservation, restart and measured readability. */
public final class ThemeRunner extends Instrumentation {
    private Activity activity;
    private boolean dark, large, verifyRestart;
    private final List<String> passed = new ArrayList<>();
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);dark="dark".equals(args.getString("theme","light"));large="true".equals(args.getString("large","false"));verifyRestart="verify".equals(args.getString("phase","full"));start();
    }
    @Override public void onStart() {
        Bundle result=new Bundle();int code=Activity.RESULT_OK;
        TodoStore store=new TodoStore(getTargetContext());
        HashSet<Long> initialTasks=new HashSet<>(),initialTemplates=new HashSet<>();
        for(TodoStore.Item t:store.tasks())initialTasks.add(t.id);
        for(TodoStore.Item t:store.templates())initialTemplates.add(t.id);
        String initialData=fingerprint(store);
        try {
            if(!large&&!verifyRestart)getTargetContext().getSharedPreferences("appearance",0).edit().putString("color_theme","unknown-palette").commit();
            ((UiModeManager)getTargetContext().getSystemService(Context.UI_MODE_SERVICE)).setApplicationNightMode(dark?UiModeManager.MODE_NIGHT_YES:UiModeManager.MODE_NIGHT_NO);
            SystemClock.sleep(1200);
            activity=startActivitySync(new Intent().setClassName(getTargetContext(),"com.qingtodo.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));settle();
            verify((Boolean)get("dark")==dark,"requested "+(dark?"dark":"light")+" mode is actually active");
            if(verifyRestart) {
                verify(((ColorTheme)get("colorTheme"))==ColorTheme.ROSE,"selected theme survives app-process restart and renders again");
            } else {
                if(!large)verify(((ColorTheme)get("colorTheme"))==ColorTheme.PURPLE,"unknown stored palette ID falls back to the default safely");
                clickDesc("颜色主题");
                onUi(()->{
                    for(ColorTheme theme:ColorTheme.values()) {
                        ViewGroup tile=(ViewGroup)findDesc(root(),"选择主题："+theme.label);
                        TextView caption=(TextView)findText(tile,theme.label);
                        if(caption==null||caption.getLayout()==null||caption.getLayout().getEllipsisCount(0)>0||caption.getHeight()<caption.getLayout().getHeight())throw new AssertionError("Clipped theme caption: "+theme.label);
                    }
                });
                verify(true,"all eight theme labels fit their measured native tiles");
                if(large) {
                    verify(activity.getResources().getConfiguration().fontScale>=1.45f,"theme picker remains readable with the device's 1.5x font scale");
                } else {
                    ColorTheme[] themes=ColorTheme.values();
                    for(int n=1;n<=themes.length;n++) {
                        ColorTheme theme=themes[n%themes.length];
                        Dialog sameDialog=(Dialog)get("themeDialog");
                        clickDesc("选择主题："+theme.label);
                        onUi(()->{
                            if(get("themeDialog")!=sameDialog)throw new AssertionError("Palette created an overlapping dialog");
                            if(((ColorTheme)get("colorTheme"))!=theme||!findDesc(root(),"选择主题："+theme.label).isSelected())throw new AssertionError("Wrong selected palette");
                            ViewGroup add=(ViewGroup)findDesc(activity.getWindow().getDecorView(),"添加事项");
                            TextView label=(TextView)findText(add,"添加事项");
                            GradientDrawable fill=(GradientDrawable)((RippleDrawable)add.getBackground()).getDrawable(0);
                            if(label.getCurrentTextColor()!=theme.onPrimary(dark)||fill.getColor().getDefaultColor()!=theme.primary(dark))throw new AssertionError("Main action did not receive the selected palette");
                            android.util.TypedValue accent=new android.util.TypedValue();
                            if(!activity.getTheme().resolveAttribute(android.R.attr.colorAccent,accent,true)||accent.data!=theme.primary(dark))throw new AssertionError("Native input accent did not follow the selected palette");
                            checkContrast(theme);
                        });
                        verify(theme.id.equals(getTargetContext().getSharedPreferences("appearance",0).getString("color_theme","purple")),theme.label+": selection, action colors, readable text and persisted ID agree");
                        capture("home-"+theme.id+"-"+(dark?"dark":"light")+".png",activity.getWindow().getDecorView());
                    }
                    capture("picker-"+(dark?"dark":"light")+".png",root());
                    clickText("完成");clickDesc("模板");clickDesc("颜色主题");clickDesc("选择主题："+ColorTheme.ROSE.label);
                    recreate();
                    verify(get("themeDialog")!=null&&((Dialog)get("themeDialog")).isShowing()&&((ColorTheme)get("colorTheme"))==ColorTheme.ROSE&&((Integer)get("page"))==1,"rotation preserves the open theme picker, selected color and template page");
                    clickText("完成");
                    verify(initialData.equals(fingerprint(store)),"all palette switches and rotation preserve tasks, notes, completion and templates");

                    long undoId=store.addTask("主题撤销验证","保留这段备注");
                    TodoStore.Item undo=null;for(TodoStore.Item item:store.tasks())if(item.id==undoId)undo=item;
                    final TodoStore.Item deleted=undo;
                    onUi(()->{invoke("switchPage",new Class<?>[]{int.class},0);invoke("render",new Class<?>[]{boolean.class},false);invoke("deleteTask",new Class<?>[]{TodoStore.Item.class},deleted);});settle();
                    long deadline=(Long)get("undoExpiresAt");
                    clickDesc("颜色主题");clickDesc("选择主题："+ColorTheme.BLUE.label);
                    verify((Long)get("undoExpiresAt")==deadline,"changing color preserves the original delete-undo deadline");
                    clickText("完成");clickText("撤销");
                    boolean restored=false;for(TodoStore.Item t:store.tasks())if(t.id==undoId&&t.note.equals("保留这段备注"))restored=true;
                    verify(restored,"undo remains usable after changing the theme");store.deleteTask(undoId);

                    store.addTemplate("主题跳转验证","切换颜色后仍可查看");
                    onUi(()->{invoke("switchPage",new Class<?>[]{int.class},1);invoke("render",new Class<?>[]{boolean.class},false);});settle();
                    clickDesc("套用模板：主题跳转验证");clickDesc("颜色主题");clickDesc("选择主题："+ColorTheme.MINT.label);clickText("完成");clickText("查看");
                    verify(((Integer)get("page"))==0&&((Integer)get("filter"))==1&&findText(activity.getWindow().getDecorView(),"主题跳转验证")!=null,"template View action keeps working after the snackbar changes color");
                    clickDesc("颜色主题");clickDesc("选择主题："+ColorTheme.ROSE.label);clickText("完成");
                }
            }
            result.putString("stream","\nPASS "+passed.size()+" "+(dark?"dark":"light")+(large?" large-font":"")+" theme checks\n"+String.join("\n",passed)+"\n");
        }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("stream","FAIL after "+passed.size()+" checks: "+android.util.Log.getStackTraceString(e));}
        finally {
            for(TodoStore.Item t:store.tasks())if(!initialTasks.contains(t.id))store.deleteTask(t.id);
            for(TodoStore.Item t:store.templates())if(!initialTemplates.contains(t.id))store.deleteTemplate(t.id);
            store.close();
        }
        finish(code,result);
    }
    private void checkContrast(ColorTheme theme) {
        if(contrast(theme.onPrimary(dark),theme.primary(dark))<4.5||contrast(theme.primary(dark),theme.soft(dark))<4.5||contrast(theme.ink(dark),theme.surface(dark))<4.5||contrast(theme.muted(dark),theme.surface(dark))<4.5||contrast(theme.snackAction(dark),theme.snackBackground(dark))<4.5)throw new AssertionError("Insufficient text contrast in "+theme.id);
    }
    private void verify(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
        passed.add("PASS: "+message);
        Bundle status=new Bundle();status.putString("stream","\nPASS: "+message);sendStatus(0,status);
    }
    private double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    private double luminance(int color){return .2126*linear((color>>16)&255)+.7152*linear((color>>8)&255)+.0722*linear(color&255);}
    private double linear(int value){double x=value/255.;return x<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4);}
    private String fingerprint(TodoStore store){StringBuilder s=new StringBuilder();for(TodoStore.Item t:store.tasks())s.append(t.id).append('|').append(t.title).append('|').append(t.note).append('|').append(t.done).append('|').append(t.created).append('\n');s.append("templates\n");for(TodoStore.Item t:store.templates())s.append(t.id).append('|').append(t.title).append('|').append(t.note).append('|').append(t.created).append('\n');return s.toString();}
    private Object get(String name){try{Field f=activity.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private void invoke(String name,Class<?>[] types,Object...args){try{Method m=activity.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}catch(Exception e){throw new RuntimeException(e);}}
    private View root(){Dialog d=(Dialog)get("themeDialog");return d==null?activity.getWindow().getDecorView():d.getWindow().getDecorView();}
    private View findDesc(View v,String label){if(label.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View f=findDesc(g.getChildAt(i),label);if(f!=null)return f;}}return null;}
    private View findText(View v,String label){if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View f=findText(g.getChildAt(i),label);if(f!=null)return f;}}return null;}
    private void clickDesc(String s){onUi(()->{View v=findDesc(root(),s);if(v==null)throw new AssertionError("Missing "+s);v.performClick();});settle();}
    private void clickText(String s){onUi(()->{View v=findText(root(),s);if(v==null)throw new AssertionError("Missing "+s);v.performClick();});settle();}
    private void onUi(Runnable r){final Throwable[] error={null};runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    private void settle(){SystemClock.sleep(350);waitForIdleSync();}
    private void recreate(){ActivityMonitor monitor=addMonitor("com.qingtodo.app.MainActivity",null,false);onUi(()->activity.recreate());Activity a=monitor.waitForActivityWithTimeout(15000);removeMonitor(monitor);if(a==null)throw new AssertionError("Recreation timed out");activity=a;settle();}
    private void capture(String name,View view)throws Exception {
        final Bitmap[] bitmap={null};onUi(()->{bitmap[0]=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap[0]));});
        File folder=new File(getTargetContext().getExternalFilesDir(null),"theme-previews");folder.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(folder,name))){bitmap[0].compress(Bitmap.CompressFormat.PNG,100,out);}bitmap[0].recycle();
    }
}
