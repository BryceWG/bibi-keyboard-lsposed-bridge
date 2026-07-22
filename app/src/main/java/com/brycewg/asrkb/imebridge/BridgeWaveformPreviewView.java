/*
 * Native preview view for bridge capture strip waveform settings.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;

final class BridgeWaveformPreviewView extends View {
    private final BridgeWaveformPainter waveformPainter = new BridgeWaveformPainter();
    private final int waveformColor;
    private BridgeVisualPrefs.VisualConfig visualConfig = BridgeVisualPrefs.defaults();
    private boolean recording;

    BridgeWaveformPreviewView(Context context, boolean recording) {
        super(context);
        this.recording = recording;
        this.waveformColor = BottomCaptureStripView.resolveWaveformColor(context);
        setBackgroundColor(Color.TRANSPARENT);
        setMinimumHeight(dp(56));
        setPadding(0, dp(4), 0, dp(4));
    }

    void setVisualConfig(BridgeVisualPrefs.VisualConfig visualConfig) {
        if (visualConfig == null) return;
        this.visualConfig = visualConfig;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int stripHeight = Math.max(1, Math.round(visualConfig.heightDp * getResources().getDisplayMetrics().density));
        int desiredHeight = stripHeight + dp(28);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int availableWidth = Math.max(1, getWidth() - dp(32));
        int stripWidth = Math.min(
            availableWidth,
            Math.max(1, Math.round(visualConfig.widthDp * getResources().getDisplayMetrics().density))
        );
        int stripHeight = Math.max(1, Math.round(visualConfig.heightDp * getResources().getDisplayMetrics().density));
        int save = canvas.save();
        canvas.translate((getWidth() - stripWidth) / 2f, (getHeight() - stripHeight) / 2f);
        long timeMs = SystemClock.uptimeMillis();
        WaveformState state = recording
            ? WaveformState.fromStatus(BridgeCaptureStatus.recording(previewAmplitude(timeMs)))
            : WaveformState.fromStatus(
                BridgeCaptureStatus.ready("preview"),
                visualConfig.showWaveformOnlyWhileRecording
            );
        waveformPainter.draw(
            canvas,
            stripWidth,
            stripHeight,
            state,
            waveformColor,
            getResources().getDisplayMetrics().density,
            timeMs
        );
        canvas.restoreToCount(save);
        if (recording) postInvalidateOnAnimation();
    }

    private int previewAmplitude(long timeMs) {
        float wave = (float) ((Math.sin(timeMs / 420.0) + 1.0) * 0.5);
        return Math.round(3500 + wave * 11000);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
