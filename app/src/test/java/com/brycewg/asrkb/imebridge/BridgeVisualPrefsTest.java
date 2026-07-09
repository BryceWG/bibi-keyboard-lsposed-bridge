package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertEquals;

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
}
