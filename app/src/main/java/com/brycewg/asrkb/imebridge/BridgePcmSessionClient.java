/*
 * Minimal Binder client for the main app bridge PCM session service.
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class BridgePcmSessionClient implements BridgeCaptureCoordinator.SessionClient {
    private static final long BIND_TIMEOUT_MS = 700L;

    private final Context context;
    private IBinder binder;
    private ServiceConnection connection;
    private String activeSessionId;

    BridgePcmSessionClient(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    @Override
    public synchronized BridgeCaptureCoordinator.OperationResult begin(String sessionId) {
        if (sessionId == null || sessionId.length() == 0) {
            return BridgeCaptureCoordinator.OperationResult.error(
                BridgeContract.PCM_RESULT_BAD_REQUEST,
                "empty session id"
            );
        }
        BridgeCaptureCoordinator.OperationResult last = null;
        for (String appPackage : BridgeContract.MAIN_APP_PACKAGES) {
            unbind();
            BridgeCaptureCoordinator.OperationResult bound = bindTo(appPackage);
            if (!bound.isSuccess()) {
                last = bound;
                continue;
            }
            BridgeCaptureCoordinator.OperationResult result = transactString(
                BridgeContract.PCM_TRANSACTION_BEGIN,
                sessionId
            );
            if (result.isSuccess()) {
                activeSessionId = sessionId;
                return result;
            }
            last = result;
        }
        unbind();
        if (last != null) return last;
        return BridgeCaptureCoordinator.OperationResult.error(
            BridgeContract.PCM_RESULT_BRIDGE_UNAVAILABLE,
            "pcm service unavailable"
        );
    }

    @Override
    public synchronized BridgeCaptureCoordinator.OperationResult writeFrame(
        String sessionId,
        byte[] pcm,
        int sampleRate,
        int channels
    ) {
        if (!isActiveSession(sessionId)) {
            return BridgeCaptureCoordinator.OperationResult.error(
                BridgeContract.PCM_RESULT_STALE_SESSION,
                "stale session"
            );
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(BridgeContract.PCM_DESCRIPTOR);
            data.writeString(sessionId);
            data.writeByteArray(pcm == null ? new byte[0] : pcm);
            data.writeInt(sampleRate);
            data.writeInt(channels);
            binder.transact(BridgeContract.PCM_TRANSACTION_WRITE_FRAME, data, reply, 0);
            reply.readException();
            return readResult(reply);
        } catch (Throwable t) {
            return BridgeCaptureCoordinator.OperationResult.error(
                BridgeContract.PCM_RESULT_UNSUPPORTED,
                t.getMessage() == null ? "write frame failed" : t.getMessage()
            );
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    @Override
    public synchronized BridgeCaptureCoordinator.OperationResult finish(String sessionId) {
        if (!isActiveSession(sessionId)) {
            return BridgeCaptureCoordinator.OperationResult.error(
                BridgeContract.PCM_RESULT_STALE_SESSION,
                "stale session"
            );
        }
        BridgeCaptureCoordinator.OperationResult result = transactString(
            BridgeContract.PCM_TRANSACTION_FINISH,
            sessionId
        );
        activeSessionId = null;
        unbind();
        return result;
    }

    @Override
    public synchronized BridgeCaptureCoordinator.OperationResult cancel(String sessionId) {
        if (!isActiveSession(sessionId)) {
            return BridgeCaptureCoordinator.OperationResult.ok("already inactive");
        }
        BridgeCaptureCoordinator.OperationResult result = transactString(
            BridgeContract.PCM_TRANSACTION_CANCEL,
            sessionId
        );
        activeSessionId = null;
        unbind();
        return result;
    }

    @Override
    public synchronized void close() {
        activeSessionId = null;
        unbind();
    }

    private boolean isActiveSession(String sessionId) {
        return activeSessionId != null && activeSessionId.equals(sessionId) && binder != null;
    }

    private BridgeCaptureCoordinator.OperationResult bindTo(String appPackage) {
        if (context == null) {
            return BridgeCaptureCoordinator.OperationResult.error(
                BridgeContract.PCM_RESULT_BRIDGE_UNAVAILABLE,
                "no context"
            );
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final IBinder[] result = new IBinder[1];
        ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                result[0] = service;
                latch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                synchronized (BridgePcmSessionClient.this) {
                    binder = null;
                    activeSessionId = null;
                }
            }
        };
        Intent intent = new Intent(BridgeContract.PCM_ACTION_BIND_SERVICE);
        intent.setPackage(appPackage);
        boolean requested;
        try {
            requested = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        } catch (Throwable t) {
            return BridgeCaptureCoordinator.OperationResult.error(
                BridgeContract.PCM_RESULT_BRIDGE_UNAVAILABLE,
                "bind failed: " + t.getClass().getSimpleName()
            );
        }
        if (!requested) {
            return BridgeCaptureCoordinator.OperationResult.error(
                BridgeContract.PCM_RESULT_BRIDGE_UNAVAILABLE,
                "bind rejected"
            );
        }
        boolean connected;
        try {
            connected = latch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            connected = false;
        }
        if (!connected || result[0] == null) {
            try {
                context.unbindService(serviceConnection);
            } catch (Throwable ignored) {
            }
            return BridgeCaptureCoordinator.OperationResult.error(
                BridgeContract.PCM_RESULT_BRIDGE_UNAVAILABLE,
                "bind timeout"
            );
        }
        binder = result[0];
        connection = serviceConnection;
        return BridgeCaptureCoordinator.OperationResult.ok("bound");
    }

    private BridgeCaptureCoordinator.OperationResult transactString(int code, String sessionId) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(BridgeContract.PCM_DESCRIPTOR);
            data.writeString(sessionId);
            binder.transact(code, data, reply, 0);
            reply.readException();
            return readResult(reply);
        } catch (Throwable t) {
            return BridgeCaptureCoordinator.OperationResult.error(
                BridgeContract.PCM_RESULT_UNSUPPORTED,
                t.getMessage() == null ? "transact failed" : t.getMessage()
            );
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private BridgeCaptureCoordinator.OperationResult readResult(Parcel reply) {
        int code = reply.readInt();
        String message = reply.readString();
        if (code == BridgeContract.PCM_RESULT_OK) {
            return BridgeCaptureCoordinator.OperationResult.ok(message == null ? "ok" : message);
        }
        return BridgeCaptureCoordinator.OperationResult.error(
            code,
            message == null ? BridgeContract.pcmMessageForCode(code) : message
        );
    }

    private void unbind() {
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
