package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WaveformStateTest {
    @Test
    public void idleIsVisibleStaticAndLowPresence() {
        WaveformState state = WaveformState.fromStatus(BridgeCaptureStatus.ready("ready"));

        assertStaticIdle(state);
        assertEquals(WaveformState.IDLE_TARGET_VOLUME, state.targetVolume);
        assertTrue(state.waveformAlpha > 0);
        assertTrue(state.waveformAlpha <= 160);
        float idleOffset = BridgeWaveformMath.yOffsetPx(state, 0.5f, 0L, 0, 48, 1f);
        assertTrue(Math.abs(idleOffset) > 1f);
        assertEquals(
            idleOffset,
            BridgeWaveformMath.yOffsetPx(state, 0.5f, 900L, 0, 48, 1f),
            0.0001f
        );
        assertTrue(Math.abs(BridgeWaveformMath.calcValue(0f, 0.5f)) > 0.9d);
    }

    @Test
    public void nonRecordingStatesReturnToStaticIdle() {
        assertStaticIdle(WaveformState.fromStatus(null));
        assertStaticIdle(WaveformState.fromStatus(BridgeCaptureStatus.unsupported("not attached")));
        assertStaticIdle(WaveformState.fromStatus(BridgeCaptureStatus.ready("attached")));
        assertStaticIdle(WaveformState.fromStatus(BridgeCaptureStatus.starting("starting")));
        assertStaticIdle(WaveformState.fromStatus(BridgeCaptureStatus.finishing()));
        assertStaticIdle(WaveformState.fromStatus(BridgeCaptureStatus.cancelling("window hidden")));
        assertStaticIdle(WaveformState.fromStatus(BridgeCaptureStatus.failed("session failed")));
        assertStaticIdle(WaveformState.reset());
    }

    @Test
    public void silenceStartsAnimatedRecordingFeedback() {
        WaveformState state = WaveformState.fromStatus(BridgeCaptureStatus.recording(0));

        assertTrue(state.visible);
        assertTrue(state.animated);
        assertTrue(state.targetVolume >= WaveformState.SILENCE_TARGET_VOLUME);
        assertTrue(state.targetVolume < 60);
        assertTrue(state.waveformAlpha > WaveformState.IDLE_WAVEFORM_ALPHA);
        assertTrue(
            Math.abs(BridgeWaveformMath.layerYOffsetPx(state.targetVolume, state.targetVolume, 0.35f, 0L, 0, 48)) > 0f
        );
        assertTrue(
            BridgeWaveformMath.layerYOffsetPx(state.targetVolume, state.targetVolume, 0.35f, 0L, 0, 48) !=
                BridgeWaveformMath.layerYOffsetPx(state.targetVolume, state.targetVolume, 0.35f, 900L, 0, 48)
        );
    }

    @Test
    public void normalAmplitudeMapsProportionally() {
        WaveformState state = WaveformState.fromStatus(BridgeCaptureStatus.recording(6000));
        WaveformState quieter = WaveformState.fromStatus(BridgeCaptureStatus.recording(3000));

        assertTrue(state.visible);
        assertTrue(state.animated);
        assertTrue(state.targetVolume > quieter.targetVolume);
        assertTrue(
            Math.abs(BridgeWaveformMath.layerYOffsetPx(state.targetVolume, state.targetVolume, 0.35f, 240L, 0, 48)) >
                Math.abs(BridgeWaveformMath.layerYOffsetPx(quieter.targetVolume, quieter.targetVolume, 0.35f, 240L, 0, 48))
        );
    }

    @Test
    public void clippingAmplitudeIsClamped() {
        WaveformState state = WaveformState.fromStatus(BridgeCaptureStatus.recording(999999));

        assertTrue(state.visible);
        assertTrue(state.animated);
        assertEquals(100, state.targetVolume);
    }

    @Test
    public void captureHeightControlsRecordingWaveformBounds() {
        WaveformState state = WaveformState.fromStatus(BridgeCaptureStatus.recording(9000));

        float smallHeightMax = BridgeWaveformMath.maxAmplitudePx(24, 1f);
        float largeHeightMax = BridgeWaveformMath.maxAmplitudePx(48, 1f);
        assertTrue(largeHeightMax > smallHeightMax * 1.8f);

        float smallOffset = Math.abs(
            BridgeWaveformMath.layerYOffsetPx(state.targetVolume, state.targetVolume, 0.35f, 240L, 0, 24)
        );
        float largeOffset = Math.abs(
            BridgeWaveformMath.layerYOffsetPx(state.targetVolume, state.targetVolume, 0.35f, 240L, 0, 48)
        );
        assertTrue(largeOffset > smallOffset * 1.8f);
    }

    private void assertStaticIdle(WaveformState state) {
        assertTrue(state.visible);
        assertTrue(!state.animated);
        assertEquals(WaveformState.IDLE_TARGET_VOLUME, state.targetVolume);
        assertEquals(WaveformState.IDLE_WAVEFORM_ALPHA, state.waveformAlpha);
    }
}
