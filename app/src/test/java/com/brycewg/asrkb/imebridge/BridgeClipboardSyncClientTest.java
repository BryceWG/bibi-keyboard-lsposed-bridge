package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

public class BridgeClipboardSyncClientTest {
    @Test
    public void proRejectionFallsBackToOssAndKeepsOssSession() {
        BridgeHostRouting.apply(BridgeContract.HOST_TARGET_AUTO);
        HostFallbackTransport transport = new HostFallbackTransport();
        BridgeClipboardSyncClient client = new BridgeClipboardSyncClient(transport);

        assertTrue(client.activate("third.party.ime", () -> true));
        client.windowHidden();

        assertEquals(
            Arrays.asList(BridgeContract.PACKAGE_PRO, BridgeContract.PACKAGE_OPEN_SOURCE),
            transport.bindAttempts
        );
        assertEquals(BridgeContract.PACKAGE_OPEN_SOURCE, transport.hiddenHost);
        assertEquals(transport.ossSessionId, transport.hiddenSessionId);
    }

    @Test
    public void proOnlyDoesNotFallBackToOpenSource() {
        BridgeHostRouting.apply(BridgeContract.HOST_TARGET_PRO);
        try {
            HostFallbackTransport transport = new HostFallbackTransport();
            BridgeClipboardSyncClient client = new BridgeClipboardSyncClient(transport);

            assertFalse(client.activate("third.party.ime", () -> true));
            assertEquals(Arrays.asList(BridgeContract.PACKAGE_PRO), transport.bindAttempts);
        } finally {
            BridgeHostRouting.apply(BridgeContract.HOST_TARGET_AUTO);
        }
    }

    @Test
    public void hiddenDuringActivateTransactionCompensatesSuccessfulSession() throws Exception {
        BlockingTransport transport = new BlockingTransport();
        BridgeClipboardSyncClient client = new BridgeClipboardSyncClient(transport);
        AtomicBoolean current = new AtomicBoolean(true);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> activation = executor.submit(
                () -> client.activate("third.party.ime", current::get)
            );
            assertTrue(transport.transactionStarted.await(1, TimeUnit.SECONDS));

            current.set(false);
            transport.releaseTransaction.countDown();

            assertFalse(activation.get(1, TimeUnit.SECONDS));
            assertTrue("stale server session was not deactivated", transport.deactivated);
            assertTrue("stale Binder connection was not released", transport.unbound);
            client.windowHidden();
            assertFalse("stale session was retained as active", transport.windowHidden);
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class BlockingTransport implements BridgeClipboardSyncClient.Transport {
        private final CountDownLatch transactionStarted = new CountDownLatch(1);
        private final CountDownLatch releaseTransaction = new CountDownLatch(1);
        private boolean deactivated;
        private boolean unbound;
        private boolean windowHidden;

        @Override
        public boolean isBinderAlive() {
            return true;
        }

        @Override
        public boolean bindTo(String appPackage) {
            return true;
        }

        @Override
        public boolean activate(String sessionId, String targetImePackage) {
            transactionStarted.countDown();
            try {
                releaseTransaction.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            return true;
        }

        @Override
        public void windowHidden(String sessionId) {
            windowHidden = true;
        }

        @Override
        public void deactivate(String sessionId) {
            deactivated = true;
        }

        @Override
        public void unbind() {
            unbound = true;
        }
    }

    private static final class HostFallbackTransport implements BridgeClipboardSyncClient.Transport {
        private final List<String> bindAttempts = new ArrayList<>();
        private String currentHost;
        private String hiddenHost;
        private String hiddenSessionId;
        private String ossSessionId;

        @Override
        public boolean isBinderAlive() {
            return currentHost != null;
        }

        @Override
        public boolean bindTo(String appPackage) {
            currentHost = appPackage;
            bindAttempts.add(appPackage);
            return true;
        }

        @Override
        public boolean activate(String sessionId, String targetImePackage) {
            // Pro represents RESULT_SYNC_DISABLED; OSS accepts the same request.
            if (!BridgeContract.PACKAGE_OPEN_SOURCE.equals(currentHost)) return false;
            ossSessionId = sessionId;
            return true;
        }

        @Override
        public void windowHidden(String sessionId) {
            hiddenHost = currentHost;
            hiddenSessionId = sessionId;
        }

        @Override
        public void deactivate(String sessionId) {
        }

        @Override
        public void unbind() {
            currentHost = null;
        }
    }
}
