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
 private static final String CHANNEL="game_recording";private MediaRecorder rec;private MediaProjection projection;private VirtualDisplay vd;private Uri uri;private ParcelFileDescriptor pfd;private File file;private boolean recording;
 public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Game recording",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
 public int onStartCommand(Intent i,int f,int id){if(i==null){stopSelf();return START_NOT_STICKY;}if(STOP.equals(i.getAction())){finishRecording();return START_NOT_STICKY;}if(START.equals(i.getAction())){foreground("Preparing recorder");try{begin(i);}catch(Exception e){Toast.makeText(this,"Could not start: "+e.getMessage(),Toast.LENGTH_LONG).show();finishRecording();}}return START_NOT_STICKY;}
 private void begin(Intent i)throws Exception{
  Intent data=i.getParcelableExtra(DATA);if(data==null)throw new IOException("Missing screen permission");
  DisplayMetrics dm=new DisplayMetrics();((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);int sw=dm.widthPixels&~1,sh=dm.heightPixels&~1;
  int q=i.getIntExtra(QUALITY,720),w=sw,h=sh;if(q==720||q==360){float s=Math.min(1f,q/(float)Math.max(sw,sh));w=Math.max(2,((int)(sw*s))&~1);h=Math.max(2,((int)(sh*s))&~1);}int bitrate=q==360?3000000:(q==1080?12000000:6000000);
  int a=i.getIntExtra(AUDIO,1);if(Build.VERSION.SDK_INT>=29){ContentValues v=new ContentValues();v.put(MediaStore.Video.Media.DISPLAY_NAME,"GameRecorder_"+System.currentTimeMillis()+".mp4");v.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");v.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/Game Recorder");v.put(MediaStore.Video.Media.IS_PENDING,1);uri=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,v);}
  rec=new MediaRecorder();if(a==1)rec.setAudioSource(MediaRecorder.AudioSource.MIC);rec.setVideoSource(MediaRecorder.VideoSource.SURFACE);rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
  if(uri!=null){pfd=getContentResolver().openFileDescriptor(uri,"w");if(pfd==null)throw new IOException("Cannot open output video");rec.setOutputFile(pfd.getFileDescriptor());}
  else{file=new File(new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES),"GameRecorder"),"GameRecorder_"+System.currentTimeMillis()+".mp4");File d=file.getParentFile();if(!d.exists()&&!d.mkdirs())throw new IOException("Cannot create output folder");rec.setOutputFile(file.getAbsolutePath());}
  rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264);rec.setVideoSize(w,h);rec.setVideoFrameRate(30);rec.setVideoEncodingBitRate(bitrate);if(a==1){rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);rec.setAudioEncodingBitRate(128000);rec.setAudioSamplingRate(44100);}rec.prepare();
  projection=((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(i.getIntExtra(CODE,0),data);if(projection==null)throw new IOException("Screen capture permission missing");
  rec.start();vd=projection.createVirtualDisplay("GameRecorder",w,h,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,rec.getSurface(),null,null);recording=true;update("Recording screen • tap Stop when finished");
 }
 private void foreground(String text){Notification n=notification(text);if(Build.VERSION.SDK_INT>=29)startForeground(814,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION|(Build.VERSION.SDK_INT>=30?ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE:0));else startForeground(814,n);}
 private Notification notification(String text){Intent s=new Intent(this,ScreenRecordService.class);s.setAction(STOP);PendingIntent sp=PendingIntent.getService(this,9,s,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));Intent open=new Intent(this,MainActivity.class);PendingIntent op=PendingIntent.getActivity(this,10,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return b.setSmallIcon(android.R.drawable.presence_video_online).setContentTitle("Game Recorder").setContentText(text).setContentIntent(op).setOngoing(recording).addAction(new Notification.Action.Builder(null,"Stop",sp).build()).build();}
 private void update(String t){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(814,notification(t));}
 private void finishRecording(){boolean had=rec!=null;if(vd!=null){vd.release();vd=null;}if(projection!=null){projection.stop();projection=null;}if(rec!=null){try{rec.stop();}catch(RuntimeException ignored){}try{rec.reset();}catch(Exception ignored){}try{rec.release();}catch(Exception ignored){}rec=null;}if(pfd!=null){try{pfd.close();}catch(IOException ignored){}pfd=null;}if(uri!=null){ContentValues v=new ContentValues();v.put(MediaStore.Video.Media.IS_PENDING,0);try{getContentResolver().update(uri,v,null,null);}catch(Exception ignored){}uri=null;}if(file!=null){android.media.MediaScannerConnection.scanFile(this,new String[]{file.getAbsolutePath()},new String[]{"video/mp4"},null);file=null;}recording=false;stopForeground(true);stopSelf();if(had)Toast.makeText(this,"Saved to Movies / Game Recorder",Toast.LENGTH_LONG).show();}
 public void onDestroy(){if(rec!=null||projection!=null)finishRecording();super.onDestroy();}public IBinder onBind(Intent i){return null;}
}