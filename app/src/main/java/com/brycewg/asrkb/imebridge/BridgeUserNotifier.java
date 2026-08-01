/*
 * User-facing warnings emitted from the hooked IME process.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class BridgeUserNotifier {
    private static final String TAG = "BiBiImeBridge";
    private static final String MODULE_PACKAGE = "com.brycewg.asrkb.imebridge";
    private static final long TOAST_COOLDOWN_MS = 30_000L;
    private static final ConcurrentHashMap<Integer, AtomicLong> LAST_TOAST_ELAPSED_MS =
        new ConcurrentHashMap<>();

    private BridgeUserNotifier() {
    }

    static void show(Context context, int messageRes) {
        if (context == null) return;
        CharSequence message;
        try {
            Context moduleContext = context.createPackageContext(MODULE_PACKAGE, 0);
            message = moduleContext.getText(messageRes);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to resolve module toast message", t);
            return;
        }
        if (!tryAcquireToastSlot(messageRes, SystemClock.elapsedRealtime())) return;
        Context appContext = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(appContext == null ? context : appContext, message, Toast.LENGTH_LONG).show()
        );
    }

    static void showCaptureFailure(Context context, BridgeCaptureStatus status) {
        if (status == null || status.state != BridgeCaptureStatus.State.FAILED) return;
        int messageRes = captureFailureMessageResFor(status.failureCode);
        if (messageRes != 0) show(context, messageRes);
    }

    static int captureFailureMessageResFor(int failureCode) {
        if (failureCode == BridgeCaptureStatus.FAILURE_REASON_NO_INPUT_CONNECTION) {
            return R.string.bridge_toast_no_input_connection;
        }
        if (failureCode == BridgeCaptureStatus.FAILURE_REASON_SENSITIVE_FIELD) {
            return R.string.bridge_toast_sensitive_field;
        }
        if (failureCode == BridgeCaptureStatus.FAILURE_REASON_AUDIO_RECORD) {
            return R.string.bridge_toast_microphone_unavailable;
        }
        if (failureCode == BridgeCaptureStatus.FAILURE_REASON_RECORDING) {
            return R.string.bridge_toast_recording_failed;
        }
        if (failureCode == BridgeCaptureStatus.FAILURE_REASON_FINISH) {
            return R.string.bridge_toast_finish_failed;
        }
        return 0;
    }

    static boolean shouldShow(long lastToastElapsedMs, long nowElapsedMs) {
        return nowElapsedMs - lastToastElapsedMs >= TOAST_COOLDOWN_MS;
    }

    static boolean tryAcquireToastSlot(int messageRes, long nowElapsedMs) {
        AtomicLong lastToastElapsedMs = LAST_TOAST_ELAPSED_MS.computeIfAbsent(
            messageRes,
            ignored -> new AtomicLong(-TOAST_COOLDOWN_MS)
        );
        while (true) {
            long lastShown = lastToastElapsedMs.get();
            if (!shouldShow(lastShown, nowElapsedMs)) return false;
            if (lastToastElapsedMs.compareAndSet(lastShown, nowElapsedMs)) return true;
        }
    }
}
