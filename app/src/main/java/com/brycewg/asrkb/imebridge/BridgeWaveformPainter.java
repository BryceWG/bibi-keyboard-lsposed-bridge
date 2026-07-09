/*
 * Shared Canvas painter that mirrors the main keyboard WaveLineView look.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class BridgeWaveformPainter {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path path = new Path();
    private final RectF clipBounds = new RectF();

    private long recordingAnimStartMs;
    private float smoothedVolume;

    BridgeWaveformPainter() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void draw(Canvas canvas, int width, int height, WaveformState state, int color, float density, long timeMs) {
        if (canvas == null || state == null || !state.visible || width <= 0 || height <= 0) return;

        int save = canvas.save();
        clipBounds.set(0f, 0f, width, height);
        canvas.clipRect(clipBounds);

        float inset = BridgeWaveformMath.horizontalInsetPx(width, density);
        float drawWidth = Math.max(1f, width - inset * 2f);
        float left = inset;
        float centerY = height / 2f;

        if (!state.animated) {
            resetAnimationState();
            drawStaticPose(
                canvas,
                left,
                drawWidth,
                centerY,
                height,
                BridgeWaveformMath.IDLE_POSE_MILLIS,
                state.targetVolume,
                color,
                state.waveformAlpha,
                density
            );
            canvas.restoreToCount(save);
            return;
        }

        if (recordingAnimStartMs <= 0L) {
            recordingAnimStartMs = timeMs;
            smoothedVolume = state.targetVolume;
        }
        long millisPassed = timeMs - recordingAnimStartMs;
        smoothedVolume = BridgeWaveformMath.smoothVolume(smoothedVolume, state.targetVolume);
        float displayVolume = BridgeWaveformMath.displayVolume(smoothedVolume);

        drawLayers(
            canvas,
            left,
            drawWidth,
            centerY,
            height,
            millisPassed,
            displayVolume,
            color,
            state.waveformAlpha,
            density
        );

        canvas.restoreToCount(save);
    }

    void resetAnimationState() {
        recordingAnimStartMs = 0L;
        smoothedVolume = 0f;
    }

    private void drawStaticPose(
        Canvas canvas,
        float left,
        float drawWidth,
        float centerY,
        int height,
        long millisPassed,
        float volume,
        int color,
        int primaryAlpha,
        float density
    ) {
        drawLayers(
            canvas,
            left,
            drawWidth,
            centerY,
            height,
            millisPassed,
            volume,
            color,
            primaryAlpha,
            density
        );
    }

    private void drawLayers(
        Canvas canvas,
        float left,
        float drawWidth,
        float centerY,
        int height,
        long millisPassed,
        float volume,
        int color,
        int primaryAlpha,
        float density
    ) {
        float thickStroke = BridgeWaveformMath.thickStrokePx(density, height);
        float fineStroke = BridgeWaveformMath.fineStrokePx(thickStroke);
        for (int layer = 0; layer < BridgeWaveformMath.PATH_FUNCS.length; layer++) {
            drawWaveLayer(
                canvas,
                left,
                drawWidth,
                centerY,
                height,
                millisPassed,
                volume,
                layer,
                color,
                primaryAlpha,
                layer == 0 ? thickStroke : fineStroke,
                layer == 0
            );
        }
    }

    private void drawWaveLayer(
        Canvas canvas,
        float left,
        float drawWidth,
        float centerY,
        int height,
        long millisPassed,
        float volume,
        int layerIndex,
        int color,
        int primaryAlpha,
        float strokeWidth,
        boolean primaryLayer
    ) {
        path.rewind();
        path.moveTo(left, centerY);

        int pointCount = BridgeWaveformMath.SAMPLING_SIZE;
        for (int i = 0; i <= pointCount; i++) {
            float progress = i / (float) pointCount;
            float x = left + progress * drawWidth;
            float y = centerY + BridgeWaveformMath.layerYOffsetPx(
                0,
                volume,
                progress,
                millisPassed,
                layerIndex,
                height
            );
            path.lineTo(x, y);
        }

        int alpha = primaryLayer ? primaryAlpha : BridgeWaveformMath.secondaryAlpha(primaryAlpha);
        paint.setColor(applyAlpha(color, alpha));
        paint.setStrokeWidth(strokeWidth);
        canvas.drawPath(path, paint);
    }

    private static int applyAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
