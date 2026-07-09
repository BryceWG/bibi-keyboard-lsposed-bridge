/*
 * Pure long-press recognizer for the bottom capture strip.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

final class LongPressCaptureGesture {
    interface Listener {
        void onLongPressStart();
        void onLongPressRelease();
        void onLongPressCancel();
    }

    private final long thresholdMs;
    private final float moveSlopPx;
    private final Listener listener;

    private boolean pending;
    private boolean triggered;
    private float downX;
    private float downY;
    private long downTimeMs;

    LongPressCaptureGesture(long thresholdMs, float moveSlopPx, Listener listener) {
        this.thresholdMs = Math.max(1L, thresholdMs);
        this.moveSlopPx = Math.max(0f, moveSlopPx);
        this.listener = listener;
    }

    void onDown(float x, float y, long eventTimeMs) {
        pending = true;
        triggered = false;
        downX = x;
        downY = y;
        downTimeMs = eventTimeMs;
    }

    boolean poll(long nowMs) {
        if (!pending || triggered) return false;
        if (nowMs - downTimeMs < thresholdMs) return false;
        triggered = true;
        if (listener != null) listener.onLongPressStart();
        return true;
    }

    boolean onMove(float x, float y) {
        return onMove(x, y, true);
    }

    boolean onMove(float x, float y, boolean inBounds) {
        if (!pending) return false;
        if (triggered) {
            if (!inBounds) {
                if (listener != null) listener.onLongPressCancel();
                reset();
            }
            return true;
        }
        float dx = x - downX;
        float dy = y - downY;
        if (dx * dx + dy * dy <= moveSlopPx * moveSlopPx) return false;
        reset();
        return false;
    }

    boolean onUp() {
        boolean wasTriggered = triggered;
        if (wasTriggered && listener != null) listener.onLongPressRelease();
        reset();
        return wasTriggered;
    }

    boolean onCancel() {
        boolean wasTriggered = triggered;
        if (wasTriggered && listener != null) listener.onLongPressCancel();
        reset();
        return wasTriggered;
    }

    boolean isPending() {
        return pending;
    }

    boolean isTriggered() {
        return triggered;
    }

    private void reset() {
        pending = false;
        triggered = false;
        downX = 0f;
        downY = 0f;
        downTimeMs = 0L;
    }
}
