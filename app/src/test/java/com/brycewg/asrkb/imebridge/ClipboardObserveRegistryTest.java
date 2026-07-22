package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class ClipboardObserveRegistryTest {
    @Test
    public void autoModeKeepsOnlyPreferredHostSubscription() {
        BridgeHostRouting.apply(BridgeContract.HOST_TARGET_AUTO);
        ClipboardObserveRegistry registry = new ClipboardObserveRegistry();

        assertTrue(registry.subscribe(BridgeContract.PACKAGE_OPEN_SOURCE, "oss-token"));
        assertTrue(registry.subscribe(BridgeContract.PACKAGE_PRO, "pro-token"));
        assertFalse(registry.subscribe(BridgeContract.PACKAGE_OPEN_SOURCE, "oss-2"));

        List<ClipboardObserveRegistry.Subscription> subscriptions = registry.snapshot();
        assertEquals(1, subscriptions.size());
        assertEquals(BridgeContract.PACKAGE_PRO, subscriptions.get(0).appPackageName);
        assertEquals("pro-token", subscriptions.get(0).token);

        registry.unsubscribe(BridgeContract.PACKAGE_OPEN_SOURCE);

        assertTrue(registry.hasSubscribers());
        assertEquals(1, registry.snapshot().size());
        assertEquals(BridgeContract.PACKAGE_PRO, registry.snapshot().get(0).appPackageName);
    }

    @Test
    public void manualHostRejectsAndPurgesDisallowedSubscriptions() {
        BridgeHostRouting.apply(BridgeContract.HOST_TARGET_AUTO);
        ClipboardObserveRegistry registry = new ClipboardObserveRegistry();
        assertTrue(registry.subscribe(BridgeContract.PACKAGE_OPEN_SOURCE, "oss-token"));
        assertTrue(registry.subscribe(BridgeContract.PACKAGE_PRO, "pro-token"));

        BridgeHostRouting.apply(BridgeContract.HOST_TARGET_PRO);
        try {
            registry.retainAllowedHosts();
            assertFalse(registry.subscribe(BridgeContract.PACKAGE_OPEN_SOURCE, "oss-2"));
            assertEquals(1, registry.snapshot().size());
            assertEquals(BridgeContract.PACKAGE_PRO, registry.snapshot().get(0).appPackageName);
        } finally {
            BridgeHostRouting.apply(BridgeContract.HOST_TARGET_AUTO);
        }
    }

    @Test
    public void emptyTokenCannotCreateSubscription() {
        BridgeHostRouting.apply(BridgeContract.HOST_TARGET_AUTO);
        ClipboardObserveRegistry registry = new ClipboardObserveRegistry();

        assertFalse(registry.subscribe(BridgeContract.PACKAGE_PRO, ""));
        assertFalse(registry.hasSubscribers());
    }
}
