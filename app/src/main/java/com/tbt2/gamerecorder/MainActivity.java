package com.tbt2.gamerecorder;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.view.Gravity;
import android.widget.*;
public class MainActivity extends Activity {
 public static final String EXTRA_START_FROM_OVERLAY="start_from_overlay";
 private static final int CAPTURE=4812, OVERLAY=4813; private RadioGroup audio,quality; private TextView status;
 public void onCreate(Bundle b){super.onCreate(b);ui();if(getIntent().getBooleanExtra(EXTRA_START_FROM_OVERLAY,false))startCapture();}
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private LinearLayout.LayoutParams lp(){return new LinearLayout.LayoutParams(-1,-2);}
 private void ui(){
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(20),dp(20),dp(20));root.setBackgroundColor(0xFFF4F6FA);
  TextView title=new TextView(this);title.setText("Game Recorder");title.setTextSize(28);title.setTextColor(0xFF172033);title.setGravity(Gravity.CENTER_VERTICAL);root.addView(title);
  TextView intro=new TextView(this);intro.setText("Record your gameplay to MP4. Uses Android screen capture and hardware video encoding where available.");intro.setTextSize(15);root.addView(intro,lp());
  root.addView(heading("Audio"));audio=new RadioGroup(this);audio.setOrientation(1);radio(audio,"Microphone",1,true);radio(audio,"Mute (no audio)",0,false);radio(audio,"Game/internal audio",2,false);radio(audio,"Game audio + microphone",3,false);root.addView(audio);
  TextView note=new TextView(this);note.setText("Internal audio needs Android 10+ and may be blocked by some games/apps. Game + microphone mixes both sources.");note.setTextColor(0xFF8A4B08);note.setTextSize(12);root.addView(note,lp());
  root.addView(heading("Video quality"));quality=new RadioGroup(this);quality.setOrientation(1);radio(quality,"Standard — 720p / 7 Mbps",720,true);radio(quality,"High — native screen resolution / 12 Mbps",1080,false);radio(quality,"Compact — 480p / 4 Mbps",480,false);root.addView(quality);
  Button start=new Button(this);start.setText("Start screen recording");LinearLayout.LayoutParams blp=lp();blp.topMargin=dp(18);root.addView(start,blp);start.setOnClickListener(v->startCapture());
  Button library=new Button(this);library.setText("Recordings & video compression");LinearLayout.LayoutParams rlp=lp();rlp.topMargin=dp(6);root.addView(library,rlp);library.setOnClickListener(v->startActivity(new Intent(this,VideoLibraryActivity.class)));
  Button translator=new Button(this);translator.setText("Live Japanese → English subtitles");LinearLayout.LayoutParams trlp=lp();trlp.topMargin=dp(6);root.addView(translator,trlp);translator.setOnClickListener(v->startActivity(new Intent(this,TranslatorActivity.class)));
  Button overlay=new Button(this);overlay.setText("Enable floating recording controls");LinearLayout.LayoutParams olp=lp();olp.topMargin=dp(6);root.addView(overlay,olp);overlay.setOnClickListener(v->enableOverlay());
  status=new TextView(this);status.setText("Ready. Android will ask you to approve screen capture.");LinearLayout.LayoutParams slp=lp();slp.topMargin=dp(10);root.addView(status,slp);
  TextView tip=new TextView(this);tip.setText("Use the floating button to pause/resume and long-press it to stop. The overlay is marked secure to ask Android to exclude it from screen capture; confirm this on your device.");LinearLayout.LayoutParams tlp=lp();tlp.topMargin=dp(12);root.addView(tip,tlp);
  ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
 }
 private TextView heading(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(18);t.setTextColor(0xFF172033);LinearLayout.LayoutParams p=lp();p.topMargin=dp(18);t.setLayoutParams(p);return t;}
 private void radio(RadioGroup g,String s,int tag,boolean checked){RadioButton b=new RadioButton(this);b.setText(s);b.setTag(tag);g.addView(b);if(checked)b.setChecked(true);}
 private void enableOverlay(){
  if(!Settings.canDrawOverlays(this)){startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),OVERLAY);return;}
  getSharedPreferences("GameRecorder",MODE_PRIVATE).edit().putBoolean("overlay_enabled",true).apply();
  startService(new Intent(this,OverlayControlService.class).setAction(OverlayControlService.SHOW));
  Toast.makeText(this,"Floating recording controls enabled. Tap to pause/resume; long-press to stop.",Toast.LENGTH_LONG).show();
 }
 private void showOverlayIfEnabled(){
  if(getSharedPreferences("GameRecorder",MODE_PRIVATE).getBoolean("overlay_enabled",false)&&Settings.canDrawOverlays(this))
   startService(new Intent(this,OverlayControlService.class).setAction(OverlayControlService.SHOW));
 }
 private void hideOverlay(){startService(new Intent(this,OverlayControlService.class).setAction(OverlayControlService.HIDE));}
 private void startCapture(){
  int a=(Integer)((RadioButton)findViewById(audio.getCheckedRadioButtonId())).getTag();
  if(a!=0&&checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},72);return;}
  int q=(Integer)((RadioButton)findViewById(quality.getCheckedRadioButtonId())).getTag();getSharedPreferences("GameRecorder",MODE_PRIVATE).edit().putInt("audio",a).putInt("quality",q).apply();
  MediaProjectionManager m=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);startActivityForResult(m.createScreenCaptureIntent(),CAPTURE);
 }
 protected void onActivityResult(int r,int code,Intent data){super.onActivityResult(r,code,data);
  if(r==OVERLAY){if(Settings.canDrawOverlays(this)){getSharedPreferences("GameRecorder",MODE_PRIVATE).edit().putBoolean("overlay_enabled",true).apply();showOverlayIfEnabled();}return;}
  if(r!=CAPTURE)return;if(code!=RESULT_OK||data==null){status.setText("Recording cancelled.");showOverlayIfEnabled();return;}
  Intent i=new Intent(this,ScreenRecordService.class);i.setAction(ScreenRecordService.START);i.putExtra(ScreenRecordService.CODE,code);i.putExtra(ScreenRecordService.DATA,data);i.putExtra(ScreenRecordService.AUDIO,getSharedPreferences("GameRecorder",MODE_PRIVATE).getInt("audio",1));i.putExtra(ScreenRecordService.QUALITY,getSharedPreferences("GameRecorder",MODE_PRIVATE).getInt("quality",720));startForegroundService(i);status.setText("Recording started. Use the floating control to pause/resume; long-press it to stop. Notification also has controls.");
 }
 public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==72&&g.length>0&&g[0]==0)startCapture();else if(r==72){Toast.makeText(this,"Audio permission denied. Choose Mute to record silently.",Toast.LENGTH_LONG).show();showOverlayIfEnabled();}}
}