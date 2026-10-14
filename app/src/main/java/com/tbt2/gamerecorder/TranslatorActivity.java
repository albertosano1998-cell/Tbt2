package com.tbt2.gamerecorder;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class TranslatorActivity extends Activity {
    private static final int REQUEST_CAPTURE=7311, REQUEST_OVERLAY=7312;
    private TextView status;
    private int pendingCode;
    private Intent pendingData;
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private LinearLayout.LayoutParams lp(){return new LinearLayout.LayoutParams(-1,-2);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(22),dp(20),dp(20));root.setBackgroundColor(0xFFF4F6FA);
        TextView title=new TextView(this);title.setText("Live Japanese subtitles");title.setTextSize(26);title.setTextColor(0xFF172033);root.addView(title,lp());
        TextView intro=new TextView(this);intro.setText("Play a Japanese video in another app, connect your earphones, then start capture here. If that app permits internal audio capture, English subtitles will float over the video.");intro.setTextSize(15);LinearLayout.LayoutParams ip=lp();ip.topMargin=dp(10);root.addView(intro,ip);
        TextView note=new TextView(this);note.setText("First use downloads the Japanese speech model and the Japanese→English translation model. Allow internet access for setup. Once models are downloaded, recognition and translation run on this phone. Some streaming/DRM apps block audio capture.");note.setTextSize(13);note.setTextColor(0xFF8A4B08);LinearLayout.LayoutParams np=lp();np.topMargin=dp(12);root.addView(note,np);
        Button start=new Button(this);start.setText("Start live subtitles");LinearLayout.LayoutParams bp=lp();bp.topMargin=dp(18);root.addView(start,bp);start.setOnClickListener(v->begin());
        Button stop=new Button(this);stop.setText("Stop live subtitles");root.addView(stop,lp());stop.setOnClickListener(v->{stopService(new Intent(this,LiveSubtitleService.class));status.setText("Subtitle service stopped.");});
        status=new TextView(this);status.setText("Ready. Choose Start live subtitles, approve screen/audio capture, then return to your video.");LinearLayout.LayoutParams sp=lp();sp.topMargin=dp(12);root.addView(status,sp);
        TextView privacy=new TextView(this);privacy.setText("Privacy: the audio is processed on-device by the installed models. Android may prevent capture from some apps. This feature does not record or save the video.");LinearLayout.LayoutParams pp=lp();pp.topMargin=dp(16);root.addView(privacy,pp);
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    }
    private void begin(){
        if(!Settings.canDrawOverlays(this)){startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName())),REQUEST_OVERLAY);return;}
        requestCapture();
    }
    private void requestCapture(){
        MediaProjectionManager mgr=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mgr.createScreenCaptureIntent(),REQUEST_CAPTURE);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==REQUEST_OVERLAY){
            if(Settings.canDrawOverlays(this))requestCapture();
            else status.setText("Overlay permission is needed to show subtitles over another app.");
            return;
        }
        if(request==REQUEST_CAPTURE){
            if(result!=RESULT_OK||data==null){status.setText("Capture permission was cancelled.");return;}
            Intent service=new Intent(this,LiveSubtitleService.class);
            service.setAction(LiveSubtitleService.START);
            service.putExtra(LiveSubtitleService.RESULT_CODE,result);
            service.putExtra(LiveSubtitleService.RESULT_DATA,data);
            startForegroundService(service);
            status.setText("Starting. A notification will remain visible while subtitles are active. Switch back to the video after the overlay appears.");
            Toast.makeText(this,"Live subtitles starting",Toast.LENGTH_SHORT).show();
        }
    }
}
