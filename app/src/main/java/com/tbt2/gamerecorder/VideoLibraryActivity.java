package com.tbt2.gamerecorder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaItem.ClippingConfiguration;
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
        compress.setText("Compress");
        compress.setOnClickListener(v -> compress(uri, name));
        Button trim = new Button(this);
        trim.setText("Trim");
        trim.setOnClickListener(v -> showTrimDialog(uri, name));
        actions.addView(play, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(compress, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(trim, new LinearLayout.LayoutParams(0, -2, 1));
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

    private void showTrimDialog(Uri source, String sourceName) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(20), dp(8), dp(20), dp(4));
        EditText start = new EditText(this);
        start.setHint("Start time in seconds (e.g. 0)");
        start.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText end = new EditText(this);
        end.setHint("End time in seconds (e.g. 30)");
        end.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        fields.addView(start);
        fields.addView(end);
        new AlertDialog.Builder(this).setTitle("Trim recording")
                .setMessage("Enter the start and end time in seconds. The original video will be kept.")
                .setView(fields)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Export trimmed copy", (dialog, which) -> {
                    try {
                        double from = Double.parseDouble(start.getText().toString().trim());
                        double to = Double.parseDouble(end.getText().toString().trim());
                        if (from < 0 || to <= from) throw new IllegalArgumentException("End time must be greater than start time.");
                        exportTrim(source, sourceName, Math.round(from * 1000), Math.round(to * 1000));
                    } catch (Exception e) {
                        Toast.makeText(this, "Enter valid start and end times.", Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void exportTrim(Uri source, String sourceName, long fromMs, long toMs) {
        if (transformer != null) {
            Toast.makeText(this, "Another export is already running.", Toast.LENGTH_SHORT).show();
            return;
        }
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "GameRecorder");
        if (!dir.exists()) dir.mkdirs();
        File output = new File(dir, "trimmed_" + System.currentTimeMillis() + ".mp4");
        try {
            ClippingConfiguration clip = new ClippingConfiguration.Builder()
                    .setStartPositionMs(fromMs).setEndPositionMs(toMs).build();
            MediaItem item = new MediaItem.Builder().setUri(source).setClippingConfiguration(clip).build();
            transformer = new Transformer.Builder(this).addListener(new Transformer.Listener() {
                @Override public void onCompleted(Composition composition, ExportResult result) {
                    transformer = null;
                    try {
                        if (!output.exists() || output.length() == 0) throw new IllegalStateException("Empty export");
                        publish(output, sourceName, "trimmed");
                        output.delete();
                        status.setText("Trimmed copy saved. Original kept.");
                        loadVideos();
                    } catch (Exception e) {
                        output.delete();
                        status.setText("Could not save trimmed video: " + e.getMessage());
                    }
                }
                @Override public void onError(Composition composition, ExportResult result, ExportException error) {
                    transformer = null;
                    output.delete();
                    status.setText("Trim failed: " + error.getMessage());
                }
            }).build();
            transformer.start(item, output.getAbsolutePath());
            status.setText("Trimming video… Keep the app open until export finishes.");
        } catch (Exception e) {
            transformer = null;
            output.delete();
            status.setText("Could not start trim: " + e.getMessage());
        }
    }

    private void compress(Uri source, String sourceName) {
        final int[] bitrates = {1200000, 2500000, 5000000};
        final long durationMs = getDurationMs(source);
        final String[] labels = {
                "Compact — smallest file" + estimateSuffix(durationMs, bitrates[0]),
                "Balanced — recommended" + estimateSuffix(durationMs, bitrates[1]),
                "High quality — larger file" + estimateSuffix(durationMs, bitrates[2])
        };
        new AlertDialog.Builder(this)
                .setTitle("Compress video")
                .setMessage("Choose an export profile. Estimates include approximately 128 kbps audio. Re-encoding can reduce quality; the original is always kept.")
                .setSingleChoiceItems(labels, 1, (dialog, which) -> {
                    dialog.dismiss();
                    compressAtBitrate(source, sourceName, bitrates[which], labels[which]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String estimateSuffix(long durationMs, int videoBitrate) {
        if (durationMs <= 0) return "";
        long estimatedBytes = (long) ((videoBitrate + 128000L) * (durationMs / 1000d) / 8d);
        return " • approx. " + bytes(estimatedBytes);
    }

    private long getDurationMs(Uri source) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, source);
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return value == null ? -1 : Long.parseLong(value);
        } catch (Exception ignored) {
            return -1;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }

    private void compressAtBitrate(Uri source, String sourceName, int bitrate, String profile) {
        if (transformer != null) {
            Toast.makeText(this, "An export is already running.", Toast.LENGTH_SHORT).show();
            return;
        }
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "GameRecorder");
        if (!dir.exists() && !dir.mkdirs()) {
            status.setText("Could not create temporary export folder.");
            return;
        }
        File output = new File(dir, "compressed_" + System.currentTimeMillis() + ".mp4");
        try {
            transformer = new Transformer.Builder(this)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setEncoderFactory(new DefaultEncoderFactory.Builder(this)
                            .setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                            .build())
                    .addListener(new Transformer.Listener() {
                        @Override public void onCompleted(Composition composition, ExportResult result) {
                            transformer = null;
                            try {
                                if (!output.exists() || output.length() == 0)
                                    throw new IllegalStateException("The encoder produced an empty file");
                                long outSize = output.length();
                                publish(output, sourceName, "compressed_" + profile.toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9]+", "_"));
                                output.delete();
                                status.setText(profile + " compression finished. Output: " + bytes(outSize)
                                        + ". Original retained.");
                                Toast.makeText(VideoLibraryActivity.this, "Compressed copy saved to Movies/Game Recorder.",
                                        Toast.LENGTH_LONG).show();
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
            status.setText("Compressing (" + profile + ")… Keep the app open until export finishes.");
        } catch (Exception e) {
            transformer = null;
            output.delete();
            status.setText("Could not start compression: " + e.getMessage());
        }
    }

    private Uri publish(File file, String originalName, String operation) throws Exception {
        ContentValues values = new ContentValues();
        String base = originalName == null ? "GameRecorder" : originalName.replaceAll("\\.[^.]+$", "");
        values.put(MediaStore.Video.Media.DISPLAY_NAME, base + "_" + operation + "_" + System.currentTimeMillis() + ".mp4");
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