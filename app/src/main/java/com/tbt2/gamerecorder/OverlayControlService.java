package com.tbt2.gamerecorder;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/** Floating recording controls. FLAG_SECURE asks Android not to capture this overlay; verify on device. */
public class OverlayControlService extends Service {
    public static final String SHOW="gr.overlay.show", HIDE="gr.overlay.hide";
    private WindowManager wm;
    private TextView bubble;
    private WindowManager.LayoutParams params;
    @Override public void onCreate(){super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&HIDE.equals(intent.getAction())) { hide(); stopSelf(); } else show();
        return START_NOT_STICKY;
    }
    private void show(){
        if(!Settings.canDrawOverlays(this))return;
        if(bubble!=null){refreshLabel();return;}
        float d=getResources().getDisplayMetrics().density;int size=(int)(54*d);
        bubble=new TextView(this);bubble.setText("REC");bubble.setTextColor(Color.WHITE);bubble.setTextSize(13);bubble.setGravity(Gravity.CENTER);
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xFFDA3344);bg.setShape(GradientDrawable.OVAL);bg.setStroke((int)(2*d),0xCCFFFFFF);bubble.setBackground(bg);bubble.setElevation(8*d);
        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        params=new WindowManager.LayoutParams(size,size,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_SECURE,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.END;params.x=(int)(12*d);params.y=(int)(180*d);
        boolean active=getSharedPreferences("GameRecorder",MODE_PRIVATE).getBoolean("recording_active",false);
        boolean paused=getSharedPreferences("GameRecorder",MODE_PRIVATE).getBoolean("recording_paused",false);
        bubble.setText(!active?"REC":(paused?"▶":"Ⅱ"));
        bubble.setOnClickListener(v->{
            boolean isActive=getSharedPreferences("GameRecorder",MODE_PRIVATE).getBoolean("recording_active",false);
            if(!isActive){
                Intent open=new Intent(this,MainActivity.class);
                open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
                open.putExtra(MainActivity.EXTRA_START_FROM_OVERLAY,true);
                startActivity(open);
            } else {
                boolean isPaused=getSharedPreferences("GameRecorder",MODE_PRIVATE).getBoolean("recording_paused",false);
                Intent cmd=new Intent(this,ScreenRecordService.class);
                cmd.setAction(isPaused?ScreenRecordService.RESUME:ScreenRecordService.PAUSE);
                startService(cmd);
                bubble.setText(isPaused?"Ⅱ":"▶");
            }
        });
        bubble.setOnLongClickListener(v->{Intent i=new Intent(this,ScreenRecordService.class);i.setAction(ScreenRecordService.STOP);startService(i);return true;});
        try{wm.addView(bubble,params);}catch(Exception e){bubble=null;params=null;}
    }
    private void refreshLabel(){if(bubble!=null){boolean active=getSharedPreferences("GameRecorder",MODE_PRIVATE).getBoolean("recording_active",false);boolean paused=getSharedPreferences("GameRecorder",MODE_PRIVATE).getBoolean("recording_paused",false);bubble.setText(!active?"REC":(paused?"▶":"Ⅱ"));}}
    private void hide(){if(bubble!=null){try{wm.removeView(bubble);}catch(Exception ignored){}bubble=null;params=null;}}
    @Override public void onDestroy(){hide();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}