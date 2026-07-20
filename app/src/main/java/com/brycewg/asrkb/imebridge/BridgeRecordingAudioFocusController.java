/*
 * Short-lived exclusive audio focus used while a hooked IME records PCM.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.util.Log;

final class BridgeRecordingAudioFocusController {
    interface Handle {
    }

    interface Gateway {
        Handle requestFocus(AudioManager.OnAudioFocusChangeListener listener);
        void abandonFocus(Handle handle);
    }

    interface FocusLossListener {
        void onFocusLost(int change);
    }

    private static final String TAG = "BiBiImeBridgeFocus";

    private final Gateway gateway;
    private final FocusLossListener focusLossListener;
    private final Object lock = new Object();
    private long requestGeneration;
    private Handle activeHandle;

    BridgeRecordingAudioFocusController(Context context, FocusLossListener focusLossListener) {
        this(new AndroidGateway(context == null ? null : context.getApplicationContext()), focusLossListener);
    }

    BridgeRecordingAudioFocusController(Gateway gateway, FocusLossListener focusLossListener) {
        this.gateway = gateway;
        this.focusLossListener = focusLossListener;
    }

    boolean acquire() {
        release();
        final long generation;
        synchronized (lock) {
            requestGeneration += 1L;
            generation = requestGeneration;
        }
        Handle handle;
        try {
            handle = gateway.requestFocus(change -> onPlatformFocusChange(generation, change));
        } catch (Throwable t) {
            Log.w(TAG, "Failed to request recording audio focus", t);
            return false;
        }
        if (handle == null) return false;

        boolean retained;
        synchronized (lock) {
            retained = requestGeneration == generation && activeHandle == null;
            if (retained) activeHandle = handle;
        }
        if (!retained) abandonSafely(handle);
        return retained;
    }

    void release() {
        Handle handle;
        synchronized (lock) {
            requestGeneration += 1L;
            handle = activeHandle;
            activeHandle = null;
        }
        if (handle != null) abandonSafely(handle);
    }

    boolean isHeldForTest() {
        synchronized (lock) {
            return activeHandle != null;
        }
    }

    private void onPlatformFocusChange(long generation, int change) {
        if (!isFocusLoss(change)) return;
        Handle handle;
        synchronized (lock) {
            if (requestGeneration != generation || activeHandle == null) return;
            handle = activeHandle;
            activeHandle = null;
            requestGeneration += 1L;
        }
        abandonSafely(handle);
        try {
            if (focusLossListener != null) focusLossListener.onFocusLost(change);
        } catch (Throwable t) {
            Log.w(TAG, "Recording audio focus loss callback failed", t);
        }
    }

    private boolean isFocusLoss(int change) {
        return change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ||
            change == AudioManager.AUDIOFOCUS_LOSS;
    }

    private void abandonSafely(Handle handle) {
        try {
            gateway.abandonFocus(handle);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to abandon recording audio focus", t);
        }
    }

    private static final class AndroidGateway implements Gateway {
        private final AudioManager audioManager;

        AndroidGateway(Context context) {
            audioManager = context == null ? null : context.getSystemService(AudioManager.class);
        }

        @Override
        public Handle requestFocus(AudioManager.OnAudioFocusChangeListener listener) {
            if (audioManager == null) return null;
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
            AudioFocusRequest request = new AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(listener)
                .build();
            int result = audioManager.requestAudioFocus(request);
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG, "Recording audio focus was not granted: result=" + result);
                return null;
            }
            return new AndroidHandle(request);
        }

        @Override
        public void abandonFocus(Handle handle) {
            if (audioManager == null || !(handle instanceof AndroidHandle)) return;
            audioManager.abandonAudioFocusRequest(((AndroidHandle) handle).request);
        }
    }

    private static final class AndroidHandle implements Handle {
        final AudioFocusRequest request;

        AndroidHandle(AudioFocusRequest request) {
            this.request = request;
        }
    }
}
