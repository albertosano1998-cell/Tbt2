package com.tbt2.gamerecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import com.alphacephei.vosk.Model;
import com.alphacephei.vosk.Recognizer;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.common.model.DownloadConditions;

import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LiveSubtitleService extends Service {
    public static final String START="subtitle.start", STOP="subtitle.stop", RESULT_CODE="result_code", RESULT_DATA="result_data";
    private static final String CHANNEL="live_subtitles";
    private static final int NOTIFICATION_ID=731;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean running=false;
    private MediaProjection projection;
    private AudioRecord recorder;
    private Model model;
    private Recognizer recognizer;
    private Translator translator;
    private WindowManager windowManager;
    private TextView subtitle;
    private android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());

    @Override public void onCreate(){super.onCreate();windowManager=(WindowManager)getSystemService(WINDOW_SERVICE);createChannel();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(intent==null||!START.equals(intent.getAction()))return START_NOT_STICKY;
        startForeground(NOTIFICATION_ID,notification("Preparing speech models…"));
        int result=intent.getIntExtra(RESULT_CODE,0);
        Intent data;
        if(Build.VERSION.SDK_INT>=33)data=intent.getParcelableExtra(RESULT_DATA,Intent.class);else data=intent.getParcelableExtra(RESULT_DATA);
        if(data==null){showText("Missing screen/audio permission result");stopSelf();return START_NOT_STICKY;}
        try{
            MediaProjectionManager mgr=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=mgr.getMediaProjection(result,data);
            if(projection==null){showText("Android did not provide audio capture");stopSelf();return START_NOT_STICKY;}
            projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){stopSelf();}},main);
            showOverlay();
            worker.execute(()->prepareAndCapture());
        }catch(Exception e){showText("Could not start capture: "+e.getMessage());stopSelf();}
        return START_NOT_STICKY;
    }
    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Live subtitles",NotificationManager.IMPORTANCE_LOW);c.setDescription("Shown while live subtitles are active");((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}
    }
    private Notification notification(String message){
        Intent open=new Intent(this,TranslatorActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setContentTitle("Live Japanese subtitles").setContentText(message).setSmallIcon(android.R.drawable.ic_menu_view).setContentIntent(pi).setOngoing(true);
        return b.build();
    }
    private void updateNotification(String message){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID,notification(message));}
    private void showOverlay(){
        if(!Settings.canDrawOverlays(this)){showText("Enable display-over-other-apps permission");return;}
        main.post(()->{
            if(subtitle!=null)return;
            float d=getResources().getDisplayMetrics().density;
            subtitle=new TextView(this);subtitle.setText("Preparing Japanese subtitles…");subtitle.setTextColor(Color.WHITE);subtitle.setTextSize(18);subtitle.setGravity(Gravity.CENTER);subtitle.setPadding((int)(12*d),(int)(8*d),(int)(12*d),(int)(8*d));
            GradientDrawable bg=new GradientDrawable();bg.setColor(0xD9000000);bg.setCornerRadius(10*d);subtitle.setBackground(bg);subtitle.setMaxLines(3);
            int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams p=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
            p.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;p.y=(int)(34*d);
            try{windowManager.addView(subtitle,p);}catch(Exception e){subtitle=null;}
        });
    }
    private void showText(String text){main.post(()->{if(subtitle!=null)subtitle.setText(text);});}
    private void prepareAndCapture(){
        try{
            File modelDir=new File(getFilesDir(),"vosk-model-small-ja-0.22");
            if(!new File(modelDir,"conf").exists())downloadAndUnzipModel(modelDir);
            File actual=findModelDirectory(modelDir);
            model=new Model(actual.getAbsolutePath());
            recognizer=new Recognizer(model,16000.0f);
            TranslatorOptions options=new TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.JAPANESE).setTargetLanguage(TranslateLanguage.ENGLISH).build();
            translator=Translation.getClient(options);
            translator.downloadModelIfNeeded(new DownloadConditions.Builder().build()).addOnSuccessListener(v->{
                updateNotification("Listening for Japanese speech");
                showText("Listening for Japanese speech…");
                worker.execute(()->captureLoop());
            }).addOnFailureListener(e->{showText("Translation model download failed. Connect to internet and retry.");updateNotification("Translation model download failed");stopSelf();});
        }catch(Exception e){showText("Model setup failed: "+e.getMessage());updateNotification("Model setup failed");stopSelf();}
    }
    private void downloadAndUnzipModel(File target)throws Exception{
        showText("Downloading Japanese speech model (large download)…");
        URL url=new URL("https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(20000);c.setReadTimeout(60000);c.connect();
        if(c.getResponseCode()!=200)throw new IllegalStateException("Model download HTTP "+c.getResponseCode());
        File zip=new File(getCacheDir(),"vosk-ja.zip");
        try(InputStream in=new BufferedInputStream(c.getInputStream());FileOutputStream out=new FileOutputStream(zip)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){out.write(b,0,n);}}
        c.disconnect();
        File root=new File(getFilesDir(),"vosk-model-small-ja-0.22");if(root.exists())deleteTree(root);root.mkdirs();
        try(ZipInputStream zis=new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(zip)))){
            ZipEntry entry;byte[] buf=new byte[32768];
            while((entry=zis.getNextEntry())!=null){
                File out=new File(root,entry.getName());
                if(!out.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))throw new SecurityException("Invalid model archive");
                if(entry.isDirectory())out.mkdirs();else{File parent=out.getParentFile();if(parent!=null)parent.mkdirs();try(FileOutputStream fos=new FileOutputStream(out)){int n;while((n=zis.read(buf))!=-1)fos.write(buf,0,n);}}
                zis.closeEntry();
            }
        }
        zip.delete();
    }
    private File findModelDirectory(File root){
        if(new File(root,"conf").exists())return root;
        File[] children=root.listFiles();if(children!=null)for(File f:children)if(f.isDirectory()&&new File(f,"conf").exists())return f;
        return root;
    }
    private void deleteTree(File f){if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File c:children)deleteTree(c);}f.delete();}
    private void captureLoop(){
        AudioRecord local=null;
        try{
            AudioPlaybackCaptureConfiguration config=new AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).build();
            AudioFormat format=new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
            int min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            local=new AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(Math.max(min*4,6400)).setAudioPlaybackCaptureConfig(config).build();
            if(local.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Audio capture unavailable");
            recorder=local;running=true;local.startRecording();
            byte[] buffer=new byte[3200];
            while(running){
                int n=local.read(buffer,0,buffer.length);
                if(n<=0)continue;
                if(recognizer.acceptWaveForm(buffer,n)){
                    String json=recognizer.getResult();String text=new JSONObject(json).optString("text","");
                    if(!text.trim().isEmpty())translate(text,false);
                }else{
                    String json=recognizer.getPartialResult();String partial=new JSONObject(json).optString("partial","");
                    if(!partial.trim().isEmpty())showText("Listening…\n"+partial);
                }
            }
        }catch(Exception e){showText("Audio capture failed or blocked by this app. Try a video player that allows internal audio capture. "+e.getMessage());updateNotification("Audio capture unavailable");}
        finally{running=false;if(local!=null){try{local.stop();}catch(Exception ignored){}local.release();}recorder=null;}
    }
    private void translate(String japanese,boolean ignored){
        if(translator==null)return;
        showText(japanese+"\nTranslating…");
        translator.translate(japanese).addOnSuccessListener(english->showText(japanese+"\n"+english)).addOnFailureListener(e->showText(japanese+"\n[Translation failed]"));
    }
    @Override public void onDestroy(){
        running=false;
        if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}}
        if(projection!=null){try{projection.stop();}catch(Exception ignored){}}
        if(translator!=null)translator.close();
        if(recognizer!=null)recognizer.close();
        if(model!=null)model.close();
        main.post(()->{if(subtitle!=null){try{windowManager.removeView(subtitle);}catch(Exception ignored){}subtitle=null;}});
        worker.shutdownNow();
        stopForeground(true);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
