package com.tbt2.gamerecorder;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.content.ContentValues;
import android.hardware.display.*;
import android.media.MediaRecorder;
import android.media.projection.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;
import java.io.*;

public class ScreenRecordService extends Service {
 public static final String START="gr.start",STOP="gr.stop",PAUSE="gr.pause",RESUME="gr.resume",CODE="code",DATA="data",AUDIO="audio",QUALITY="quality";
 private static final String CHANNEL="game_recording"; private static final int NOTE_ID=814;
 private MediaRecorder rec; private MediaProjection projection; private VirtualDisplay vd;
 private Uri uri; private ParcelFileDescriptor pfd; private File file, audioFile;
 private AdvancedAudioRecorder advancedAudio; private boolean recording, advancedMode, paused; private int audioMode; private long maxBytes;
 public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Game recording",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
 public int onStartCommand(Intent i,int f,int id){
  if(i==null){stopSelf();return START_NOT_STICKY;}
  if(STOP.equals(i.getAction())){finishRecording();return START_NOT_STICKY;}
  if(PAUSE.equals(i.getAction())){pauseRecording();return START_STICKY;}
  if(RESUME.equals(i.getAction())){resumeRecording();return START_STICKY;}
  if(START.equals(i.getAction())){audioMode=i.getIntExtra(AUDIO,1);foreground("Preparing recorder",audioMode!=0);try{begin(i);}catch(Exception e){Toast.makeText(this,"Could not start: "+e.getMessage(),Toast.LENGTH_LONG).show();finishRecording();}}
  return START_NOT_STICKY;
 }
 private File privateFile(String prefix){File dir=new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES),"GameRecorder");if(!dir.exists())dir.mkdirs();return new File(dir,prefix+"_"+System.currentTimeMillis()+".mp4");}
 private void begin(Intent i)throws Exception{
  Intent data=i.getParcelableExtra(DATA);if(data==null)throw new IOException("Missing screen permission");
  DisplayMetrics dm=new DisplayMetrics();((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
  int sw=dm.widthPixels&~1,sh=dm.heightPixels&~1;int q=i.getIntExtra(QUALITY,720),w=sw,h=sh;
  if(q==720||q==480){int target=q;float s=Math.min(1f,target/(float)Math.max(sw,sh));w=Math.max(2,((int)(sw*s))&~1);h=Math.max(2,((int)(sh*s))&~1);}
  int bitrate=q==480?4000000:(q==720?7000000:12000000);
  File storage=getExternalFilesDir(Environment.DIRECTORY_MOVIES); if(storage==null)storage=getFilesDir(); long free=storage.getUsableSpace(); if(free<300L*1024*1024)throw new IOException("Less than 300 MB storage remains. Free space before recording."); maxBytes=Math.max(100L*1024*1024,free-200L*1024*1024);
  advancedMode=(audioMode==2||audioMode==3);
  if(advancedMode){file=privateFile("video_tmp");audioFile=privateFile("audio_tmp");}
  else {
   if(Build.VERSION.SDK_INT>=29){ContentValues v=new ContentValues();v.put(MediaStore.Video.Media.DISPLAY_NAME,"GameRecorder_"+System.currentTimeMillis()+".mp4");v.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");v.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/Game Recorder");v.put(MediaStore.Video.Media.IS_PENDING,1);uri=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,v);}
   if(uri!=null){pfd=getContentResolver().openFileDescriptor(uri,"w");if(pfd==null)throw new IOException("Cannot open output video");}
   else file=privateFile("GameRecorder");
  }
  rec=new MediaRecorder();if(audioMode==1)rec.setAudioSource(MediaRecorder.AudioSource.MIC);
  rec.setVideoSource(MediaRecorder.VideoSource.SURFACE);rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
  if(pfd!=null)rec.setOutputFile(pfd.getFileDescriptor());else rec.setOutputFile(file.getAbsolutePath());
  rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264);rec.setVideoSize(w,h);rec.setVideoFrameRate(30);rec.setVideoEncodingBitRate(bitrate);
  try{rec.setMaxFileSize(maxBytes);}catch(Exception ignored){}
  rec.setOnInfoListener((mr,what,extra)->{if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED){android.util.Log.w("GameRecorder","Reserved storage limit reached; stopping recording");finishRecording();}});
  if(audioMode==1){rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);rec.setAudioEncodingBitRate(128000);rec.setAudioSamplingRate(44100);}
  rec.prepare();projection=((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(i.getIntExtra(CODE,0),data);
  if(projection==null)throw new IOException("Screen capture permission missing");
  if(advancedMode){advancedAudio=new AdvancedAudioRecorder(projection,audioMode==3,audioFile);advancedAudio.start();}
  rec.start();vd=projection.createVirtualDisplay("GameRecorder",w,h,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,rec.getSurface(),null,null);
  recording=true;paused=false;getSharedPreferences("GameRecorder",MODE_PRIVATE).edit().putBoolean("recording_active",true).putBoolean("recording_paused",false).apply();if(getSharedPreferences("GameRecorder",MODE_PRIVATE).getBoolean("overlay_enabled",false))startService(new Intent(this,OverlayControlService.class).setAction(OverlayControlService.SHOW));update("Recording • Pause or Stop from notification");
 }
 private void foreground(String text,boolean needsAudio){
  Notification n=notification(text);
  if(Build.VERSION.SDK_INT>=29){int types=ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;if(Build.VERSION.SDK_INT>=30&&needsAudio)types|=ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;startForeground(NOTE_ID,n,types);}
  else startForeground(NOTE_ID,n);
 }
 private Notification notification(String text){
  Intent s=new Intent(this,ScreenRecordService.class);s.setAction(STOP);
  PendingIntent sp=PendingIntent.getService(this,9,s,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
  Intent open=new Intent(this,MainActivity.class);PendingIntent op=PendingIntent.getActivity(this,10,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
  Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
  Intent p=new Intent(this,ScreenRecordService.class);p.setAction(paused?RESUME:PAUSE);PendingIntent pp=PendingIntent.getService(this,11,p,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
  return b.setSmallIcon(android.R.drawable.presence_video_online).setContentTitle("Game Recorder").setContentText(text).setContentIntent(op).setOngoing(recording).addAction(new Notification.Action.Builder(null,paused?"Resume":"Pause",pp).build()).addAction(new Notification.Action.Builder(null,"Stop",sp).build()).build();
 }
 private void update(String t){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTE_ID,notification(t));}
 private void pauseRecording(){if(!recording||paused||rec==null)return;try{rec.pause();if(advancedAudio!=null)advancedAudio.pause();paused=true;getSharedPreferences("GameRecorder",MODE_PRIVATE).edit().putBoolean("recording_paused",true).apply();if(getSharedPreferences("GameRecorder",MODE_PRIVATE).getBoolean("overlay_enabled",false))startService(new Intent(this,OverlayControlService.class).setAction(OverlayControlService.SHOW));update("Recording paused");}catch(Exception e){Toast.makeText(this,"Could not pause: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
 private void resumeRecording(){if(!recording||!paused||rec==null)return;try{rec.resume();if(advancedAudio!=null)advancedAudio.resume();paused=false;getSharedPreferences("GameRecorder",MODE_PRIVATE).edit().putBoolean("recording_paused",false).apply();if(getSharedPreferences("GameRecorder",MODE_PRIVATE).getBoolean("overlay_enabled",false))startService(new Intent(this,OverlayControlService.class).setAction(OverlayControlService.SHOW));update("Recording resumed");}catch(Exception e){Toast.makeText(this,"Could not resume: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
 private void finishRecording(){
  boolean had=rec!=null;
  boolean videoFinalized=false;
  if(vd!=null){try{vd.release();}catch(Exception ignored){}vd=null;}
  if(rec!=null){
   try{rec.stop();videoFinalized=true;}catch(RuntimeException e){android.util.Log.e("GameRecorder","Recorder stopped before a valid video was finalized",e);}
   try{rec.reset();}catch(Exception ignored){}try{rec.release();}catch(Exception ignored){}rec=null;
  }
  if(pfd!=null){try{pfd.close();}catch(IOException ignored){}pfd=null;}
  if(advancedAudio!=null){try{advancedAudio.stop();}catch(Exception e){android.util.Log.e("GameRecorder","Audio recorder cleanup failed",e);}advancedAudio=null;}
  if(projection!=null){try{projection.stop();}catch(Exception ignored){}projection=null;}
  boolean saved=false;
  try{
   if(videoFinalized&&advancedMode&&file!=null&&file.exists()&&file.length()>0){
    File merged=privateFile("merged");
    boolean mergedOk=false;
    try{Mp4Merger.merge(file,audioFile,merged);mergedOk=merged.exists()&&merged.length()>0;}catch(Exception e){android.util.Log.e("GameRecorder","Audio mux failed; preserving video-only recording",e);}
    if(mergedOk){
     try{uri=Mp4Merger.publish(this,merged);saved=true;}
     catch(Exception publishError){android.util.Log.e("GameRecorder","Merged export could not be published; trying video-only copy",publishError);uri=publishVideoOnly(file);saved=uri!=null;}
    } else {uri=publishVideoOnly(file);saved=uri!=null;}
    if(merged.exists())merged.delete();
   } else if(videoFinalized&&uri!=null){
    if(isUriVideoNonEmpty(uri)){
     ContentValues ready=new ContentValues();ready.put(MediaStore.Video.Media.IS_PENDING,0);getContentResolver().update(uri,ready,null,null);saved=true;
    } else {getContentResolver().delete(uri,null,null);uri=null;}
   } else if(videoFinalized&&file!=null&&file.exists()&&file.length()>0){
    android.media.MediaScannerConnection.scanFile(this,new String[]{file.getAbsolutePath()},new String[]{"video/mp4"},null);saved=true;
   } else if(uri!=null){
    // Remove the pending MediaStore row if encoder preparation or recording failed.
    getContentResolver().delete(uri,null,null);uri=null;
   }
  }catch(Exception e){android.util.Log.e("GameRecorder","Could not finalize recording",e);Toast.makeText(this,"Recording could not be finalized: "+e.getMessage(),Toast.LENGTH_LONG).show();}
  if(advancedMode&&file!=null&&saved)file.delete();
  if(audioFile!=null)audioFile.delete();
  if(getSharedPreferences("GameRecorder",MODE_PRIVATE).getBoolean("overlay_enabled",false)&&android.provider.Settings.canDrawOverlays(this))
   startService(new Intent(this,OverlayControlService.class).setAction(OverlayControlService.SHOW));
  recording=false;paused=false;getSharedPreferences("GameRecorder",MODE_PRIVATE).edit().putBoolean("recording_active",false).putBoolean("recording_paused",false).apply();stopForeground(true);stopSelf();
  if(had&&saved)Toast.makeText(this,"Recording saved in Movies / Game Recorder",Toast.LENGTH_LONG).show();
  else if(had&&!saved)Toast.makeText(this,"Recording failed or was too short to save. Try recording a few seconds and stop again.",Toast.LENGTH_LONG).show();
  file=null;audioFile=null;uri=null;advancedMode=false;
 }
 private boolean isUriVideoNonEmpty(Uri target){
  try(android.database.Cursor c=getContentResolver().query(target,new String[]{MediaStore.Video.Media.SIZE},null,null,null)){
   return c!=null&&c.moveToFirst()&&c.getLong(0)>0;
  }catch(Exception e){return false;}
 }
 private Uri publishVideoOnly(File source)throws Exception{
  if(Build.VERSION.SDK_INT>=29){
   ContentValues v=new ContentValues();v.put(MediaStore.Video.Media.DISPLAY_NAME,"GameRecorder_"+System.currentTimeMillis()+".mp4");v.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");v.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/Game Recorder");v.put(MediaStore.Video.Media.IS_PENDING,1);
   Uri dest=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,v);if(dest==null)throw new IOException("Cannot create video entry");
   try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(dest,"w")){if(out==null)throw new IOException("Cannot write video");byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
   ContentValues ready=new ContentValues();ready.put(MediaStore.Video.Media.IS_PENDING,0);getContentResolver().update(dest,ready,null,null);return dest;
  }
  android.media.MediaScannerConnection.scanFile(this,new String[]{source.getAbsolutePath()},new String[]{"video/mp4"},null);return Uri.fromFile(source);
 }
 public void onDestroy(){if(rec!=null||projection!=null||advancedAudio!=null)finishRecording();super.onDestroy();}
 public IBinder onBind(Intent i){return null;}
}