package com.tbt2.gamerecorder;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Merges the video-only and AAC-only MP4s without re-encoding. */
public final class Mp4Merger {
    private Mp4Merger() {}
    private static final class Track {
        MediaExtractor extractor;
        int sourceTrack;
        int outputTrack;
        ByteBuffer buffer = ByteBuffer.allocate(2 * 1024 * 1024);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long time;
        boolean done;
    }

    public static void merge(File video, File audio, File output) throws Exception {
        MediaExtractor ve = new MediaExtractor();
        MediaExtractor ae = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean started = false;
        List<Track> tracks = new ArrayList<>();
        try {
            ve.setDataSource(video.getAbsolutePath());
            ae.setDataSource(audio.getAbsolutePath());
            muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            addTracks(ve, muxer, tracks);
            if (audio.exists() && audio.length() > 0) addTracks(ae, muxer, tracks);
            if (tracks.isEmpty()) throw new IllegalStateException("No playable tracks were recorded");
            muxer.start(); started = true;
            for (Track t : tracks) advance(t);
            while (true) {
                Track next = null;
                for (Track t : tracks) if (!t.done && (next == null || t.time < next.time)) next = t;
                if (next == null) break;
                next.buffer.clear();
                int size = next.extractor.readSampleData(next.buffer, 0);
                if (size < 0) { next.done = true; continue; }
                next.info.set(0, size, next.time, next.extractor.getSampleFlags());
                muxer.writeSampleData(next.outputTrack, next.buffer, next.info);
                next.extractor.advance();
                advance(next);
            }
        } finally {
            if (muxer != null) { try { if (started) muxer.stop(); } catch (Exception ignored) {} try { muxer.release(); } catch (Exception ignored) {} }
            try { ve.release(); } catch (Exception ignored) {}
            try { ae.release(); } catch (Exception ignored) {}
        }
    }

    private static void addTracks(MediaExtractor ex, MediaMuxer muxer, List<Track> tracks) {
        for (int i=0; i<ex.getTrackCount(); i++) {
            MediaFormat f = ex.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime == null || (!mime.startsWith("video/") && !mime.startsWith("audio/"))) continue;
            ex.selectTrack(i);
            Track t = new Track(); t.extractor=ex; t.sourceTrack=i; t.outputTrack=muxer.addTrack(f);
            tracks.add(t);
        }
    }
    private static void advance(Track t) {
        long time=t.extractor.getSampleTime();
        if(time<0){t.done=true; t.time=Long.MAX_VALUE;} else {t.done=false;t.time=time;}
    }

    public static Uri publish(Context context, File merged) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, "GameRecorder_" + System.currentTimeMillis() + ".mp4");
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Game Recorder");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Could not create output video");
            try (InputStream in = new FileInputStream(merged);
                 OutputStream out = context.getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new IllegalStateException("Could not write output video");
                byte[] b = new byte[64 * 1024]; int n;
                while ((n = in.read(b)) != -1) out.write(b, 0, n);
            } catch (Exception e) {
                context.getContentResolver().delete(uri, null, null);
                throw e;
            }
            ContentValues ready = new ContentValues(); ready.put(MediaStore.Video.Media.IS_PENDING, 0);
            context.getContentResolver().update(uri, ready, null, null);
            return uri;
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Game Recorder");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create Movies/Game Recorder");
        File dest = new File(dir, "GameRecorder_" + System.currentTimeMillis() + ".mp4");
        try (InputStream in = new FileInputStream(merged); OutputStream out = new FileOutputStream(dest)) {
            byte[] b = new byte[64 * 1024]; int n; while ((n=in.read(b))!=-1) out.write(b,0,n);
        }
        android.media.MediaScannerConnection.scanFile(context, new String[]{dest.getAbsolutePath()}, new String[]{"video/mp4"}, null);
        return Uri.fromFile(dest);
    }
}