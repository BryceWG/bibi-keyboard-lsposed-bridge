package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LongPressCaptureGestureTest {
    @Test
    public void pollBeforeThresholdDoesNotStart() {
        Events events = new Events();
        LongPressCaptureGesture gesture = new LongPressCaptureGesture(500L, 10f, events);

        gesture.onDown(4f, 5f, 1000L);

        assertFalse(gesture.poll(1499L));
        assertEquals(0, events.starts);
        assertFalse(gesture.onUp());
        assertEquals(0, events.releases);
    }

    @Test
    public void longPressThenReleaseStartsAndFinishes() {
        Events events = new Events();
        LongPressCaptureGesture gesture = new LongPressCaptureGesture(500L, 10f, events);

        gesture.onDown(0f, 0f, 1000L);

        assertTrue(gesture.poll(1500L));
        assertTrue(gesture.onUp());
        assertEquals(1, events.starts);
        assertEquals(1, events.releases);
        assertEquals(0, events.cancels);
    }

    @Test
    public void moveBeforeThresholdCancelsWithoutStarting() {
        Events events = new Events();
        LongPressCaptureGesture gesture = new LongPressCaptureGesture(500L, 4f, events);

        gesture.onDown(0f, 0f, 1000L);
        assertFalse(gesture.onMove(8f, 0f));

        assertFalse(gesture.isPending());
        assertEquals(0, events.starts);
        assertEquals(0, events.cancels);
    }

    @Test
    public void cancelAfterStartCancelsActiveHold() {
        Events events = new Events();
        LongPressCaptureGesture gesture = new LongPressCaptureGesture(500L, 10f, events);

        gesture.onDown(0f, 0f, 1000L);
        gesture.poll(1500L);
        assertTrue(gesture.onCancel());

        assertEquals(1, events.starts);
        assertEquals(1, events.cancels);
        assertEquals(0, events.releases);
    }

    @Test
    public void moveWithinHotZoneAfterStartDoesNotCancel() {
        Events events = new Events();
        LongPressCaptureGesture gesture = new LongPressCaptureGesture(500L, 4f, events);

        gesture.onDown(10f, 10f, 1000L);
        gesture.poll(1500L);
        assertTrue(gesture.onMove(40f, 12f, true));
        assertTrue(gesture.onMove(80f, 14f, true));
        assertTrue(gesture.onUp());

        assertEquals(1, events.starts);
        assertEquals(1, events.releases);
        assertEquals(0, events.cancels);
    }

    @Test
    public void moveOutsideHotZoneAfterStartCancels() {
        Events events = new Events();
        LongPressCaptureGesture gesture = new LongPressCaptureGesture(500L, 4f, events);

        gesture.onDown(10f, 10f, 1000L);
        gesture.poll(1500L);
        assertTrue(gesture.onMove(40f, 12f, false));

        assertEquals(1, events.starts);
        assertEquals(1, events.cancels);
        assertEquals(0, events.releases);
    }

    private static final class Events implements LongPressCaptureGesture.Listener {
        int starts;
        int releases;
        int cancels;

        @Override
        public void onLongPressStart() {
            starts++;
        }

        @Override
        public void onLongPressRelease() {
            releases++;
        }

        @Override
        public void onLongPressCancel() {
            cancels++;
        }
    }
}
