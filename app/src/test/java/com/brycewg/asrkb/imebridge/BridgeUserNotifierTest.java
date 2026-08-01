package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.DeadObjectException;
import android.os.RemoteException;

import org.junit.Test;

public class BridgeUserNotifierTest {
    @Test
    public void toastCooldownAllowsTheFirstWarningAndSuppressesImmediateRepeats() {
        assertTrue(BridgeUserNotifier.shouldShow(-30_000L, 0L));
        assertFalse(BridgeUserNotifier.shouldShow(10_000L, 39_999L));
        assertTrue(BridgeUserNotifier.shouldShow(10_000L, 40_000L));
    }

    @Test
    public void differentWarningsHaveIndependentCooldowns() {
        assertTrue(BridgeUserNotifier.tryAcquireToastSlot(R.string.bridge_toast_feature_disabled, 0L));
        assertFalse(BridgeUserNotifier.tryAcquireToastSlot(R.string.bridge_toast_feature_disabled, 1L));
        assertTrue(BridgeUserNotifier.tryAcquireToastSlot(R.string.bridge_toast_sensitive_field, 1L));
    }

    @Test
    public void pcmFailuresUseSpecificWarningMessages() {
        assertEquals(
            R.string.bridge_toast_protocol_mismatch,
            BridgePcmSessionClient.warningMessageResFor(BridgeContract.PCM_RESULT_UNSUPPORTED)
        );
        assertEquals(
            R.string.bridge_toast_session_unavailable,
            BridgePcmSessionClient.warningMessageResFor(BridgeContract.PCM_RESULT_SESSION_UNAVAILABLE)
        );
        assertEquals(
            R.string.bridge_toast_connection_failed,
            BridgePcmSessionClient.warningMessageResFor(BridgeContract.PCM_RESULT_BRIDGE_UNAVAILABLE)
        );
        assertEquals(
            R.string.bridge_toast_no_input_connection,
            BridgePcmSessionClient.warningMessageResFor(BridgeContract.PCM_RESULT_NO_INPUT_CONNECTION)
        );
        assertEquals(
            R.string.bridge_toast_sensitive_field,
            BridgePcmSessionClient.warningMessageResFor(BridgeContract.PCM_RESULT_SENSITIVE_FIELD)
        );
        assertEquals(
            R.string.bridge_toast_feature_disabled,
            BridgePcmSessionClient.warningMessageResFor(BridgeContract.PCM_RESULT_FEATURE_DISABLED)
        );
        assertEquals(0, BridgePcmSessionClient.warningMessageResFor(BridgeContract.PCM_RESULT_BUSY));
        assertEquals(
            BridgeContract.PCM_RESULT_BRIDGE_UNAVAILABLE,
            BridgePcmSessionClient.transportFailureCodeFor(new DeadObjectException())
        );
        assertTrue(BridgePcmSessionClient.stopsFallbackFor(BridgeContract.PCM_RESULT_FEATURE_DISABLED));
        assertTrue(BridgePcmSessionClient.stopsFallbackFor(BridgeContract.PCM_RESULT_NO_INPUT_CONNECTION));
        assertTrue(BridgePcmSessionClient.stopsFallbackFor(BridgeContract.PCM_RESULT_SENSITIVE_FIELD));
        assertTrue(BridgePcmSessionClient.stopsFallbackFor(BridgeContract.PCM_RESULT_SESSION_UNAVAILABLE));
        assertFalse(BridgePcmSessionClient.stopsFallbackFor(BridgeContract.PCM_RESULT_BRIDGE_UNAVAILABLE));
        assertEquals(
            BridgeContract.PCM_RESULT_BRIDGE_UNAVAILABLE,
            BridgePcmSessionClient.transportFailureCodeFor(new RemoteException())
        );
        assertEquals(
            R.string.bridge_toast_microphone_unavailable,
            BridgeUserNotifier.captureFailureMessageResFor(BridgeCaptureStatus.FAILURE_REASON_AUDIO_RECORD)
        );
        assertEquals(
            R.string.bridge_toast_no_input_connection,
            BridgeUserNotifier.captureFailureMessageResFor(BridgeCaptureStatus.FAILURE_REASON_NO_INPUT_CONNECTION)
        );
        assertEquals(
            R.string.bridge_toast_sensitive_field,
            BridgeUserNotifier.captureFailureMessageResFor(BridgeCaptureStatus.FAILURE_REASON_SENSITIVE_FIELD)
        );
        assertEquals(
            0,
            BridgeUserNotifier.captureFailureMessageResFor(BridgeCaptureStatus.FAILURE_REASON_NONE)
        );
        assertEquals(
            R.string.bridge_toast_recording_failed,
            BridgeUserNotifier.captureFailureMessageResFor(BridgeCaptureStatus.FAILURE_REASON_RECORDING)
        );
        assertEquals(
            R.string.bridge_toast_finish_failed,
            BridgeUserNotifier.captureFailureMessageResFor(BridgeCaptureStatus.FAILURE_REASON_FINISH)
        );
    }
}
