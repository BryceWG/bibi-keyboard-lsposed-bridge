/*
 * Tiny bottom-edge long-press target with lightweight recording feedback.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

final class BottomCaptureStripView extends View {
    interface Listener {
        void onCaptureHoldStarted();
        void onCaptureHoldReleased();
        void onCaptureHoldCancelled();
    }

    static final int HEIGHT_DP = 32;
    /** Faster than the system long-press timeout so capture feels snappy. */
    static final long CAPTURE_LONG_PRESS_MS = 220L;
    // 独立 Xposed 模块无法依赖主 app UiColors；这是系统 accent/Monet 均不可用时的最后兜底。
    private static final int FALLBACK_WAVEFORM_COLOR = 0xFF4CAF50;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BridgeWaveformPainter waveformPainter = new BridgeWaveformPainter();
    private final LongPressCaptureGesture gesture;
    private final long longPressTimeoutMs;
    private final int waveformColor;
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            gesture.poll(SystemClock.uptimeMillis());
        }
    };

    private WaveformState waveformState = WaveformState.idle();
    private BridgeCaptureStatus captureStatus = BridgeCaptureStatus.ready("attached");
    private boolean showWaveformOnlyWhileRecording;

    BottomCaptureStripView(Context context, Listener listener) {
        super(context);
        float slop = ViewConfiguration.get(context).getScaledTouchSlop();
        longPressTimeoutMs = Math.min(ViewConfiguration.getLongPressTimeout(), CAPTURE_LONG_PRESS_MS);
        waveformColor = resolveWaveformColor(context);
        gesture = new LongPressCaptureGesture(
            longPressTimeoutMs,
            slop,
            new LongPressCaptureGesture.Listener() {
                @Override
                public void onLongPressStart() {
                    if (listener != null) listener.onCaptureHoldStarted();
                }

                @Override
                public void onLongPressRelease() {
                    if (listener != null) listener.onCaptureHoldReleased();
                }

                @Override
                public void onLongPressCancel() {
                    if (listener != null) listener.onCaptureHoldCancelled();
                }
            }
        );
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void updateStatus(BridgeCaptureStatus status) {
        captureStatus = status;
        applyWaveformState();
    }

    void setShowWaveformOnlyWhileRecording(boolean enabled) {
        showWaveformOnlyWhileRecording = enabled;
        applyWaveformState();
    }

    private void applyWaveformState() {
        WaveformState next = WaveformState.fromStatus(
            captureStatus,
            showWaveformOnlyWhileRecording
        );
        if (waveformState != null && waveformState.animated && !next.animated) {
            waveformPainter.resetAnimationState();
        }
        this.waveformState = next;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gesture.onDown(event.getX(), event.getY(), event.getEventTime());
                mainHandler.removeCallbacks(longPressRunnable);
                mainHandler.postDelayed(longPressRunnable, longPressTimeoutMs);
                return true;
            case MotionEvent.ACTION_MOVE:
                gesture.onMove(event.getX(), event.getY(), isWithinStrip(event.getX(), event.getY()));
                if (!gesture.isPending()) mainHandler.removeCallbacks(longPressRunnable);
                return true;
            case MotionEvent.ACTION_UP:
                mainHandler.removeCallbacks(longPressRunnable);
                gesture.onUp();
                return true;
            case MotionEvent.ACTION_CANCEL:
                mainHandler.removeCallbacks(longPressRunnable);
                gesture.onCancel();
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mainHandler.removeCallbacks(longPressRunnable);
        gesture.onCancel();
        waveformState = WaveformState.reset();
        waveformPainter.resetAnimationState();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (canvas == null || waveformState == null || !waveformState.visible) return;
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        long timeMs = SystemClock.uptimeMillis();
        waveformPainter.draw(
            canvas,
            width,
            height,
            waveformState,
            waveformColor,
            getResources().getDisplayMetrics().density,
            timeMs
        );
        if (waveformState.animated) postInvalidateOnAnimation();
    }

    static int computeStripWidthPx(int rootWidthPx, float density, int desiredWidthDp) {
        if (rootWidthPx <= 0 || density <= 0f) return 1;
        int desired = Math.round(BridgeVisualPrefs.clampWidthDp(desiredWidthDp) * density);
        int horizontalMargin = Math.round(16 * density);
        int available = Math.max(1, rootWidthPx - horizontalMargin * 2);
        return Math.min(available, desired);
    }

    static int resolveWaveformColor(Context context) {
        if (context == null) return FALLBACK_WAVEFORM_COLOR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return context.getColor(android.R.color.system_accent1_600);
            } catch (Throwable ignored) {
                // Fall through to themed accent/fallback for OEMs without the dynamic color resource.
            }
        }
        try {
            TypedValue value = new TypedValue();
            if (context.getTheme() != null &&
                context.getTheme().resolveAttribute(android.R.attr.colorAccent, value, true)) {
                if (value.resourceId != 0) return context.getColor(value.resourceId);
                if (value.data != 0) return value.data;
            }
        } catch (Throwable ignored) {
            // Keep rendering available inside third-party IME processes with unusual themes.
        }
        return FALLBACK_WAVEFORM_COLOR;
    }

    private boolean isWithinStrip(float x, float y) {
        return x >= 0f && y >= 0f && x <= getWidth() && y <= getHeight();
    }
}
