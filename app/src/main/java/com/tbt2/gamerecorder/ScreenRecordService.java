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
 public static final String START="gr.start",STOP="gr.stop",CODE="code",DATA="data",AUDIO="audio",QUALITY="quality";
 private static final String CHANNEL="game_recording"; private static final int NOTE_ID=814;
 private MediaRecorder rec; private MediaProjection projection; private VirtualDisplay vd;
 private Uri uri; private ParcelFileDescriptor pfd; private File file, audioFile;
 private AdvancedAudioRecorder advancedAudio; private boolean recording, advancedMode; private int audioMode;
 public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Game recording",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
 public int onStartCommand(Intent i,int f,int id){
  if(i==null){stopSelf();return START_NOT_STICKY;}
  if(STOP.equals(i.getAction())){finishRecording();return START_NOT_STICKY;}
  if(START.equals(i.getAction())){audioMode=i.getIntExtra(AUDIO,1);foreground("Preparing recorder",audioMode!=0);try{begin(i);}catch(Exception e){Toast.makeText(this,"Could not start: "+e.getMessage(),Toast.LENGTH_LONG).show();finishRecording();}}
  return START_NOT_STICKY;
 }
 private File privateFile(String prefix){File dir=new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES),"GameRecorder");if(!dir.exists())dir.mkdirs();return new File(dir,prefix+"_"+System.currentTimeMillis()+".mp4");}
 private void begin(Intent i)throws Exception{
  Intent data=i.getParcelableExtra(DATA);if(data==null)throw new IOException("Missing screen permission");
  DisplayMetrics dm=new DisplayMetrics();((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
  int sw=dm.widthPixels&~1,sh=dm.heightPixels&~1;int q=i.getIntExtra(QUALITY,720),w=sw,h=sh;
  if(q==720||q==360){int target=q==360?360:720;float s=Math.min(1f,target/(float)Math.max(sw,sh));w=Math.max(2,((int)(sw*s))&~1);h=Math.max(2,((int)(sh*s))&~1);}
  int bitrate=q==360?3000000:(q==1080?12000000:6000000);
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
  if(audioMode==1){rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);rec.setAudioEncodingBitRate(128000);rec.setAudioSamplingRate(44100);}
  rec.prepare();projection=((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(i.getIntExtra(CODE,0),data);
  if(projection==null)throw new IOException("Screen capture permission missing");
  if(advancedMode){advancedAudio=new AdvancedAudioRecorder(projection,audioMode==3,audioFile);advancedAudio.start();}
  rec.start();vd=projection.createVirtualDisplay("GameRecorder",w,h,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,rec.getSurface(),null,null);
  recording=true;update("Recording screen • tap Stop when finished");
 }
 private void foreground(String text,boolean needsAudio){
  Notification n=notification(text);
  if(Build.VERSION.SDK_INT>=29){int types=ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;if(needsAudio)types|=ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;startForeground(NOTE_ID,n,types);}
  else startForeground(NOTE_ID,n);
 }
 private Notification notification(String text){
  Intent s=new Intent(this,ScreenRecordService.class);s.setAction(STOP);
  PendingIntent sp=PendingIntent.getService(this,9,s,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
  Intent open=new Intent(this,MainActivity.class);PendingIntent op=PendingIntent.getActivity(this,10,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
  Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
  return b.setSmallIcon(android.R.drawable.presence_video_online).setContentTitle("Game Recorder").setContentText(text).setContentIntent(op).setOngoing(recording).addAction(new Notification.Action.Builder(null,"Stop",sp).build()).build();
 }
 private void update(String t){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTE_ID,notification(t));}
 private void finishRecording(){
  boolean had=rec!=null;
  if(vd!=null){vd.release();vd=null;}
  if(rec!=null){try{rec.stop();}catch(RuntimeException ignored){}try{rec.reset();}catch(Exception ignored){}try{rec.release();}catch(Exception ignored){}rec=null;}
  if(pfd!=null){try{pfd.close();}catch(IOException ignored){}pfd=null;}
  if(advancedAudio!=null){try{advancedAudio.stop();}catch(Exception ignored){}advancedAudio=null;}
  if(projection!=null){try{projection.stop();}catch(Exception ignored){}projection=null;}
  try{
   if(advancedMode&&file!=null&&file.exists()&&file.length()>0){
    File merged=privateFile("merged");
    boolean mergedOk=false;
    try{Mp4Merger.merge(file,audioFile,merged);mergedOk=merged.exists()&&merged.length()>0;}catch(Exception e){android.util.Log.e("GameRecorder","Audio mux failed; preserving video-only recording",e);}
    if(mergedOk){uri=Mp4Merger.publish(this,merged);merged.delete();}
    else {uri=publishVideoOnly(file);}
   } else if(uri!=null){
    ContentValues ready=new ContentValues();ready.put(MediaStore.Video.Media.IS_PENDING,0);getContentResolver().update(uri,ready,null,null);
   } else if(file!=null&&file.exists()){
    android.media.MediaScannerConnection.scanFile(this,new String[]{file.getAbsolutePath()},new String[]{"video/mp4"},null);
   }
  }catch(Exception e){android.util.Log.e("GameRecorder","Could not finalize recording",e);Toast.makeText(this,"Recording could not be finalized: "+e.getMessage(),Toast.LENGTH_LONG).show();}
  if(advancedMode&&file!=null)file.delete();if(audioFile!=null)audioFile.delete();
  recording=false;stopForeground(true);stopSelf();
  if(had)Toast.makeText(this,"Recording saved in Movies / Game Recorder",Toast.LENGTH_LONG).show();
  file=null;audioFile=null;uri=null;advancedMode=false;
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