/*
 * WaveLineView-compatible waveform sampling for the IME bridge visual feedback.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

final class BridgeWaveformMath {
    static final int SAMPLING_SIZE = 64;
    static final float MOVE_SPEED = 250f;
    /** Fixed phase for the idle pose; 0.5 centers a peak for WaveLineView's sin envelope. */
    static final long IDLE_POSE_MILLIS = Math.round(0.5f * MOVE_SPEED);
    static final int SENSIBILITY = 10;
    static final float PER_VOLUME = SENSIBILITY * 0.35f;
    static final float[] PATH_FUNCS = {0.6f, 0.35f, 0.1f, -0.1f};
    private static final int DEFAULT_SENSITIVITY = 5;
    private static final float DEFAULT_GAIN = computeGain(DEFAULT_SENSITIVITY);

    private BridgeWaveformMath() {
    }

    static double calcValue(float mapX, float offset) {
        float normalizedOffset = offset % 2f;
        double sinFunc = Math.sin(Math.PI * mapX - normalizedOffset * Math.PI);
        double recessionFunc = 4d / (4d + Math.pow(mapX, 4d));
        return sinFunc * recessionFunc;
    }

    static float progressToMapX(float progress) {
        float x = clamp01(progress);
        return x * 4f - 2f;
    }

    static float waveAmplitudePx(int viewHeight) {
        if (viewHeight <= 0) return 0f;
        return viewHeight / 3f;
    }

    static float maxAmplitudePx(int viewHeight, float density) {
        return waveAmplitudePx(viewHeight);
    }

    static float layerYOffsetPx(
        int targetVolume,
        float smoothedVolume,
        float progress,
        long millisPassed,
        int layerIndex,
        int viewHeight
    ) {
        if (layerIndex < 0 || layerIndex >= PATH_FUNCS.length) return 0f;
        float amplitude = waveAmplitudePx(viewHeight);
        float offset = millisPassed / MOVE_SPEED;
        float mapX = progressToMapX(progress);
        double value = calcValue(mapX, offset);
        float volume = Math.max(0f, smoothedVolume);
        float realY = (float) (amplitude * value * PATH_FUNCS[layerIndex] * volume * 0.01f);
        return realY;
    }

    static float yOffsetPx(
        WaveformState state,
        float progress,
        long millisPassed,
        int layerIndex,
        int viewHeight,
        float density
    ) {
        if (state == null || !state.visible) return 0f;
        long phaseMs = state.animated ? millisPassed : IDLE_POSE_MILLIS;
        float volume = state.animated
            ? displayVolume(state.targetVolume)
            : state.targetVolume;
        return layerYOffsetPx(
            state.targetVolume,
            volume,
            progress,
            phaseMs,
            layerIndex,
            viewHeight
        );
    }

    static int peakToTargetVolume(int peak) {
        if (peak <= 0) {
            return WaveformState.SILENCE_TARGET_VOLUME;
        }
        float normalized = Math.min(1f, peak / (float) WaveformState.RESPONSIVE_AMPLITUDE);
        float boosted = Math.min(1f, normalized * DEFAULT_GAIN);
        int volume = Math.round(boosted * 100f);
        return Math.max(WaveformState.SILENCE_TARGET_VOLUME, volume);
    }

    static float horizontalInsetPx(float width, float density) {
        return Math.max(1.5f * density, width * 0.02f);
    }

    static float displayVolume(float smoothedVolume) {
        return Math.max(smoothedVolume, WaveformState.MIN_DISPLAY_VOLUME);
    }

    static float smoothVolume(float current, int targetVolume) {
        int target = Math.max(0, Math.min(100, targetVolume));
        if (current < target - PER_VOLUME) {
            return current + PER_VOLUME;
        }
        if (current > target + PER_VOLUME) {
            if (current < PER_VOLUME * 2f) {
                return PER_VOLUME * 2f;
            }
            return current - PER_VOLUME;
        }
        return target;
    }

    static float thickStrokePx(float density, int height) {
        float preferred = 2.8f * density;
        if (height > 0) preferred = Math.min(preferred, height * 0.14f);
        return Math.max(2.2f * density, preferred);
    }

    static float idleStrokePx(float density) {
        return Math.max(2.4f * density, 2f);
    }

    static float fineStrokePx(float thickStrokePx) {
        return Math.max(1f, thickStrokePx * (2f / 6f));
    }

    static int secondaryAlpha(int primaryAlpha) {
        return Math.round(primaryAlpha * (100f / 255f));
    }

    static float computeGain(int sensitivity) {
        int normalized = Math.max(1, Math.min(10, sensitivity));
        return (float) (0.25 * Math.pow(48.0, (normalized - 1) / 9.0));
    }

    private static float clamp01(float value) {
        if (value < 0f) return 0f;
        if (value > 1f) return 1f;
        return value;
    }
}
