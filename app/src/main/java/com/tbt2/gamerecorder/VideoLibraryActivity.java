package com.tbt2.gamerecorder;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.VideoEncoderSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;

/** Lists screen recordings and provides a lower-bitrate export. */
public class VideoLibraryActivity extends Activity {
    private LinearLayout list;
    private TextView status;
    private Transformer transformer;
    private Uri activeUri;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView title = new TextView(this);
        title.setText("Your recordings");
        title.setTextSize(24);
        root.addView(title);
        TextView help = new TextView(this);
        help.setText("Tap Play to watch a recording. Compress creates a smaller, re-encoded copy; it can reduce image quality. Original files are kept.");
        root.addView(help);
        status = new TextView(this);
        status.setPadding(0, dp(10), 0, dp(10));
        root.addView(status);
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Button refresh = new Button(this);
        refresh.setText("Refresh recordings");
        refresh.setOnClickListener(v -> loadVideos());
        root.addView(refresh);
        setContentView(root);
        loadVideos();
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    private void loadVideos() {
        list.removeAllViews();
        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = { MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATE_ADDED };
        String selection = null;
        String[] args = null;
        if (Build.VERSION.SDK_INT >= 29) {
            selection = MediaStore.Video.Media.RELATIVE_PATH + "=?";
            args = new String[] { Environment.DIRECTORY_MOVIES + "/Game Recorder/" };
        }
        int count = 0;
        try (Cursor c = getContentResolver().query(collection, projection, selection, args,
                MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (c != null) while (c.moveToNext()) {
                long id = c.getLong(0);
                String name = c.getString(1);
                long size = c.getLong(2);
                long date = c.getLong(3);
                Uri uri = Uri.withAppendedPath(collection, Long.toString(id));
                addVideo(name, size, date, uri);
                count++;
            }
        } catch (Exception e) {
            status.setText("Could not load recordings: " + e.getMessage());
            return;
        }
        status.setText(count == 0 ? "No recordings found yet. Record a video first." : count + " recording(s).");
    }

    private void addVideo(String name, long size, long date, Uri uri) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        TextView info = new TextView(this);
        info.setText(name + "\n" + bytes(size) + " • " + DateFormat.getDateTimeInstance().format(new Date(date * 1000)));
        info.setTextSize(15);
        card.addView(info);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button play = new Button(this);
        play.setText("Play");
        play.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "video/mp4");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { startActivity(i); } catch (Exception e) { Toast.makeText(this, "No video player found.", Toast.LENGTH_LONG).show(); }
        });
        Button compress = new Button(this);
        compress.setText("Compress copy");
        compress.setOnClickListener(v -> compress(uri, name));
        actions.addView(play, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(compress, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(actions);
        View divider = new View(this);
        divider.setBackgroundColor(0xFFDDDDDD);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(1));
        dlp.topMargin = dp(6);
        card.addView(divider, dlp);
        list.addView(card);
    }

    private String bytes(long n) {
        if (n >= 1024L*1024*1024) return String.format(java.util.Locale.US, "%.2f GB", n/(1024d*1024*1024));
        if (n >= 1024L*1024) return String.format(java.util.Locale.US, "%.1f MB", n/(1024d*1024));
        return String.format(java.util.Locale.US, "%.0f KB", n/1024d);
    }

    private void compress(Uri source, String sourceName) {
        if (transformer != null) {
            Toast.makeText(this, "A compression is already running.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "GameRecorder");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create temporary folder");
            File output = new File(dir, "compressed_" + System.currentTimeMillis() + ".mp4");
            transformer = new Transformer.Builder(this)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setEncoderFactory(new DefaultEncoderFactory.Builder(this)
                            .setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder().setBitrate(2500000).build())
                            .build())
                    .addListener(new Transformer.Listener() {
                        @Override public void onCompleted(Composition composition, ExportResult result) {
                            Transformer done = transformer;
                            transformer = null;
                            try {
                                if (!output.exists() || output.length() == 0) throw new IllegalStateException("The encoder produced an empty file");
                                Uri saved = publish(output, sourceName);
                                long outSize = output.length();
                                output.delete();
                                status.setText("Compression finished. Saved copy (" + bytes(outSize) + "). Original retained.");
                                Toast.makeText(VideoLibraryActivity.this, "Compressed copy saved to Movies/Game Recorder.", Toast.LENGTH_LONG).show();
                                loadVideos();
                            } catch (Exception e) {
                                output.delete();
                                status.setText("Could not save compressed video: " + e.getMessage());
                            }
                        }
                        @Override public void onError(Composition composition, ExportResult result, ExportException error) {
                            transformer = null;
                            output.delete();
                            status.setText("Compression failed: " + error.getMessage());
                        }
                    }).build();
            transformer.start(MediaItem.fromUri(source), output.getAbsolutePath());
            status.setText("Compressing… Keep the app open until export finishes.");
        } catch (Exception e) {
            transformer = null;
            status.setText("Could not start compression: " + e.getMessage());
        }
    }

    private Uri publish(File file, String originalName) throws Exception {
        ContentValues values = new ContentValues();
        String base = originalName == null ? "GameRecorder" : originalName.replaceAll("\\.[^.]+$", "");
        values.put(MediaStore.Video.Media.DISPLAY_NAME, base + "_compressed_" + System.currentTimeMillis() + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Game Recorder");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
        }
        Uri dest = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (dest == null) throw new IllegalStateException("Could not create output video");
        try (InputStream in = new FileInputStream(file);
             OutputStream out = getContentResolver().openOutputStream(dest, "w")) {
            if (out == null) throw new IllegalStateException("Could not write output video");
            byte[] b = new byte[65536]; int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
        } catch (Exception e) {
            getContentResolver().delete(dest, null, null);
            throw e;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues ready = new ContentValues(); ready.put(MediaStore.Video.Media.IS_PENDING, 0);
            getContentResolver().update(dest, ready, null, null);
        }
        return dest;
    }

    @Override protected void onDestroy() {
        if (transformer != null) {
            try { transformer.cancel(); } catch (Exception ignored) {}
            transformer = null;
        }
        super.onDestroy();
    }
}