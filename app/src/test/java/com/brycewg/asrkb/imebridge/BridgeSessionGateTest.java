package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BridgeSessionGateTest {
    @Test
    public void legacyRequestIsAcceptedOnlyWhenNoSessionIsActive() {
        assertTrue(BridgeSessionGate.accepts(null, null));
        assertTrue(BridgeSessionGate.accepts("", ""));

        assertFalse(BridgeSessionGate.accepts("active", null));
        assertFalse(BridgeSessionGate.accepts("active", ""));
    }

    @Test
    public void sessionRequestBeforeBeginIsRejected() {
        assertFalse(BridgeSessionGate.accepts(null, "session-1"));
        assertFalse(BridgeSessionGate.accepts("", "session-1"));
    }

    @Test
    public void sessionRequestRequiresExactActiveSessionMatch() {
        assertTrue(BridgeSessionGate.accepts("session-1", "session-1"));

        assertFalse(BridgeSessionGate.accepts("session-1", "session-2"));
    }

    @Test
    public void oldSessionRequestAfterResetIsRejected() {
        String oldSessionId = "session-1";

        assertTrue(BridgeSessionGate.accepts(oldSessionId, oldSessionId));

        assertFalse(BridgeSessionGate.accepts(null, oldSessionId));
    }

    @Test
    public void oldSessionRequestAfterCancelIsRejected() {
        String oldSessionId = "session-1";

        assertTrue(BridgeSessionGate.accepts(oldSessionId, oldSessionId));

        assertFalse(BridgeSessionGate.accepts(null, oldSessionId));
    }
}
