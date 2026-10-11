package com.tbt2.gamerecorder;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Records internal playback audio, optionally mixed with the microphone, to a temporary AAC MP4.
 * Internal playback capture is supported on Android 10+ and remains subject to each game's capture policy.
 */
public final class AdvancedAudioRecorder {
    private static final String TAG = "GameRecorderAudio";
    private static final int RATE = 48000;
    private static final int CHANNELS = 2;
    private static final int CHANNEL_MASK = AudioFormat.CHANNEL_IN_STEREO;
    private static final int SAMPLES_PER_FRAME = 960;
    private final MediaProjection projection;
    private final boolean includeMic;
    private final File output;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private AudioRecord playback;
    private AudioRecord microphone;
    private MediaCodec encoder;
    private MediaMuxer muxer;
    private Thread worker;
    private int track = -1;
    private boolean muxerStarted;
    private long framesSubmitted;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    public AdvancedAudioRecorder(MediaProjection projection, boolean includeMic, File output) {
        this.projection = projection;
        this.includeMic = includeMic;
        this.output = output;
    }

    public void start() throws Exception {
        if (Build.VERSION.SDK_INT < 29) throw new IllegalStateException("Internal audio requires Android 10 or newer");
        int min = AudioRecord.getMinBufferSize(RATE, CHANNEL_MASK, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) throw new IllegalStateException("Device does not support stereo PCM capture");
        int bufferBytes = Math.max(min, SAMPLES_PER_FRAME * CHANNELS * 2 * 4);

        AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build();
        playback = new AudioRecord.Builder()
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE).setChannelMask(CHANNEL_MASK).build())
                .setAudioPlaybackCaptureConfig(config).setBufferSizeInBytes(bufferBytes).build();
        if (playback.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Could not initialize internal audio capture");

        if (includeMic) {
            microphone = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, CHANNEL_MASK,
                    AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
            if (microphone.getState() != AudioRecord.STATE_INITIALIZED) {
                microphone.release(); microphone = null;
                microphone = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
                if (microphone.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Could not initialize microphone");
            }
        }
        MediaFormat fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, CHANNELS);
        fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, 192000);
        fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        encoder.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
        muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        playback.startRecording();
        if (microphone != null) microphone.startRecording();
        running.set(true);
        worker = new Thread(this::recordLoop, "GameRecorder-Audio");
        worker.start();
    }

    private void recordLoop() {
        short[] game = new short[SAMPLES_PER_FRAME * CHANNELS];
        short[] mic = new short[SAMPLES_PER_FRAME * CHANNELS];
        short[] mixed = new short[SAMPLES_PER_FRAME * CHANNELS];
        byte[] pcm = new byte[mixed.length * 2];
        try {
            while (running.get()) {
                int got = playback.read(game, 0, game.length, AudioRecord.READ_BLOCKING);
                if (got <= 0) continue;
                int samples = Math.min(got, game.length);
                if (microphone != null) {
                    int micGot;
                    if (microphone.getChannelCount() == 1) {
                        short[] mono = new short[SAMPLES_PER_FRAME];
                        micGot = microphone.read(mono, 0, Math.min(SAMPLES_PER_FRAME, samples / 2), AudioRecord.READ_NON_BLOCKING);
                        int frames = Math.max(0, micGot);
                        for (int i = 0; i < samples / 2; i++) {
                            if (i < frames) {
                                int m = mono[i];
                                mixed[i * 2] = clamp(game[i * 2] + m);
                                mixed[i * 2 + 1] = clamp(game[i * 2 + 1] + m);
                            } else {
                                mixed[i * 2] = game[i * 2];
                                mixed[i * 2 + 1] = game[i * 2 + 1];
                            }
                        }
                    } else {
                        micGot = microphone.read(mic, 0, samples, AudioRecord.READ_NON_BLOCKING);
                        int count = Math.min(samples, Math.max(0, micGot));
                        for (int i = 0; i < samples; i++) mixed[i] = i < count ? clamp(game[i] + mic[i]) : game[i];
                    }
                    if (samples <= 0) continue;
                } else {
                    System.arraycopy(game, 0, mixed, 0, samples);
                }
                int bytes = samples * 2;
                for (int i = 0; i < samples; i++) {
                    pcm[i * 2] = (byte)(mixed[i] & 0xff);
                    pcm[i * 2 + 1] = (byte)((mixed[i] >> 8) & 0xff);
                }
                queuePcm(pcm, bytes, framesSubmitted * 1000000L / RATE);
                framesSubmitted += samples / CHANNELS;
            }
        } catch (Exception e) {
            Log.e(TAG, "Audio capture loop failed", e);
        } finally {
            drainEncoder(true);
        }
    }

    private short clamp(int value) { return (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value)); }

    private void queuePcm(byte[] data, int size, long ptsUs) {
        try {
            int index = encoder.dequeueInputBuffer(10000);
            if (index >= 0) {
                ByteBuffer input = encoder.getInputBuffer(index);
                if (input != null) {
                    input.clear();
                    int count = Math.min(size, input.remaining());
                    input.put(data, 0, count);
                    encoder.queueInputBuffer(index, 0, count, ptsUs, 0);
                }
            }
            drainEncoder(false);
        } catch (Exception e) { Log.e(TAG, "AAC input failed", e); }
    }

    private void drainEncoder(boolean eos) {
        if (encoder == null) return;
        try {
            if (eos) {
                int in = encoder.dequeueInputBuffer(10000);
                if (in >= 0) encoder.queueInputBuffer(in, 0, 0, framesSubmitted * 1000000L / RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
            long deadline = System.currentTimeMillis() + (eos ? 1500 : 20);
            while (System.currentTimeMillis() < deadline) {
                int out = encoder.dequeueOutputBuffer(info, eos ? 10000 : 0);
                if (out == MediaCodec.INFO_TRY_AGAIN_LATER) { if (!eos) break; continue; }
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(encoder.getOutputFormat());
                    muxer.start(); muxerStarted = true;
                } else if (out >= 0) {
                    ByteBuffer data = encoder.getOutputBuffer(out);
                    if (data != null && info.size > 0 && muxerStarted && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        data.position(info.offset); data.limit(info.offset + info.size);
                        muxer.writeSampleData(track, data, info);
                    }
                    boolean end = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    encoder.releaseOutputBuffer(out, false);
                    if (end) break;
                }
            }
        } catch (Exception e) { Log.e(TAG, "AAC drain failed", e); }
    }

    public void stop() {
        running.set(false);
        // Stop AudioRecords first so blocking reads wake before the worker is joined.
        releaseRecord(playback); playback = null;
        releaseRecord(microphone); microphone = null;
        if (worker != null) {
            try { worker.join(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            worker = null;
        }
        if (encoder != null) { try { encoder.stop(); } catch (Exception ignored) {} try { encoder.release(); } catch (Exception ignored) {} encoder = null; }
        if (muxer != null) { try { if (muxerStarted) muxer.stop(); } catch (Exception ignored) {} try { muxer.release(); } catch (Exception ignored) {} muxer = null; }
    }
    private void releaseRecord(AudioRecord r) { if (r != null) { try { r.stop(); } catch (Exception ignored) {} try { r.release(); } catch (Exception ignored) {} } }
}
