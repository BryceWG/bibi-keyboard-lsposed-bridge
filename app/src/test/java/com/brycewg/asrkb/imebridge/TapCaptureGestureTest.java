package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TapCaptureGestureTest {
    @Test
    public void validTapsStartThenFinishCapture() {
        TapCaptureGesture gesture = new TapCaptureGesture(8f);

        gesture.onDown(4f, 4f);
        assertTrue(gesture.onUp());
        assertEquals(
            BottomCaptureStripView.TAP_ACTION_START,
            BottomCaptureStripView.tapAction(BridgeCaptureStatus.ready("ready"))
        );

        gesture.onDown(4f, 4f);
        assertTrue(gesture.onUp());
        assertEquals(
            BottomCaptureStripView.TAP_ACTION_FINISH,
            BottomCaptureStripView.tapAction(BridgeCaptureStatus.recording(1000))
        );
        assertEquals(
            BottomCaptureStripView.TAP_ACTION_FINISH,
            BottomCaptureStripView.tapAction(BridgeCaptureStatus.starting("starting"))
        );
    }

    @Test
    public void movementAndCancellationDoNotTriggerTapActions() {
        TapCaptureGesture gesture = new TapCaptureGesture(4f);

        gesture.onDown(0f, 0f);
        gesture.onMove(8f, 0f, true);
        assertFalse(gesture.onUp());

        gesture.onDown(0f, 0f);
        gesture.cancel();
        assertFalse(gesture.onUp());
        assertEquals(
            BottomCaptureStripView.TAP_ACTION_NONE,
            BottomCaptureStripView.tapAction(BridgeCaptureStatus.finishing())
        );
        assertEquals(
            BottomCaptureStripView.TAP_ACTION_NONE,
            BottomCaptureStripView.tapAction(BridgeCaptureStatus.cancelling("cancel"))
        );
    }
}
