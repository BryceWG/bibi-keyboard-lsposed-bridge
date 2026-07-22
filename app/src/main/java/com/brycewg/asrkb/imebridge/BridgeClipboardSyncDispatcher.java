/*
 * Serializes clipboard runtime Binder work away from the hooked IME main thread.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

final class BridgeClipboardSyncDispatcher {
    interface Client {
        boolean activate(String targetImePackage, BooleanSupplier isCurrent);
        void windowHidden();
        void deactivate();
    }

    private final Client client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BiBiClipboardSyncBridge");
        thread.setDaemon(true);
        return thread;
    });
    private long generation;
    private boolean destroyed;

    BridgeClipboardSyncDispatcher(Client client) {
        this.client = client;
    }

    synchronized void windowShown(String targetImePackage) {
        if (destroyed) return;
        long currentGeneration = ++generation;
        executor.execute(() -> {
            if (!isCurrent(currentGeneration)) return;
            client.activate(targetImePackage, () -> isCurrent(currentGeneration));
        });
    }

    /**
     * Host-target transition barrier: close the old session before any reconnect.
     * If {@code reconnectTargetImePackage} is non-null, activate only after deactivate completes.
     */
    synchronized void transitionHosts(String reconnectTargetImePackage) {
        if (destroyed) return;
        final boolean shouldActivate = reconnectTargetImePackage != null &&
            reconnectTargetImePackage.length() > 0;
        final String target = reconnectTargetImePackage;
        final long barrierGeneration = ++generation;
        executor.execute(() -> {
            client.deactivate();
            if (!shouldActivate) return;
            if (!isCurrent(barrierGeneration)) return;
            client.activate(target, () -> isCurrent(barrierGeneration));
        });
    }

    synchronized void windowHidden() {
        if (destroyed) return;
        generation++;
        executor.execute(client::windowHidden);
    }

    synchronized void destroy() {
        if (destroyed) return;
        destroyed = true;
        generation++;
        executor.execute(client::deactivate);
        executor.shutdown();
    }

    private synchronized boolean isCurrent(long candidate) {
        return !destroyed && generation == candidate;
    }
}
