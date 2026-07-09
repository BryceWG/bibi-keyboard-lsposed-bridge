/*
 * Pure amplitude-to-visual state mapping for the bottom capture strip.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

final class WaveformState {
    static final int PCM_16_MAX = 32767;
    static final int RESPONSIVE_AMPLITUDE = 12000;
    static final float SILENCE_LEVEL = 0.22f;
    static final int SILENCE_TARGET_VOLUME = 52;
    static final int MIN_DISPLAY_VOLUME = 48;
    static final int IDLE_TARGET_VOLUME = 38;
    static final int IDLE_WAVEFORM_ALPHA = 150;
    static final int RECORDING_WAVEFORM_ALPHA = 218;

    final boolean visible;
    final boolean animated;
    final int targetVolume;
    final int waveformAlpha;

    private WaveformState(boolean visible, boolean animated, int targetVolume, int waveformAlpha) {
        this.visible = visible;
        this.animated = animated;
        this.targetVolume = clampVolume(targetVolume);
        this.waveformAlpha = clampAlpha(waveformAlpha);
    }

    static WaveformState idle() {
        return new WaveformState(true, false, IDLE_TARGET_VOLUME, IDLE_WAVEFORM_ALPHA);
    }

    static WaveformState fromStatus(BridgeCaptureStatus status) {
        if (status == null || status.state != BridgeCaptureStatus.State.RECORDING) {
            return idle();
        }
        return new WaveformState(
            true,
            true,
            BridgeWaveformMath.peakToTargetVolume(status.amplitude),
            RECORDING_WAVEFORM_ALPHA
        );
    }

    static WaveformState reset() {
        return idle();
    }

    private static int clampVolume(int value) {
        if (value < 0) return 0;
        if (value > 100) return 100;
        return value;
    }

    private static int clampAlpha(int value) {
        if (value < 0) return 0;
        if (value > 255) return 255;
        return value;
    }
}
