package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BridgeVisualPrefsTest {
    @Test
    public void widthAndHeightAreClampedToSupportedRange() {
        assertEquals(BridgeVisualPrefs.MIN_WIDTH_DP, BridgeVisualPrefs.clampWidthDp(20));
        assertEquals(BridgeVisualPrefs.MAX_WIDTH_DP, BridgeVisualPrefs.clampWidthDp(999));
        assertEquals(BridgeVisualPrefs.MIN_HEIGHT_DP, BridgeVisualPrefs.clampHeightDp(10));
        assertEquals(BridgeVisualPrefs.MAX_HEIGHT_DP, BridgeVisualPrefs.clampHeightDp(99));
    }

    @Test
    public void stripWidthUsesConfiguredDpButRespectsScreenMargins() {
        assertEquals(190, BottomCaptureStripView.computeStripWidthPx(500, 1f, 190));
        assertEquals(120, BottomCaptureStripView.computeStripWidthPx(500, 1f, 20));
        assertEquals(280, BottomCaptureStripView.computeStripWidthPx(500, 1f, 999));
        assertEquals(168, BottomCaptureStripView.computeStripWidthPx(200, 1f, 280));
    }

    @Test
    public void defaultsEnableRecordingAreaAndAutoHost() {
        BridgeVisualPrefs.VisualConfig defaults = BridgeVisualPrefs.defaults();
        assertEquals(BridgeContract.HOST_TARGET_AUTO, defaults.hostTarget);
        assertTrue(defaults.showRecordingArea);
        assertFalse(defaults.showWaveformOnlyWhileRecording);
        assertFalse(defaults.tapToToggleRecording);
    }

    @Test
    public void captureAttachWaitsForWindowShownConfig() {
        assertFalse(BridgeVisualPrefs.shouldAttachCapture(false, true));
        assertFalse(BridgeVisualPrefs.shouldAttachCapture(false, false));
        assertFalse(BridgeVisualPrefs.shouldAttachCapture(true, false));
        assertTrue(BridgeVisualPrefs.shouldAttachCapture(true, true));
    }

    @Test
    public void withersPreserveUnrelatedFields() {
        BridgeVisualPrefs.VisualConfig base = new BridgeVisualPrefs.VisualConfig(
            160,
            40,
            BridgeContract.HOST_TARGET_PRO,
            false,
            true,
            true
        );
        assertEquals(BridgeContract.HOST_TARGET_PRO, base.withSize(200, 48).hostTarget);
        assertFalse(base.withSize(200, 48).showRecordingArea);
        assertTrue(base.withSize(200, 48).showWaveformOnlyWhileRecording);
        assertTrue(base.withSize(200, 48).tapToToggleRecording);
        assertTrue(base.withShowRecordingArea(true).showRecordingArea);
        assertTrue(base.withShowRecordingArea(true).showWaveformOnlyWhileRecording);
        assertTrue(base.withHostTarget(BridgeContract.HOST_TARGET_AUTO)
            .showWaveformOnlyWhileRecording);
        assertFalse(base.withShowWaveformOnlyWhileRecording(false).showRecordingArea);
        assertTrue(base.withShowWaveformOnlyWhileRecording(false).tapToToggleRecording);
        assertFalse(base.withTapToToggleRecording(false).showRecordingArea);
        assertTrue(base.withTapToToggleRecording(false).showWaveformOnlyWhileRecording);
        assertEquals(
            BridgeContract.HOST_TARGET_OPEN_SOURCE,
            base.withHostTarget(BridgeContract.HOST_TARGET_OPEN_SOURCE).hostTarget
        );
    }

    @Test
    public void recordingOnlyModeHidesIdleButShowsRecordingWaveform() {
        assertFalse(WaveformState.fromStatus(
            BridgeCaptureStatus.ready("attached"),
            true
        ).visible);
        assertTrue(WaveformState.fromStatus(
            BridgeCaptureStatus.recording(1000),
            true
        ).visible);
    }
}
