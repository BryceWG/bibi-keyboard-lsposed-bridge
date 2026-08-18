/*
 * Tiny bottom-edge recording target with lightweight feedback.
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
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewConfiguration;

final class BottomCaptureStripView extends View {
    interface Listener {
        void onCaptureHoldStarted();
        void onCaptureHoldReleased();
        void onCaptureHoldCancelled();
    }

    static final int HEIGHT_DP = 32;
    static final int TAP_ACTION_NONE = 0;
    static final int TAP_ACTION_START = 1;
    static final int TAP_ACTION_FINISH = 2;
    /** Faster than the system long-press timeout so capture feels snappy. */
    static final long CAPTURE_LONG_PRESS_MS = 220L;
    // 独立 Xposed 模块无法依赖主 app UiColors；这是系统 accent/Monet 均不可用时的最后兜底。
    private static final int FALLBACK_WAVEFORM_COLOR = 0xFF4CAF50;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BridgeWaveformPainter waveformPainter = new BridgeWaveformPainter();
    private final LongPressCaptureGesture gesture;
    private final TapCaptureGesture tapGesture;
    private final Listener listener;
    private long triggerDelayMs;
    private final int waveformColor;
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (tapToToggleRecording) {
                if (pendingTapAction != TAP_ACTION_NONE && tapGesture.isPending()) {
                    dispatchTapAction(pendingTapAction);
                    delayedTapTriggered = true;
                }
                return;
            }
            gesture.poll(SystemClock.uptimeMillis());
        }
    };

    private WaveformState waveformState = WaveformState.idle();
    private BridgeCaptureStatus captureStatus = BridgeCaptureStatus.ready("attached");
    private boolean showWaveformOnlyWhileRecording;
    private boolean tapToToggleRecording;
    private int pendingTapAction = TAP_ACTION_NONE;
    private boolean delayedTapTriggered;
    private long downTimeMs;
    private float downX;
    private float downY;

    BottomCaptureStripView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        float slop = ViewConfiguration.get(context).getScaledTouchSlop();
        triggerDelayMs = BridgeVisualPrefs.DEFAULT_TRIGGER_DELAY_MS;
        waveformColor = resolveWaveformColor(context);
        gesture = new LongPressCaptureGesture(
            triggerDelayMs,
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
        tapGesture = new TapCaptureGesture(slop);
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

    void setTapToToggleRecording(boolean enabled) {
        if (tapToToggleRecording == enabled) return;
        mainHandler.removeCallbacks(longPressRunnable);
        gesture.onCancel();
        tapGesture.cancel();
        pendingTapAction = TAP_ACTION_NONE;
        delayedTapTriggered = false;
        tapToToggleRecording = enabled;
    }

    void setTriggerDelayMs(long delayMs) {
        triggerDelayMs = BridgeVisualPrefs.clampTriggerDelayMs((int) delayMs);
        gesture.setThresholdMs(triggerDelayMs);
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
                if (tapToToggleRecording) {
                    tapGesture.onDown(event.getX(), event.getY());
                    pendingTapAction = tapAction(captureStatus);
                    delayedTapTriggered = false;
                    downTimeMs = event.getEventTime();
                    downX = event.getX();
                    downY = event.getY();
                    mainHandler.removeCallbacks(longPressRunnable);
                    if (pendingTapAction == TAP_ACTION_FINISH) {
                        dispatchTapAction(pendingTapAction);
                        delayedTapTriggered = true;
                    } else if (pendingTapAction == TAP_ACTION_START && triggerDelayMs == 0L) {
                        dispatchTapAction(pendingTapAction);
                        delayedTapTriggered = true;
                    } else {
                        mainHandler.postDelayed(longPressRunnable, triggerDelayMs);
                    }
                    return true;
                }
                gesture.onDown(event.getX(), event.getY(), event.getEventTime());
                downTimeMs = event.getEventTime();
                downX = event.getX();
                downY = event.getY();
                mainHandler.removeCallbacks(longPressRunnable);
                if (triggerDelayMs == 0L) {
                    gesture.poll(event.getEventTime());
                } else {
                    mainHandler.postDelayed(longPressRunnable, triggerDelayMs);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (tapToToggleRecording) {
                    tapGesture.onMove(
                        event.getX(),
                        event.getY(),
                        isWithinStrip(event.getX(), event.getY())
                    );
                    if (!tapGesture.isPending()) mainHandler.removeCallbacks(longPressRunnable);
                    return true;
                }
                gesture.onMove(event.getX(), event.getY(), isWithinStrip(event.getX(), event.getY()));
                if (!gesture.isPending()) mainHandler.removeCallbacks(longPressRunnable);
                return true;
            case MotionEvent.ACTION_UP:
                if (tapToToggleRecording) {
                    mainHandler.removeCallbacks(longPressRunnable);
                    if (!delayedTapTriggered &&
                        event.getEventTime() - downTimeMs >= triggerDelayMs &&
                        tapGesture.isPending() && pendingTapAction != TAP_ACTION_NONE) {
                        dispatchTapAction(pendingTapAction);
                        delayedTapTriggered = true;
                    }
                    boolean validTap = tapGesture.onUp();
                    boolean shortTap = !delayedTapTriggered && validTap;
                    if (shortTap) replayShortTap(event);
                    pendingTapAction = TAP_ACTION_NONE;
                    delayedTapTriggered = false;
                    downTimeMs = 0L;
                    return true;
                }
                mainHandler.removeCallbacks(longPressRunnable);
                if (gesture.isPending() && !gesture.isTriggered()) {
                    gesture.poll(event.getEventTime());
                }
                boolean shortHold = gesture.isPending() && !gesture.isTriggered();
                gesture.onUp();
                if (shortHold) replayShortTap(event);
                downTimeMs = 0L;
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (tapToToggleRecording) {
                    mainHandler.removeCallbacks(longPressRunnable);
                    tapGesture.cancel();
                    pendingTapAction = TAP_ACTION_NONE;
                    delayedTapTriggered = false;
                    downTimeMs = 0L;
                    return true;
                }
                mainHandler.removeCallbacks(longPressRunnable);
                gesture.onCancel();
                downTimeMs = 0L;
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mainHandler.removeCallbacks(longPressRunnable);
        gesture.onCancel();
        tapGesture.cancel();
        pendingTapAction = TAP_ACTION_NONE;
        delayedTapTriggered = false;
        downTimeMs = 0L;
        downX = 0f;
        downY = 0f;
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

    private void replayShortTap(MotionEvent upEvent) {
        ViewParent parent = getParent();
        if (!(parent instanceof ViewGroup) || upEvent == null) return;
        ViewGroup root = (ViewGroup) parent;
        int[] stripLocation = new int[2];
        int[] rootLocation = new int[2];
        getLocationOnScreen(stripLocation);
        root.getLocationOnScreen(rootLocation);
        float x = stripLocation[0] + downX - rootLocation[0];
        float y = stripLocation[1] + downY - rootLocation[1];
        MotionEvent down = MotionEvent.obtain(
            upEvent.getDownTime(),
            upEvent.getEventTime(),
            MotionEvent.ACTION_DOWN,
            x,
            y,
            upEvent.getMetaState()
        );
        MotionEvent up = MotionEvent.obtain(
            upEvent.getDownTime(),
            upEvent.getEventTime(),
            MotionEvent.ACTION_UP,
            x,
            y,
            upEvent.getMetaState()
        );
        int oldVisibility = getVisibility();
        try {
            setVisibility(INVISIBLE);
            root.dispatchTouchEvent(down);
            root.dispatchTouchEvent(up);
        } finally {
            down.recycle();
            up.recycle();
            setVisibility(oldVisibility);
        }
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

    static int tapAction(BridgeCaptureStatus status) {
        if (status == null) return TAP_ACTION_NONE;
        if (status.state == BridgeCaptureStatus.State.READY ||
            status.state == BridgeCaptureStatus.State.FAILED) {
            return TAP_ACTION_START;
        }
        if (status.state == BridgeCaptureStatus.State.STARTING ||
            status.state == BridgeCaptureStatus.State.RECORDING) {
            return TAP_ACTION_FINISH;
        }
        return TAP_ACTION_NONE;
    }

    private void dispatchTapAction(int action) {
        if (action == TAP_ACTION_START && listener != null) {
            listener.onCaptureHoldStarted();
        } else if (action == TAP_ACTION_FINISH && listener != null) {
            listener.onCaptureHoldReleased();
        }
    }

    private boolean isWithinStrip(float x, float y) {
        return x >= 0f && y >= 0f && x <= getWidth() && y <= getHeight();
    }
}
