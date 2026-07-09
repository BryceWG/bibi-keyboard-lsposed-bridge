package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BridgeWaveformMathTest {
    @Test
    public void calcValueMatchesWaveLineViewEnvelope() {
        assertEquals(0d, BridgeWaveformMath.calcValue(-2f, 0f), 0.0001d);
        assertEquals(0d, BridgeWaveformMath.calcValue(2f, 0f), 0.0001d);
        assertTrue(Math.abs(BridgeWaveformMath.calcValue(0f, 0.5f)) > 0.9d);
        assertEquals(0d, BridgeWaveformMath.calcValue(0f, 0f), 0.0001d);
    }

    @Test
    public void smoothVolumeApproachesTargetLikeWaveLineView() {
        float current = 0f;
        for (int i = 0; i < 40; i++) {
            current = BridgeWaveformMath.smoothVolume(current, 80);
        }
        assertEquals(80f, current, 0.0001f);
    }

    @Test
    public void peakToTargetVolumeKeepsSilenceBreathing() {
        int silence = BridgeWaveformMath.peakToTargetVolume(0);
        int louder = BridgeWaveformMath.peakToTargetVolume(9000);
        assertEquals(WaveformState.SILENCE_TARGET_VOLUME, silence);
        assertTrue(louder > silence);
        assertEquals(100, BridgeWaveformMath.peakToTargetVolume(999999));
    }

    @Test
    public void secondaryStrokeIsThinnerThanPrimary() {
        float thick = BridgeWaveformMath.thickStrokePx(3f, 96);
        float fine = BridgeWaveformMath.fineStrokePx(thick);
        assertTrue(fine < thick);
        assertTrue(BridgeWaveformMath.secondaryAlpha(218) < 218);
    }

    @Test
    public void idlePoseUsesFixedPhase() {
        assertEquals(125L, BridgeWaveformMath.IDLE_POSE_MILLIS);
        float center = Math.abs(
            BridgeWaveformMath.layerYOffsetPx(
                WaveformState.IDLE_TARGET_VOLUME,
                WaveformState.IDLE_TARGET_VOLUME,
                0.5f,
                BridgeWaveformMath.IDLE_POSE_MILLIS,
                0,
                48
            )
        );
        float edge = Math.abs(
            BridgeWaveformMath.layerYOffsetPx(
                WaveformState.IDLE_TARGET_VOLUME,
                WaveformState.IDLE_TARGET_VOLUME,
                0.05f,
                BridgeWaveformMath.IDLE_POSE_MILLIS,
                0,
                48
            )
        );
        assertTrue(center > edge);
        assertTrue(center > 1f);
    }
}
