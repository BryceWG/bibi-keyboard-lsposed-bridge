/*
 * Pure tap recognizer for toggle-style capture gestures.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

final class TapCaptureGesture {
    private final float moveSlopPx;
    private boolean pending;
    private float downX;
    private float downY;

    TapCaptureGesture(float moveSlopPx) {
        this.moveSlopPx = Math.max(0f, moveSlopPx);
    }

    void onDown(float x, float y) {
        pending = true;
        downX = x;
        downY = y;
    }

    void onMove(float x, float y, boolean inBounds) {
        if (!pending) return;
        float dx = x - downX;
        float dy = y - downY;
        if (!inBounds || dx * dx + dy * dy > moveSlopPx * moveSlopPx) cancel();
    }

    boolean onUp() {
        boolean tapped = pending;
        cancel();
        return tapped;
    }

    void cancel() {
        pending = false;
        downX = 0f;
        downY = 0f;
    }
}
