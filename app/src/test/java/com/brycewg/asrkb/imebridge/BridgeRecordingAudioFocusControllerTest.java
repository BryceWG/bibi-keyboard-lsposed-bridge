package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.AudioManager;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class BridgeRecordingAudioFocusControllerTest {
    @Test
    public void acquireAndRepeatedReleaseAbandonsExactlyOnce() {
        FakeGateway gateway = new FakeGateway(true);
        BridgeRecordingAudioFocusController controller =
            new BridgeRecordingAudioFocusController(gateway, change -> { });

        assertTrue(controller.acquire());
        controller.release();
        controller.release();

        assertFalse(controller.isHeldForTest());
        assertEquals(1, gateway.abandoned.size());
    }

    @Test
    public void deniedFocusDoesNotCreateLease() {
        FakeGateway gateway = new FakeGateway(false);
        BridgeRecordingAudioFocusController controller =
            new BridgeRecordingAudioFocusController(gateway, change -> { });

        assertFalse(controller.acquire());
        controller.release();

        assertFalse(controller.isHeldForTest());
        assertTrue(gateway.abandoned.isEmpty());
    }

    @Test
    public void focusLossReleasesAndNotifiesExactlyOnce() {
        FakeGateway gateway = new FakeGateway(true);
        List<Integer> losses = new ArrayList<>();
        BridgeRecordingAudioFocusController controller =
            new BridgeRecordingAudioFocusController(gateway, losses::add);

        assertTrue(controller.acquire());
        gateway.emit(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
        gateway.emit(AudioManager.AUDIOFOCUS_LOSS);

        assertFalse(controller.isHeldForTest());
        assertEquals(1, gateway.abandoned.size());
        assertEquals(1, losses.size());
        assertEquals(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, losses.get(0).intValue());
    }

    private static final class FakeGateway implements BridgeRecordingAudioFocusController.Gateway {
        final boolean grant;
        final List<BridgeRecordingAudioFocusController.Handle> abandoned = new ArrayList<>();
        AudioManager.OnAudioFocusChangeListener listener;

        FakeGateway(boolean grant) {
            this.grant = grant;
        }

        @Override
        public BridgeRecordingAudioFocusController.Handle requestFocus(
            AudioManager.OnAudioFocusChangeListener listener
        ) {
            this.listener = listener;
            return grant ? new FakeHandle() : null;
        }

        @Override
        public void abandonFocus(BridgeRecordingAudioFocusController.Handle handle) {
            abandoned.add(handle);
        }

        void emit(int change) {
            if (listener != null) listener.onAudioFocusChange(change);
        }
    }

    private static final class FakeHandle implements BridgeRecordingAudioFocusController.Handle {
    }
}
