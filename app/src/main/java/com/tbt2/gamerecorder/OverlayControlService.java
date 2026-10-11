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
        if(bubble!=null||!Settings.canDrawOverlays(this))return;
        float d=getResources().getDisplayMetrics().density;int size=(int)(54*d);
        bubble=new TextView(this);bubble.setText("REC");bubble.setTextColor(Color.WHITE);bubble.setTextSize(13);bubble.setGravity(Gravity.CENTER);
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xFFDA3344);bg.setShape(GradientDrawable.OVAL);bg.setStroke((int)(2*d),0xCCFFFFFF);bubble.setBackground(bg);bubble.setElevation(8*d);
        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        params=new WindowManager.LayoutParams(size,size,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_SECURE,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.END;params.x=(int)(12*d);params.y=(int)(180*d);
        bubble.setText("Ⅱ");\n        bubble.setOnClickListener(v->{Intent i=new Intent(this,ScreenRecordService.class);i.setAction(ScreenRecordService.PAUSE);startService(i);bubble.setText("Ⅱ");});\n        bubble.setOnLongClickListener(v->{Intent i=new Intent(this,ScreenRecordService.class);i.setAction(ScreenRecordService.STOP);startService(i);return true;});
        try{wm.addView(bubble,params);}catch(Exception e){bubble=null;params=null;}
    }
    private void hide(){if(bubble!=null){try{wm.removeView(bubble);}catch(Exception ignored){}bubble=null;params=null;}}
    @Override public void onDestroy(){hide();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}