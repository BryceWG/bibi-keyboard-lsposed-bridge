/*
 * Pro-first Binder client for Clipboard Sync Runtime activation.
 * Never sends SyncClipboard credentials — only session identity and target IME package.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

final class BridgeClipboardSyncClient implements BridgeClipboardSyncDispatcher.Client {
    private static final long BIND_TIMEOUT_MS = 700L;

    interface Transport {
        boolean isBinderAlive();
        boolean bindTo(String appPackage);
        boolean activate(String sessionId, String targetImePackage);
        void windowHidden(String sessionId);
        void deactivate(String sessionId);
        void unbind();
    }

    private final Transport transport;
    private String activeSessionId;
    private String activeTargetPackage;

    BridgeClipboardSyncClient(Context context) {
        this(new AndroidTransport(context));
    }

    BridgeClipboardSyncClient(Transport transport) {
        this.transport = transport;
    }

    @Override
    public synchronized boolean activate(
        String targetImePackage,
        BooleanSupplier isCurrent
    ) {
        if (transport == null || targetImePackage == null || targetImePackage.length() == 0) {
            return false;
        }
        if (!isCurrent.getAsBoolean()) return false;
        // Screen Session：同一目标已绑定则保持，避免普通隐藏后再显示时反复握手。
        if (targetImePackage.equals(activeTargetPackage) &&
            transport.isBinderAlive() &&
            activeSessionId != null) {
            return true;
        }
        String sessionId = UUID.randomUUID().toString();
        for (String appPackage : BridgeContract.MAIN_APP_PACKAGES) {
            if (!isCurrent.getAsBoolean()) break;
            transport.unbind();
            if (!transport.bindTo(appPackage)) continue;
            if (!isCurrent.getAsBoolean()) break;
            if (transport.activate(sessionId, targetImePackage)) {
                if (!isCurrent.getAsBoolean()) {
                    transport.deactivate(sessionId);
                    transport.unbind();
                    return false;
                }
                activeSessionId = sessionId;
                activeTargetPackage = targetImePackage;
                return true;
            }
        }
        transport.unbind();
        return false;
    }

    @Override
    public synchronized void windowHidden() {
        if (activeSessionId == null || !transport.isBinderAlive()) return;
        transport.windowHidden(activeSessionId);
    }

    @Override
    public synchronized void deactivate() {
        if (activeSessionId != null && transport.isBinderAlive()) {
            transport.deactivate(activeSessionId);
        }
        activeSessionId = null;
        activeTargetPackage = null;
        transport.unbind();
    }

    private static final class AndroidTransport implements Transport {
        private final Context context;
        private volatile IBinder binder;
        private volatile ServiceConnection connection;

        AndroidTransport(Context context) {
            this.context = context == null ? null : context.getApplicationContext();
        }

        @Override
        public boolean isBinderAlive() {
            return binder != null && binder.isBinderAlive();
        }

        @Override
        public boolean bindTo(String appPackage) {
            if (context == null) return false;
            Intent intent = new Intent(BridgeContract.CLIPBOARD_SYNC_ACTION_BIND_SERVICE);
            intent.setPackage(appPackage);
            intent.setComponent(
                new ComponentName(appPackage, BridgeContract.CLIPBOARD_SYNC_DESCRIPTOR)
            );
            CountDownLatch latch = new CountDownLatch(1);
            final boolean[] ok = {false};
            connection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    if (connection != this) return;
                    binder = service;
                    ok[0] = service != null;
                    latch.countDown();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    if (connection != this) return;
                    binder = null;
                }

                @Override
                public void onBindingDied(ComponentName name) {
                    if (connection != this) return;
                    binder = null;
                }
            };
            try {
                boolean bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
                if (!bound) {
                    connection = null;
                    return false;
                }
                latch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                return ok[0] && binder != null;
            } catch (Throwable t) {
                unbind();
                return false;
            }
        }

        @Override
        public boolean activate(String sessionId, String targetImePackage) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(BridgeContract.CLIPBOARD_SYNC_DESCRIPTOR);
                data.writeInt(BridgeContract.PROTOCOL_VERSION);
                data.writeString(sessionId);
                data.writeString(targetImePackage);
                binder.transact(BridgeContract.CLIPBOARD_SYNC_TRANSACTION_ACTIVATE, data, reply, 0);
                reply.readException();
                int code = reply.readInt();
                return code == BridgeContract.RESULT_OK;
            } catch (Throwable t) {
                return false;
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        @Override
        public void windowHidden(String sessionId) {
            transactString(BridgeContract.CLIPBOARD_SYNC_TRANSACTION_WINDOW_HIDDEN, sessionId);
        }

        @Override
        public void deactivate(String sessionId) {
            transactString(BridgeContract.CLIPBOARD_SYNC_TRANSACTION_DEACTIVATE, sessionId);
        }

        private void transactString(int transactionCode, String sessionId) {
            if (binder == null) return;
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(BridgeContract.CLIPBOARD_SYNC_DESCRIPTOR);
                data.writeString(sessionId);
                binder.transact(transactionCode, data, reply, 0);
                reply.readException();
            } catch (Throwable ignored) {
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        @Override
        public void unbind() {
            if (connection != null && context != null) {
                try {
                    context.unbindService(connection);
                } catch (Throwable ignored) {
                }
            }
            connection = null;
            binder = null;
        }
    }
}
