package com.tbt2.gamerecorder;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
public class MainActivity extends Activity {
 private static final int CAPTURE=4812; private RadioGroup audio,quality; private TextView status;
 public void onCreate(Bundle b){super.onCreate(b);ui();}
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private LinearLayout.LayoutParams lp(){return new LinearLayout.LayoutParams(-1,-2);}
 private void ui(){
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(20),dp(20),dp(20));root.setBackgroundColor(0xFFF4F6FA);
  TextView title=new TextView(this);title.setText("Game Recorder");title.setTextSize(28);title.setTextColor(0xFF172033);title.setGravity(Gravity.CENTER_VERTICAL);root.addView(title);
  TextView intro=new TextView(this);intro.setText("Record your gameplay to MP4. Uses Android screen capture and hardware video encoding where available.");intro.setTextSize(15);root.addView(intro,lp());
  root.addView(heading("Audio"));audio=new RadioGroup(this);audio.setOrientation(1);radio(audio,"Microphone",1,true);radio(audio,"Mute (no audio)",0,false);radio(audio,"Game/internal audio (not available yet)",2,false);radio(audio,"Game audio + microphone (not available yet)",3,false);root.addView(audio);
  TextView note=new TextView(this);note.setText("This first build supports microphone or mute. Internal game audio is not implemented yet.");note.setTextColor(0xFF8A4B08);note.setTextSize(12);root.addView(note,lp());
  root.addView(heading("Video quality"));quality=new RadioGroup(this);quality.setOrientation(1);radio(quality,"Balanced — 720p / 6 Mbps",720,true);radio(quality,"High — full screen / 12 Mbps",1080,false);radio(quality,"Battery saver — 720p / 3 Mbps",360,false);root.addView(quality);
  Button start=new Button(this);start.setText("Start screen recording");LinearLayout.LayoutParams blp=lp();blp.topMargin=dp(18);root.addView(start,blp);start.setOnClickListener(v->startCapture());
  status=new TextView(this);status.setText("Ready. Android will ask you to approve screen capture.");LinearLayout.LayoutParams slp=lp();slp.topMargin=dp(10);root.addView(status,slp);
  TextView tip=new TextView(this);tip.setText("Stop recording from the persistent notification. The app screen is not kept over your game, so its controls won't appear in the saved video.");LinearLayout.LayoutParams tlp=lp();tlp.topMargin=dp(12);root.addView(tip,tlp);
  ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
 }
 private TextView heading(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(18);t.setTextColor(0xFF172033);LinearLayout.LayoutParams p=lp();p.topMargin=dp(18);t.setLayoutParams(p);return t;}
 private void radio(RadioGroup g,String s,int tag,boolean checked){RadioButton b=new RadioButton(this);b.setText(s);b.setTag(tag);g.addView(b);if(checked)b.setChecked(true);}
 private void startCapture(){
  int a=(Integer)((RadioButton)findViewById(audio.getCheckedRadioButtonId())).getTag();
  if(a==2||a==3){Toast.makeText(this,"Internal game audio is not available in this build. Choose Microphone or Mute.",Toast.LENGTH_LONG).show();return;}
  if(a==1&&checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},72);return;}
  int q=(Integer)((RadioButton)findViewById(quality.getCheckedRadioButtonId())).getTag();getPreferences(0).edit().putInt("audio",a).putInt("quality",q).apply();
  MediaProjectionManager m=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);startActivityForResult(m.createScreenCaptureIntent(),CAPTURE);
 }
 protected void onActivityResult(int r,int code,Intent data){super.onActivityResult(r,code,data);if(r!=CAPTURE)return;if(code!=RESULT_OK||data==null){status.setText("Recording cancelled.");return;}
  Intent i=new Intent(this,ScreenRecordService.class);i.setAction(ScreenRecordService.START);i.putExtra(ScreenRecordService.CODE,code);i.putExtra(ScreenRecordService.DATA,data);i.putExtra(ScreenRecordService.AUDIO,getPreferences(0).getInt("audio",1));i.putExtra(ScreenRecordService.QUALITY,getPreferences(0).getInt("quality",720));startForegroundService(i);status.setText("Recording started. Stop from the notification.");
 }
 public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==72&&g.length>0&&g[0]==0)startCapture();else if(r==72)Toast.makeText(this,"Microphone denied. Choose Mute to record silently.",Toast.LENGTH_LONG).show();}
}