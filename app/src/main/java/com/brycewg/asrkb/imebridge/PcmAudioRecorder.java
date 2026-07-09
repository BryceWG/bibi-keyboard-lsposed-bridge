/*
 * AudioRecord-backed PCM16 mono recorder used inside the hooked IME process.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;

final class PcmAudioRecorder implements BridgeCaptureCoordinator.AudioRecorder {
    static final int SAMPLE_RATE = 16000;
    static final int CHANNELS = 1;

    private final Context context;
    private final Object lock = new Object();
    private AudioRecord audioRecord;
    private Thread readThread;
    private volatile boolean running;
    private String activeSessionId;
    private BridgeCaptureCoordinator.AudioRecorderCallback callback;

    PcmAudioRecorder(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    @Override
    public boolean start(String sessionId, BridgeCaptureCoordinator.AudioRecorderCallback callback) {
        synchronized (lock) {
            if (running) return false;
            if (context == null || !hasRecordAudioPermission()) return false;
            int minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            );
            if (minBuffer <= 0) return false;
            int bufferSize = Math.max(minBuffer * 2, SAMPLE_RATE / 2);
            AudioRecord record;
            try {
                record = createAudioRecord(bufferSize);
                if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                    releaseQuietly(record);
                    return false;
                }
                record.startRecording();
                if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    releaseQuietly(record);
                    return false;
                }
            } catch (Throwable t) {
                return false;
            }
            this.audioRecord = record;
            this.callback = callback;
            this.activeSessionId = sessionId;
            this.running = true;
            this.readThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    readLoop(bufferSize);
                }
            }, "BiBiBridgePcmRecorder");
            this.readThread.start();
            return true;
        }
    }

    @Override
    public void stop() {
        stopInternal();
    }

    @Override
    public void cancel() {
        stopInternal();
    }

    @Override
    public boolean isRecording() {
        return running;
    }

    private AudioRecord createAudioRecord(int bufferSize) {
        if (Build.VERSION.SDK_INT >= 23) {
            return new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .build();
        }
        return new AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        );
    }

    private void readLoop(int bufferSize) {
        byte[] buffer = new byte[Math.max(1024, bufferSize / 2)];
        while (running) {
            AudioRecord record = audioRecord;
            if (record == null) break;
            int read;
            try {
                read = record.read(buffer, 0, buffer.length);
            } catch (Throwable t) {
                notifyError("audio read failed");
                break;
            }
            if (read > 0) {
                BridgeCaptureCoordinator.AudioRecorderCallback cb = callback;
                String sessionId = activeSessionId;
                if (cb != null && sessionId != null) {
                    byte[] frame = new byte[read];
                    System.arraycopy(buffer, 0, frame, 0, read);
                    cb.onPcmFrame(sessionId, frame, SAMPLE_RATE, CHANNELS, calculatePeak(frame, read));
                }
            } else if (read < 0) {
                notifyError("audio read returned " + read);
                break;
            }
        }
        stopInternal();
    }

    private int calculatePeak(byte[] frame, int length) {
        int peak = 0;
        int safeLength = length - (length % 2);
        for (int i = 0; i < safeLength; i += 2) {
            int sample = (frame[i] & 0xFF) | (frame[i + 1] << 8);
            if (sample == -32768) sample = 32767;
            int value = Math.abs(sample);
            if (value > peak) peak = value;
        }
        return peak;
    }

    private void notifyError(String message) {
        BridgeCaptureCoordinator.AudioRecorderCallback cb = callback;
        String sessionId = activeSessionId;
        if (cb != null && sessionId != null) cb.onRecorderError(sessionId, message);
    }

    private boolean hasRecordAudioPermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    private void stopInternal() {
        AudioRecord record;
        Thread thread;
        synchronized (lock) {
            if (!running && audioRecord == null) return;
            running = false;
            record = audioRecord;
            thread = readThread;
            audioRecord = null;
            readThread = null;
            activeSessionId = null;
            callback = null;
        }
        if (record != null) {
            try {
                record.stop();
            } catch (Throwable ignored) {
            }
            releaseQuietly(record);
        }
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(250L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void releaseQuietly(AudioRecord record) {
        try {
            record.release();
        } catch (Throwable ignored) {
        }
    }
}
