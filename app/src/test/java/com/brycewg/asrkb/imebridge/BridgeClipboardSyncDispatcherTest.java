package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.junit.Test;

public class BridgeClipboardSyncDispatcherTest {
    @Test
    public void windowShownReturnsBeforeBindCompletes() throws Exception {
        CountDownLatch bindStarted = new CountDownLatch(1);
        CountDownLatch releaseBind = new CountDownLatch(1);
        FakeClient client = new FakeClient(bindStarted, releaseBind);
        BridgeClipboardSyncDispatcher dispatcher = new BridgeClipboardSyncDispatcher(client);

        long started = System.nanoTime();
        dispatcher.windowShown("third.party.ime");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertTrue("background bind did not start", bindStarted.await(1, TimeUnit.SECONDS));
        assertTrue("window callback waited for bind", elapsedMs < 500L);
        releaseBind.countDown();
        dispatcher.destroy();
        assertTrue(client.deactivated.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void hiddenDuringBindPreventsLateActivation() throws Exception {
        CountDownLatch bindStarted = new CountDownLatch(1);
        CountDownLatch releaseBind = new CountDownLatch(1);
        FakeClient client = new FakeClient(bindStarted, releaseBind);
        BridgeClipboardSyncDispatcher dispatcher = new BridgeClipboardSyncDispatcher(client);

        dispatcher.windowShown("third.party.ime");
        assertTrue("background bind did not start", bindStarted.await(1, TimeUnit.SECONDS));
        dispatcher.windowHidden();
        releaseBind.countDown();
        dispatcher.destroy();
        assertTrue(client.deactivated.await(1, TimeUnit.SECONDS));

        assertFalse("stale show activated after hide", client.activated.get());
    }

    private static final class FakeClient implements BridgeClipboardSyncDispatcher.Client {
        private final CountDownLatch bindStarted;
        private final CountDownLatch releaseBind;
        private final CountDownLatch deactivated = new CountDownLatch(1);
        private final AtomicBoolean activated = new AtomicBoolean();

        FakeClient(CountDownLatch bindStarted, CountDownLatch releaseBind) {
            this.bindStarted = bindStarted;
            this.releaseBind = releaseBind;
        }

        @Override
        public boolean activate(String targetImePackage, BooleanSupplier isCurrent) {
            bindStarted.countDown();
            try {
                releaseBind.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (!isCurrent.getAsBoolean()) return false;
            activated.set(true);
            return true;
        }

        @Override
        public void windowHidden() {
        }

        @Override
        public void deactivate() {
            deactivated.countDown();
        }
    }
}
