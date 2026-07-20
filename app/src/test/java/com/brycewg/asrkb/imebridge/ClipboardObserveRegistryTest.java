package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class ClipboardObserveRegistryTest {
    @Test
    public void subscriptionsPreferProAndRemainIsolatedPerHost() {
        ClipboardObserveRegistry registry = new ClipboardObserveRegistry();

        assertTrue(registry.subscribe(BridgeContract.PACKAGE_OPEN_SOURCE, "oss-token"));
        assertTrue(registry.subscribe(BridgeContract.PACKAGE_PRO, "pro-token"));

        List<ClipboardObserveRegistry.Subscription> subscriptions = registry.snapshot();
        assertEquals(2, subscriptions.size());
        assertEquals(BridgeContract.PACKAGE_PRO, subscriptions.get(0).appPackageName);
        assertEquals("pro-token", subscriptions.get(0).token);
        assertEquals(BridgeContract.PACKAGE_OPEN_SOURCE, subscriptions.get(1).appPackageName);

        registry.unsubscribe(BridgeContract.PACKAGE_OPEN_SOURCE);

        assertTrue(registry.hasSubscribers());
        assertEquals(1, registry.snapshot().size());
        assertEquals(BridgeContract.PACKAGE_PRO, registry.snapshot().get(0).appPackageName);
    }

    @Test
    public void emptyTokenCannotCreateSubscription() {
        ClipboardObserveRegistry registry = new ClipboardObserveRegistry();

        assertFalse(registry.subscribe(BridgeContract.PACKAGE_PRO, ""));
        assertFalse(registry.hasSubscribers());
    }
}
